package com.bank.digitalcore.carddispatch.adapter.partner;

import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures unavailable optional carriers do not prevent service startup. */
class CardDispatchPropertiesTest {
    @Test
    void acceptsIncompleteEnabledCarrierUntilSelectedAtRequestTime() {
        CardDispatchProperties properties = new CardDispatchProperties();
        CardDispatchProperties.Partner dhl = new CardDispatchProperties.Partner();
        dhl.setEnabled(true);
        properties.setPartners(Map.of(DeliveryPartner.DHL, dhl));

        assertTrue(properties.isConfigurationValid());
    }
}
