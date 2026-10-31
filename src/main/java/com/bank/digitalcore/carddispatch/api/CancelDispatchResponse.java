package com.bank.digitalcore.carddispatch.api;

import com.bank.digitalcore.carddispatch.domain.DispatchStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable acknowledgment of a carrier-confirmed cancellation.
 */
public record CancelDispatchResponse(UUID dispatchId, DispatchStatus status, Instant canceledAt) {
}
