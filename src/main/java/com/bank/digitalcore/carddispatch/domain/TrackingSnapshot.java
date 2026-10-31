package com.bank.digitalcore.carddispatch.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The stable bank API view of the latest tracking lookup.
 */
public record TrackingSnapshot(
        UUID dispatchId,
        DispatchStatus status,
        String statusDescription,
        LocalDate estimatedDeliveryDate,
        Instant lastUpdatedAt,
        List<TrackingEvent> events
) {
    /**
     * Creates an immutable snapshot with an immutable event list.
     */
    public TrackingSnapshot {
        events = List.copyOf(events);
    }
}
