package com.bank.digitalcore.carddispatch.port;

import com.bank.digitalcore.carddispatch.domain.CardDispatch;

/**
 * Result of creating an idempotent booking record.
 */
public record BookingRegistration(CardDispatch dispatch, boolean newlyCreated) {
}
