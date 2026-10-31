package com.bank.digitalcore.carddispatch.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal dispatch identity and carrier reference; the mobile contract hides carrier details.
 */
public record CardDispatch(
        UUID dispatchId,
        String customerReference,
        DeliveryPartner deliveryPartner,
        String trackingNumber,
        Instant dispatchedAt,
        String idempotencyKey
) {
}
