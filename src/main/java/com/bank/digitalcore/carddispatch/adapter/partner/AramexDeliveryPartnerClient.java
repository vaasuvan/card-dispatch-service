package com.bank.digitalcore.carddispatch.adapter.partner;

import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import com.bank.digitalcore.carddispatch.domain.DispatchStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Aramex-specific routing client and provider status mapping.
 */
@Component
public class AramexDeliveryPartnerClient extends AbstractHttpDeliveryPartnerClient {
    /**
     * Creates the dedicated Aramex client.
     */
    public AramexDeliveryPartnerClient(
            CardDispatchProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager,
            RestClient.Builder restClientBuilder,
            ClientHttpRequestFactory partnerRequestFactory
    ) {
        super(DeliveryPartner.ARAMEX, Map.of(
                "INTRANSIT", DispatchStatus.IN_TRANSIT,
                "OUTFORDELIVERY", DispatchStatus.OUT_FOR_DELIVERY,
                "DELIVERED", DispatchStatus.DELIVERED,
                "DELIVERYATTEMPT", DispatchStatus.DELIVERY_ATTEMPTED,
                "EXCEPTION", DispatchStatus.EXCEPTION
        ), properties, authorizedClientManager, restClientBuilder, partnerRequestFactory);
    }
}
