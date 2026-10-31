package com.bank.digitalcore.carddispatch.adapter.partner;

import com.bank.digitalcore.carddispatch.domain.*;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.DispatchCancellationNotSupportedException;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerNotConfiguredException;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerTrackingNotFoundException;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerUnavailableException;
import com.bank.digitalcore.carddispatch.port.DeliveryPartnerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shared HTTP/OAuth transport and normalized response mapper for carrier clients.
 * Provider-specific clients supply their enum identity and status vocabulary.
 */
public abstract class AbstractHttpDeliveryPartnerClient implements DeliveryPartnerClient {
    private static final Logger log = LoggerFactory.getLogger(AbstractHttpDeliveryPartnerClient.class);
    private final DeliveryPartner partner;
    private final CardDispatchProperties properties;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final RestClient.Builder restClientBuilder;
    private final ClientHttpRequestFactory requestFactory;
    private final Map<String, DispatchStatus> statusMappings;
    private final boolean enabled;

    /**
     * Creates a configured dedicated carrier client over shared OAuth2 and HTTP facilities.
     */
    protected AbstractHttpDeliveryPartnerClient(
            DeliveryPartner partner,
            Map<String, DispatchStatus> statusMappings,
            CardDispatchProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager,
            RestClient.Builder restClientBuilder,
            ClientHttpRequestFactory requestFactory
    ) {
        this.partner = partner;
        this.statusMappings = Map.copyOf(statusMappings);
        this.properties = properties;
        this.authorizedClientManager = authorizedClientManager;
        this.restClientBuilder = restClientBuilder;
        this.requestFactory = requestFactory;
        CardDispatchProperties.Partner config = config();
        this.enabled = config != null && config.isEnabled() && validConfig(config);
        logConfiguration(config);
    }

    /**
     * Returns the carrier enum handled by this dedicated client.
     */
    @Override
    public final DeliveryPartner partner() {
        return partner;
    }

    /**
     * Returns whether this carrier has its required secure settings enabled.
     */
    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    private void logConfiguration(CardDispatchProperties.Partner config) {
        if (config == null) {
            log.warn("Carrier integration configuration is missing partner={}", partner);
        } else if (!config.isEnabled()) {
            log.info("Carrier integration is disabled partner={}", partner);
        } else if (!enabled) {
            log.warn("Carrier integration configuration is invalid partner={} baseUrlValid={} "
                            + "bookingPathValid={} trackingPathValid={} cancellationPathValid={} "
                            + "oauthRegistrationConfigured={}",
                    partner, validBaseUrl(config.getBaseUrl()), safeBookingPath(config.getBookingPath()),
                    safePath(config.getTrackingPath()), safePath(config.getCancellationPath()),
                    config.getOauth2RegistrationId() != null && !config.getOauth2RegistrationId().isBlank());
        } else {
            log.info("Carrier integration is enabled partner={}", partner);
        }
    }

