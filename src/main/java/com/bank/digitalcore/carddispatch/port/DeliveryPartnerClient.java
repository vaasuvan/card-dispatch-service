package com.bank.digitalcore.carddispatch.port;

import com.bank.digitalcore.carddispatch.domain.*;

/**
 * Dedicated HTTP integration contract for a single delivery partner.
 */
public interface DeliveryPartnerClient {
    /**
     * Identifies the carrier handled by this client.
     */
    DeliveryPartner partner();

    /**
     * Returns whether the client's endpoint and credentials are configured and enabled.
     */
    boolean isEnabled();

    /**
     * Books the shipment and returns the tracking reference issued by this carrier.
     */
    CarrierBooking book(CardDispatch dispatch, RecipientAddress recipient, ShipmentDetails shipment);

    /**
     * Calls the carrier tracking endpoint and maps the response to the bank model.
     */
    TrackingSnapshot track(CardDispatch dispatch);

    /**
     * Requests cancellation and returns only after the carrier confirms it.
     */
    void cancel(CardDispatch dispatch);
}
