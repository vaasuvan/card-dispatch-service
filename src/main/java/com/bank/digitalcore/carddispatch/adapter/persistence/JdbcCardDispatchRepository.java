package com.bank.digitalcore.carddispatch.adapter.persistence;

import com.bank.digitalcore.carddispatch.domain.*;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.DispatchNotFoundException;
import com.bank.digitalcore.carddispatch.port.BookingRegistration;
import com.bank.digitalcore.carddispatch.port.CardDispatchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL adapter for idempotent booking, routing, and normalized status history.
 */
@Repository
public class JdbcCardDispatchRepository implements CardDispatchRepository {
    private final JdbcTemplate jdbc;

    /**
     * Creates the repository backed by Spring JDBC.
     */
    public JdbcCardDispatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Commits a pending row before the application makes its carrier call.
     * A duplicate key returns the existing row so callers do not repeat an uncertain booking.
     */
    @Override
    @Transactional
    public BookingRegistration createBooking(CardDispatch dispatch) {
        int inserted = jdbc.update("""
                        INSERT INTO card_dispatches
                            (dispatch_id, customer_reference, partner_code, tracking_number, dispatched_at,
                             idempotency_key_hash, request_fingerprint, current_status)
                        VALUES (?, ?, ?, NULL, ?, ?, ?, 'BOOKING_PENDING')
                        ON CONFLICT (customer_reference, idempotency_key_hash) DO NOTHING
                        """, dispatch.dispatchId(), dispatch.customerReference(), dispatch.deliveryPartner().name(),
                Timestamp.from(dispatch.dispatchedAt()), dispatch.idempotencyKeyHash(), dispatch.requestFingerprint());
        CardDispatch result = inserted == 1
                ? find(dispatch.dispatchId()).orElseThrow(DispatchNotFoundException::new)
                : findByIdempotencyKey(dispatch.customerReference(), dispatch.idempotencyKeyHash())
                .orElseThrow(DispatchNotFoundException::new);
        if (inserted == 0 && !result.requestFingerprint().equals(dispatch.requestFingerprint())) {
            throw new com.bank.digitalcore.carddispatch.domain.DispatchExceptions.DispatchConflictException();
        }
        if (inserted == 1) {
            jdbc.update("""
                    INSERT INTO card_dispatch_events (dispatch_id, status, description, occurred_at)
                    VALUES (?, 'BOOKING_PENDING', 'Shipment booking requested', ?)
                    """, dispatch.dispatchId(), Timestamp.from(dispatch.dispatchedAt()));
        }
        return new BookingRegistration(result, inserted == 1);
    }

    /**
     * Stores the carrier reference only after a successful booking response.
     */
    @Override
    @Transactional
    public void completeBooking(UUID dispatchId, CarrierBooking booking) {
        int updated = jdbc.update("""
                UPDATE card_dispatches
                SET tracking_number = ?, current_status = 'DISPATCHED', last_updated_at = ?,
                    status_description = 'Shipment booked with delivery partner',
                    refreshed_at = CURRENT_TIMESTAMP
                WHERE dispatch_id = ? AND current_status = 'BOOKING_PENDING'
                """, booking.trackingNumber(), Timestamp.from(booking.bookedAt()), dispatchId);
        if (updated != 1) {
            throw new IllegalStateException("Dispatch booking state changed before carrier result was stored");
        }
        jdbc.update("""
                INSERT INTO card_dispatch_events (dispatch_id, status, description, occurred_at)
                VALUES (?, 'DISPATCHED', 'Shipment booked with delivery partner', ?)
                """, dispatchId, Timestamp.from(booking.bookedAt()));
    }

    /**
     * Finds the pending or completed dispatch created with a customer-scoped idempotency key.
     */
    private Optional<CardDispatch> findByIdempotencyKey(String customerReference, String idempotencyKeyHash) {
        return findOne("""
                SELECT dispatch_id, customer_reference, partner_code, tracking_number, dispatched_at,
                       idempotency_key_hash, request_fingerprint
                FROM card_dispatches WHERE customer_reference = ? AND idempotency_key_hash = ?
                """, customerReference, idempotencyKeyHash);
    }

    /**
     * Finds a dispatch by the bank-owned ID.
     */
    @Override
    public Optional<CardDispatch> find(UUID dispatchId) {
        return findOne("""
                SELECT dispatch_id, customer_reference, partner_code, tracking_number, dispatched_at,
                       idempotency_key_hash, request_fingerprint
                FROM card_dispatches WHERE dispatch_id = ?
                """, dispatchId);
    }

