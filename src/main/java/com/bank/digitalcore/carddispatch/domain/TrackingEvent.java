package com.bank.digitalcore.carddispatch.domain;

import java.time.Instant;

/**
 * A customer-safe carrier tracking event normalized by the partner adapter.
 */
public record TrackingEvent(
        DispatchStatus status,
        String description,
        Instant occurredAt,
        String location
) {
}
