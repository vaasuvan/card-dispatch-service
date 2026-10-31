package com.bank.digitalcore.carddispatch.api;

import com.bank.digitalcore.carddispatch.domain.RecipientAddress;
import com.bank.digitalcore.carddispatch.domain.ShipmentDetails;
import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import jakarta.validation.Valid;

/**
 * Immutable dispatch creation contract with an explicit supported delivery partner.
 */
public record CreateDispatchRequest(
        @jakarta.validation.constraints.NotNull DeliveryPartner deliveryPartner,
        @Valid @jakarta.validation.constraints.NotNull RecipientAddress recipient,
        @Valid @jakarta.validation.constraints.NotNull ShipmentDetails shipment
) {
}
