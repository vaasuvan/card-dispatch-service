package com.bank.digitalcore.carddispatch.adapter.partner;

import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import com.bank.digitalcore.carddispatch.domain.DispatchStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * External partner endpoints, OAuth2 registrations, and partner-specific status mappings.
 */
@ConfigurationProperties(prefix = "card-dispatch")
@Validated
public class CardDispatchProperties {
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private int maxEvents = 50;
    private String idempotencyHmacKey;
    private Map<DeliveryPartner, Partner> partners = new HashMap<>();

    /**
     * Returns the upper connection timeout applied to partner HTTP calls.
     */
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    /**
     * Sets the upper connection timeout applied to partner HTTP calls.
     */
    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /**
     * Returns the upper response timeout applied to partner HTTP calls.
     */
    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * Sets the upper response timeout applied to partner HTTP calls.
     */
    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Returns the maximum event count retained per tracking response.
     */
    public int getMaxEvents() {
        return maxEvents;
    }

    /**
     * Sets the maximum event count retained per tracking response.
     */
    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    /** Returns the secret-manager supplied HMAC key for idempotency fingerprints. */
    public String getIdempotencyHmacKey() {
        return idempotencyHmacKey;
    }

    /** Sets the base64-encoded 32-byte idempotency HMAC key. */
    public void setIdempotencyHmacKey(String idempotencyHmacKey) {
        this.idempotencyHmacKey = idempotencyHmacKey;
    }

    /**
     * Returns configured partner definitions keyed by carrier enum.
     */
    public Map<DeliveryPartner, Partner> getPartners() {
        return partners;
    }

    /**
     * Sets configured partner definitions keyed by carrier enum.
     */
    public void setPartners(Map<DeliveryPartner, Partner> partners) {
        this.partners = partners;
    }

    /** Validates service-wide limits without preventing startup for an unselected partner. */
    @jakarta.validation.constraints.AssertTrue(message = "card-dispatch timeout or partner map configuration is invalid")
    public boolean isConfigurationValid() {
        if (connectTimeoutMs < 1 || connectTimeoutMs > 30_000
                || readTimeoutMs < 1 || readTimeoutMs > 60_000
                || maxEvents < 1 || maxEvents > 50) {
            return false;
        }
        return partners != null && partners.values().stream().noneMatch(java.util.Objects::isNull);
    }

    /**
     * One carrier endpoint, OAuth2 registration, and cancellation/tracking route configuration.
     */
    public static class Partner {
        private boolean enabled;
        private String baseUrl;
        private String bookingPath;
        private String trackingPath;
        private String cancellationPath;
        private String oauth2RegistrationId;
        private Map<String, DispatchStatus> statusMappings = new HashMap<>();

        /**
         * Returns whether this partner may be called.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables this partner integration.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the HTTPS partner API base URL.
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * Sets the partner API base URL.
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /** Returns the shipment booking endpoint path. */
        public String getBookingPath() {
            return bookingPath;
        }

        /** Sets the booking path containing a {dispatchId} template variable. */
        public void setBookingPath(String bookingPath) {
            this.bookingPath = bookingPath;
        }

        /**
         * Returns the endpoint path containing a {trackingNumber} template variable.
         */
        public String getTrackingPath() {
            return trackingPath;
        }

        /**
         * Sets the endpoint path containing a {trackingNumber} template variable.
         */
        public void setTrackingPath(String trackingPath) {
            this.trackingPath = trackingPath;
        }

        /**
         * Returns the carrier cancellation endpoint path.
         */
        public String getCancellationPath() {
            return cancellationPath;
        }

        /**
         * Sets the carrier cancellation endpoint path.
         */
        public void setCancellationPath(String cancellationPath) {
            this.cancellationPath = cancellationPath;
        }

        /**
         * Returns the OAuth2 client registration ID used for this carrier.
         */
        public String getOauth2RegistrationId() {
            return oauth2RegistrationId;
        }

        /**
         * Sets the OAuth2 client registration ID used for this carrier.
         */
        public void setOauth2RegistrationId(String oauth2RegistrationId) {
            this.oauth2RegistrationId = oauth2RegistrationId;
        }

        /**
         * Returns case-insensitive partner status to bank status mappings.
         */
        public Map<String, DispatchStatus> getStatusMappings() {
            return statusMappings;
        }

        /**
         * Sets case-insensitive partner status to bank status mappings.
         */
        public void setStatusMappings(Map<String, DispatchStatus> statusMappings) {
            this.statusMappings = statusMappings;
        }
    }
}
