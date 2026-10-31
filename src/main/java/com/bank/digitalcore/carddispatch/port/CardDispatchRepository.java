package com.bank.digitalcore.carddispatch.port;

import com.bank.digitalcore.carddispatch.domain.CardDispatch;
import com.bank.digitalcore.carddispatch.domain.CarrierBooking;
import com.bank.digitalcore.carddispatch.domain.DispatchStatus;
import com.bank.digitalcore.carddispatch.domain.TrackingSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for dispatch registrations and the latest normalized snapshot.
 */
public interface CardDispatchRepository {
    /**
     * Creates a pending carrier booking before any remote side effect.
     */
    BookingRegistration createBooking(CardDispatch dispatch);

    /**
     * Persists the carrier-issued tracking number after confirmed shipment booking.
     */
    void completeBooking(UUID dispatchId, CarrierBooking booking);

    /**
     * Finds the internal carrier routing data by bank dispatch identifier.
     */
    Optional<CardDispatch> find(UUID dispatchId);

    /**
     * Persists the normalized status and event timeline returned by a carrier.
     */
    void saveSnapshot(TrackingSnapshot snapshot);

    /**
     * Persists a locally confirmed cancellation state and timestamp.
     */
    void markCanceled(java.util.UUID dispatchId, java.time.Instant canceledAt);

    /**
     * Returns the locally stored status for cancel idempotency and terminal-state checks.
     */
    DispatchStatus findStatus(java.util.UUID dispatchId);

    /**
     * Loads the last normalized snapshot without contacting a partner.
     */
    java.util.Optional<TrackingSnapshot> findSnapshot(java.util.UUID dispatchId);
}
