package com.bank.digitalcore.carddispatch.adapter.partner;

import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import com.bank.digitalcore.carddispatch.domain.DispatchStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * DHL-specific routing client and provider status mapping.
 */
@Component
public class DhlDeliveryPartnerClient extends AbstractHttpDeliveryPartnerClient {
    /**
     * Creates the dedicated DHL client.
     */
    public DhlDeliveryPartnerClient(
            CardDispatchProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager,
            RestClient.Builder restClientBuilder,
            ClientHttpRequestFactory partnerRequestFactory
    ) {
        super(DeliveryPartner.DHL, Map.of(
                "PICKED_UP", DispatchStatus.IN_TRANSIT,
                "TRANSIT", DispatchStatus.IN_TRANSIT,
                "OUT_FOR_DELIVERY", DispatchStatus.OUT_FOR_DELIVERY,
                "DELIVERED", DispatchStatus.DELIVERED,
                "DELIVERY_ATTEMPTED", DispatchStatus.DELIVERY_ATTEMPTED,
                "RETURNED", DispatchStatus.RETURNED
        ), properties, authorizedClientManager, restClientBuilder, partnerRequestFactory);
    }
}
