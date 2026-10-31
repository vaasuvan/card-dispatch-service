package com.bank.digitalcore.carddispatch.adapter.partner;

import com.bank.digitalcore.carddispatch.domain.CardDispatch;
import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerNotConfiguredException;
import com.bank.digitalcore.carddispatch.domain.TrackingSnapshot;
import com.bank.digitalcore.carddispatch.port.DeliveryPartnerClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Covers carrier routing through the enum-based registry boundary.
 */
class DefaultDeliveryPartnerRegistryTest {
    /**
     * Routes track requests to one enabled client and never calls other carriers.
     */
    @Test
    void routesTrackingByEnum() {
        DeliveryPartnerClient dhl = client(DeliveryPartner.DHL, true);
        DeliveryPartnerClient fedex = client(DeliveryPartner.FEDEX, true);
        CardDispatch dispatch = dispatch(DeliveryPartner.FEDEX);
        TrackingSnapshot response = mock(TrackingSnapshot.class);
        when(fedex.track(dispatch)).thenReturn(response);
        DefaultDeliveryPartnerRegistry registry = new DefaultDeliveryPartnerRegistry(List.of(dhl, fedex));

        assertSame(response, registry.track(dispatch));

        verify(fedex).track(dispatch);
        verify(dhl, never()).track(dispatch);
    }

    /**
     * Fails closed for an unconfigured or disabled carrier.
     */
    @Test
    void rejectsDisabledCarrier() {
        DeliveryPartnerClient dhl = client(DeliveryPartner.DHL, false);
        DefaultDeliveryPartnerRegistry registry = new DefaultDeliveryPartnerRegistry(List.of(dhl));

        assertThrows(PartnerNotConfiguredException.class, () -> registry.track(dispatch(DeliveryPartner.DHL)));

        verify(dhl, never()).track(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Rejects duplicate client ownership of the same enum.
     */
    @Test
    void rejectsDuplicateCarrierClients() {
        DeliveryPartnerClient first = client(DeliveryPartner.DHL, true);
        DeliveryPartnerClient second = client(DeliveryPartner.DHL, true);

        assertThrows(IllegalStateException.class,
                () -> new DefaultDeliveryPartnerRegistry(List.of(first, second)));
    }

    private DeliveryPartnerClient client(DeliveryPartner partner, boolean enabled) {
        DeliveryPartnerClient client = mock(DeliveryPartnerClient.class);
        when(client.partner()).thenReturn(partner);
        when(client.isEnabled()).thenReturn(enabled);
        return client;
    }

    private CardDispatch dispatch(DeliveryPartner partner) {
        return new CardDispatch(UUID.randomUUID(), "customer-reference", partner, "tracking-reference",
                Instant.parse("2026-10-30T08:00:00Z"), "request-key-hash", "request-fingerprint");
    }
}
