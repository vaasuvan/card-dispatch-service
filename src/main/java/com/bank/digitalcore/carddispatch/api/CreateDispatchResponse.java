package com.bank.digitalcore.carddispatch.api;

import com.bank.digitalcore.carddispatch.domain.DispatchStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, partner-neutral response returned when a dispatch is created or booking is pending.
 */
public record CreateDispatchResponse(
        UUID dispatchId,
        DispatchStatus status,
        Instant createdAt,
        boolean bookingPending
) {
}
