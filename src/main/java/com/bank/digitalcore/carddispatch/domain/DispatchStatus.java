package com.bank.digitalcore.carddispatch.domain;

/**
 * Bank-normalized delivery lifecycle states exposed independently of carrier APIs.
 */
public enum DispatchStatus {
    BOOKING_PENDING,
    BOOKING_FAILED,
    REGISTERED,
    DISPATCHED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERY_ATTEMPTED,
    DELIVERED,
    RETURNED,
    CANCELED,
    EXCEPTION,
    UNKNOWN
}
