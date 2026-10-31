package com.bank.digitalcore.carddispatch.api;

import java.time.Instant;

/**
 * Safe, stable error body that never includes upstream carrier response details.
 */
public record CardDispatchApiError(
        Instant timestamp,
        String code,
        String message,
        String path,
        String correlationId
) {
}
