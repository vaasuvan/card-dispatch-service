package com.bank.digitalcore.carddispatch.adapter.partner;

import com.bank.digitalcore.carddispatch.domain.*;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerNotConfiguredException;
import com.bank.digitalcore.carddispatch.port.DeliveryPartnerClient;
import com.bank.digitalcore.carddispatch.port.DeliveryPartnerRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves exactly one enabled carrier client for each supported delivery partner enum.
 */
@Component
public class DefaultDeliveryPartnerRegistry implements DeliveryPartnerRegistry {
    private final Map<DeliveryPartner, DeliveryPartnerClient> clients;

    /**
     * Indexes the dedicated carrier clients and rejects duplicate carrier ownership.
     */
    public DefaultDeliveryPartnerRegistry(List<DeliveryPartnerClient> clients) {
        EnumMap<DeliveryPartner, DeliveryPartnerClient> index = new EnumMap<>(DeliveryPartner.class);
        for (DeliveryPartnerClient client : clients) {
            if (index.putIfAbsent(client.partner(), client) != null) {
                throw new IllegalStateException("Multiple clients configured for delivery partner " + client.partner());
            }
        }
        this.clients = Map.copyOf(index);
    }

    /**
     * Routes tracking to the one enabled client for the persisted partner enum.
     */
    @Override
    public TrackingSnapshot track(CardDispatch dispatch) {
        return client(dispatch.deliveryPartner()).track(dispatch);
    }

    /**
     * Routes shipment creation to the dedicated client selected for the internal carrier enum.
     */
    @Override
    public CarrierBooking book(CardDispatch dispatch, RecipientAddress recipient, ShipmentDetails shipment) {
        return client(dispatch.deliveryPartner()).book(dispatch, recipient, shipment);
    }

    /**
     * Routes cancellation to the one enabled client for the persisted partner enum.
     */
    @Override
    public void cancel(CardDispatch dispatch) {
        client(dispatch.deliveryPartner()).cancel(dispatch);
    }

    /**
     * Checks whether the enum has a registered and enabled HTTP client.
     */
    @Override
    public boolean supports(DeliveryPartner partner) {
        DeliveryPartnerClient client = clients.get(partner);
        return client != null && client.isEnabled();
    }

    private DeliveryPartnerClient client(DeliveryPartner partner) {
        DeliveryPartnerClient client = clients.get(partner);
        if (client == null || !client.isEnabled()) {
            throw new PartnerNotConfiguredException();
        }
        return client;
    }
}