    private Optional<CardDispatch> findOne(String sql, Object... args) {
        List<CardDispatch> result = jdbc.query(sql, (rs, row) -> new CardDispatch(
                rs.getObject("dispatch_id", UUID.class),
                rs.getString("customer_reference"),
                DeliveryPartner.valueOf(rs.getString("partner_code")),
                rs.getString("tracking_number"),
                rs.getTimestamp("dispatched_at").toInstant(),
                rs.getString("idempotency_key_hash"),
                rs.getString("request_fingerprint")
        ), args);
        return result.stream().findFirst();
    }

    /**
     * Replaces the persisted carrier event view with the normalized snapshot.
     */
    @Override
    @Transactional
    public void saveSnapshot(TrackingSnapshot snapshot) {
        int updated = jdbc.update("""
                        UPDATE card_dispatches
                        SET current_status = ?, status_description = ?, estimated_delivery_date = ?,
                            last_updated_at = ?, refreshed_at = CURRENT_TIMESTAMP
                        WHERE dispatch_id = ?
                        """, snapshot.status().name(), snapshot.statusDescription(),
                snapshot.estimatedDeliveryDate(), Timestamp.from(snapshot.lastUpdatedAt()), snapshot.dispatchId());
        if (updated != 1) {
            throw new DispatchNotFoundException();
        }
        jdbc.update("DELETE FROM card_dispatch_events WHERE dispatch_id = ?", snapshot.dispatchId());
        for (TrackingEvent event : snapshot.events()) {
            jdbc.update("""
                            INSERT INTO card_dispatch_events (dispatch_id, status, description, occurred_at, location)
                            VALUES (?, ?, ?, ?, ?)
                            """, snapshot.dispatchId(), event.status().name(), event.description(),
                    Timestamp.from(event.occurredAt()), event.location());
        }
    }

    /**
     * Persists a carrier-confirmed cancellation and its status event atomically.
     */
    @Override
    @Transactional
    public void markCanceled(UUID dispatchId, Instant canceledAt) {
        int updated = jdbc.update("""
                UPDATE card_dispatches
                SET current_status = 'CANCELED', status_description = 'Dispatch canceled',
                    last_updated_at = ?, refreshed_at = CURRENT_TIMESTAMP
                WHERE dispatch_id = ?
                """, Timestamp.from(canceledAt), dispatchId);
        if (updated != 1) {
            throw new DispatchNotFoundException();
        }
        jdbc.update("""
                INSERT INTO card_dispatch_events (dispatch_id, status, description, occurred_at)
                VALUES (?, 'CANCELED', 'Dispatch canceled', ?)
                """, dispatchId, Timestamp.from(canceledAt));
    }

    /**
     * Reads the current bank-owned enum state for a registered dispatch.
     */
    @Override
    public DispatchStatus findStatus(UUID dispatchId) {
        List<DispatchStatus> status = jdbc.query("""
                SELECT current_status FROM card_dispatches WHERE dispatch_id = ?
                """, (rs, row) -> DispatchStatus.valueOf(rs.getString("current_status")), dispatchId);
        return status.stream().findFirst().orElseThrow(DispatchNotFoundException::new);
    }

    /**
     * Loads the latest persisted normalized view for terminal dispatches.
     */
    @Override
    public Optional<TrackingSnapshot> findSnapshot(UUID dispatchId) {
        List<TrackingSnapshot> snapshots = jdbc.query("""
                SELECT current_status, status_description, estimated_delivery_date, last_updated_at
                FROM card_dispatches WHERE dispatch_id = ?
                """, (rs, row) -> {
            java.sql.Date estimate = rs.getDate("estimated_delivery_date");
            Timestamp updatedAt = rs.getTimestamp("last_updated_at");
            return new TrackingSnapshot(dispatchId,
                    DispatchStatus.valueOf(rs.getString("current_status")),
                    rs.getString("status_description"),
                    estimate == null ? null : estimate.toLocalDate(),
                    updatedAt == null ? Instant.EPOCH : updatedAt.toInstant(),
                    List.of());
        }, dispatchId);
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        TrackingSnapshot snapshot = snapshots.getFirst();
        List<TrackingEvent> events = jdbc.query("""
                SELECT status, description, occurred_at, location
                FROM card_dispatch_events WHERE dispatch_id = ? ORDER BY occurred_at DESC
                """, (rs, row) -> new TrackingEvent(
                DispatchStatus.valueOf(rs.getString("status")),
                rs.getString("description"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("location")
        ), dispatchId);
        return Optional.of(new TrackingSnapshot(snapshot.dispatchId(), snapshot.status(),
                snapshot.statusDescription(), snapshot.estimatedDeliveryDate(), snapshot.lastUpdatedAt(), events));
    }
}
