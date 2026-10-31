package com.bank.digitalcore.carddispatch.domain;

import java.time.Instant;

/**
 * Carrier-issued shipment reference returned only to internal service logic.
 */
public record CarrierBooking(String trackingNumber, Instant bookedAt) {
}
