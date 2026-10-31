package com.bank.digitalcore.carddispatch.api;

import com.bank.digitalcore.carddispatch.domain.DispatchStatus;
import com.bank.digitalcore.carddispatch.domain.TrackingEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Immutable mobile-facing tracking response independent of carrier response schemas.
 */
public record TrackDispatchResponse(
        UUID dispatchId,
        DispatchStatus status,
        String statusDescription,
        LocalDate estimatedDeliveryDate,
        Instant lastUpdatedAt,
        List<TrackingEvent> events
) {
    /**
     * Copies events to prevent mutation after response creation.
     */
    public TrackDispatchResponse {
        events = List.copyOf(events);
    }
}