    /** Books through the normalized carrier-facade contract and captures its tracking reference. */
    @Override
    public final CarrierBooking book(CardDispatch dispatch, RecipientAddress recipient, ShipmentDetails shipment) {
        CardDispatchProperties.Partner config = requiredConfig();
        try {
            CarrierBookingResponse response = client(config).post()
                    .uri(bookingEndpoint(config.getBookingPath(), dispatch.dispatchId()))
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken(config));
                        headers.set("Idempotency-Key", dispatch.dispatchId().toString());
                    })
                    .body(new CarrierBookingRequest(dispatch.dispatchId().toString(), recipient, shipment))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        log.warn("Carrier booking returned an error partner={} status={}",
                                partner, result.getStatusCode().value());
                        throw new PartnerUnavailableException();
                    })
                    .body(CarrierBookingResponse.class);
            if (response == null || response.trackingNumber() == null
                    || response.trackingNumber().isBlank() || response.trackingNumber().length() > 256) {
                throw new PartnerUnavailableException();
            }
            log.debug("Carrier booking completed partner={}", partner);
            return new CarrierBooking(response.trackingNumber(),
                    response.bookedAt() == null ? Instant.now() : response.bookedAt());
        } catch (PartnerUnavailableException exception) {
            throw exception;
        } catch (RestClientException | OAuth2AuthorizationException exception) {
            log.warn("Carrier booking outcome requires reconciliation partner={} errorType={}",
                    partner, exception.getClass().getSimpleName());
            throw new PartnerUnavailableException();
        }
    }

    /**
     * Calls the carrier track endpoint and maps only the normalized fields to bank response objects.
     */
    @Override
    public final TrackingSnapshot track(CardDispatch dispatch) {
        CardDispatchProperties.Partner config = requiredConfig();
        try {
            CarrierTrackingResponse response = client(config).get()
                    .uri(endpoint(config.getTrackingPath(), dispatch.trackingNumber()))
                    .headers(headers -> headers.setBearerAuth(accessToken(config)))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        if (result.getStatusCode().value() == 404) {
                            log.info("Carrier tracking reference was not found partner={}", partner);
                            throw new PartnerTrackingNotFoundException();
                        }
                        log.warn("Carrier tracking returned an error partner={} status={}",
                                partner, result.getStatusCode().value());
                        throw new PartnerUnavailableException();
                    })
                    .body(CarrierTrackingResponse.class);
            if (response == null || response.status() == null || response.lastUpdatedAt() == null) {
                throw new PartnerUnavailableException();
            }
            log.debug("Carrier tracking response received partner={}", partner);
            return normalize(dispatch, response);
        } catch (PartnerTrackingNotFoundException | PartnerUnavailableException exception) {
            throw exception;
        } catch (RestClientException | OAuth2AuthorizationException exception) {
            log.warn("Carrier tracking failed partner={} errorType={}", partner, exception.getClass().getSimpleName());
            throw new PartnerUnavailableException();
        }
    }

    /**
     * Calls the configured cancellation endpoint; carrier success is required before local cancellation.
     */
    @Override
    public final void cancel(CardDispatch dispatch) {
        CardDispatchProperties.Partner config = requiredConfig();
        try {
            client(config).delete()
                    .uri(endpoint(config.getCancellationPath(), dispatch.trackingNumber()))
                    .headers(headers -> headers.setBearerAuth(accessToken(config)))
                    .retrieve()
                    .onStatus(status -> status.value() == 405 || status.value() == 501,
                            (request, result) -> {
                                log.info("Carrier does not support cancellation partner={} status={}",
                                        partner, result.getStatusCode().value());
                                throw new DispatchCancellationNotSupportedException();
                            })
                    .onStatus(status -> status.value() == 404,
                            (request, result) -> {
                                log.info("Carrier cancellation reference was not found partner={}", partner);
                                throw new PartnerTrackingNotFoundException();
                            })
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        log.warn("Carrier cancellation returned an error partner={} status={}",
                                partner, result.getStatusCode().value());
                        throw new PartnerUnavailableException();
                    })
                    .toBodilessEntity();
            log.debug("Carrier cancellation completed partner={}", partner);
        } catch (DispatchCancellationNotSupportedException | PartnerTrackingNotFoundException
                 | PartnerUnavailableException exception) {
            throw exception;
        } catch (RestClientException | OAuth2AuthorizationException exception) {
            log.warn("Carrier cancellation failed partner={} errorType={}", partner,
                    exception.getClass().getSimpleName());
            throw new PartnerUnavailableException();
        }
    }

    private TrackingSnapshot normalize(CardDispatch dispatch, CarrierTrackingResponse response) {
        if (response.status().length() > 80 || (response.statusDescription() != null
                && response.statusDescription().length() > 512)) {
            throw new PartnerUnavailableException();
        }
        List<TrackingEvent> events = response.events() == null ? List.of() : response.events().stream()
                .filter(event -> event != null && event.status() != null && event.occurredAt() != null)
                .limit(properties.getMaxEvents())
                .map(event -> {
                    if (event.status().length() > 80 || (event.description() != null && event.description().length() > 512)
                            || (event.location() != null && event.location().length() > 256)) {
                        throw new PartnerUnavailableException();
                    }
                    return new TrackingEvent(mapStatus(event.status()), event.description(),
                            event.occurredAt(), event.location());
                })
                .toList();
        return new TrackingSnapshot(dispatch.dispatchId(), mapStatus(response.status()),
                response.statusDescription(), response.estimatedDeliveryDate(), response.lastUpdatedAt(), events);
    }

    private DispatchStatus mapStatus(String providerStatus) {
        String normalized = providerStatus.trim().toUpperCase(Locale.ROOT);
        Map<String, DispatchStatus> configuredMappings = config().getStatusMappings();
        DispatchStatus configured = configuredMappings.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(normalized))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        return configured != null
                ? configured
                : statusMappings.getOrDefault(normalized, DispatchStatus.UNKNOWN);
    }

    private String accessToken(CardDispatchProperties.Partner config) {
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(config.getOauth2RegistrationId())
                        .principal("card-dispatch-service").build());
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            log.warn("Carrier OAuth2 authorization returned no access token partner={}", partner);
            throw new PartnerUnavailableException();
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }

    private RestClient client(CardDispatchProperties.Partner config) {
        return restClientBuilder.clone()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private URI endpoint(String path, String trackingNumber) {
        String encodedTrackingNumber = URLEncoder.encode(trackingNumber, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return URI.create(config().getBaseUrl() + path.replace("{trackingNumber}", encodedTrackingNumber));
    }

    private CardDispatchProperties.Partner requiredConfig() {
        CardDispatchProperties.Partner config = config();
        if (config == null || !config.isEnabled() || !validConfig(config)) {
            throw new PartnerNotConfiguredException();
        }
        return config;
    }

    private CardDispatchProperties.Partner config() {
        return properties.getPartners().get(partner);
    }

    private boolean validConfig(CardDispatchProperties.Partner config) {
        return validBaseUrl(config.getBaseUrl())
                && safeBookingPath(config.getBookingPath())
                && safePath(config.getTrackingPath())
                && safePath(config.getCancellationPath())
                && config.getOauth2RegistrationId() != null
                && !config.getOauth2RegistrationId().isBlank();
    }

    private boolean validBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        try {
            URI uri = URI.create(baseUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean safePath(String path) {
        return path != null && path.startsWith("/") && path.contains("{trackingNumber}")
                && !path.contains("..") && !path.contains("?") && !path.contains("#");
    }

    private boolean safeBookingPath(String path) {
        return path != null && path.startsWith("/") && path.contains("{dispatchId}")
                && !path.contains("..") && !path.contains("?") && !path.contains("#");
    }

    private URI bookingEndpoint(String path, UUID dispatchId) {
        return URI.create(config().getBaseUrl() + path.replace("{dispatchId}", dispatchId.toString()));
    }

    /** Normalized response expected from the carrier integration facade after booking. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record CarrierBookingResponse(String trackingNumber, Instant bookedAt) { }

    /** Stable booking request sent to an approved carrier integration facade. */
    public record CarrierBookingRequest(
            String dispatchId,
            RecipientAddress recipient,
            ShipmentDetails shipment
    ) { }

    /**
     * Normalized HTTP response expected from the carrier integration facade.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record CarrierTrackingResponse(
            String status,
            String statusDescription,
            LocalDate estimatedDeliveryDate,
            Instant lastUpdatedAt,
            List<CarrierTrackingEvent> events
    ) {
    }

    /**
     * Normalized partner event shape mapped into the bank-owned event model.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record CarrierTrackingEvent(
            String status,
            String description,
            Instant occurredAt,
            String location
    ) {
    }
}
