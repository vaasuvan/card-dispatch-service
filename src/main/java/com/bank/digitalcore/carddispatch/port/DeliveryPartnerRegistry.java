package com.bank.digitalcore.carddispatch.port;

import com.bank.digitalcore.carddispatch.domain.*;

/**
 * Routing boundary for selecting an explicit carrier client by enum value.
 */
public interface DeliveryPartnerRegistry {
    /**
     * Retrieves a normalized tracking response through the selected carrier client.
     */
    TrackingSnapshot track(CardDispatch dispatch);

    /**
     * Books a physical shipment through the selected carrier client.
     */
    CarrierBooking book(CardDispatch dispatch, RecipientAddress recipient, ShipmentDetails shipment);

    /**
     * Requests cancellation through the selected carrier client.
     */
    void cancel(CardDispatch dispatch);

    /**
     * Returns whether the configured carrier can currently be routed.
     */
    boolean supports(DeliveryPartner partner);
}
