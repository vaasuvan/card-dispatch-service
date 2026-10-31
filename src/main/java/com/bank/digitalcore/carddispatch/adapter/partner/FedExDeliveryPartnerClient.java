package com.bank.digitalcore.carddispatch.adapter.partner;

import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import com.bank.digitalcore.carddispatch.domain.DispatchStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * FedEx-specific routing client and provider status mapping.
 */
@Component
public class FedExDeliveryPartnerClient extends AbstractHttpDeliveryPartnerClient {
    /**
     * Creates the dedicated FedEx client.
     */
    public FedExDeliveryPartnerClient(
            CardDispatchProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager,
            RestClient.Builder restClientBuilder,
            ClientHttpRequestFactory partnerRequestFactory
    ) {
        super(DeliveryPartner.FEDEX, Map.of(
                "PU", DispatchStatus.IN_TRANSIT,
                "IT", DispatchStatus.IN_TRANSIT,
                "OD", DispatchStatus.OUT_FOR_DELIVERY,
                "DL", DispatchStatus.DELIVERED,
                "DE", DispatchStatus.EXCEPTION,
                "SE", DispatchStatus.EXCEPTION
        ), properties, authorizedClientManager, restClientBuilder, partnerRequestFactory);
    }
}
