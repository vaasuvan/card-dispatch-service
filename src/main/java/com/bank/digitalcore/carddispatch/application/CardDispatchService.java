package com.bank.digitalcore.carddispatch.application;

import com.bank.digitalcore.carddispatch.api.CancelDispatchResponse;
import com.bank.digitalcore.carddispatch.api.CreateDispatchRequest;
import com.bank.digitalcore.carddispatch.api.CreateDispatchResponse;
import com.bank.digitalcore.carddispatch.api.TrackDispatchResponse;
import com.bank.digitalcore.carddispatch.domain.CardDispatch;
import com.bank.digitalcore.carddispatch.domain.CarrierBooking;
import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.DispatchNotCancelableException;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.DispatchNotFoundException;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerNotConfiguredException;
import com.bank.digitalcore.carddispatch.domain.DispatchStatus;
import com.bank.digitalcore.carddispatch.domain.TrackingSnapshot;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerUnavailableException;
import com.bank.digitalcore.carddispatch.port.BookingRegistration;
import com.bank.digitalcore.carddispatch.domain.RecipientAddress;
import com.bank.digitalcore.carddispatch.domain.ShipmentDetails;
import com.bank.digitalcore.carddispatch.port.CardDispatchRepository;
import com.bank.digitalcore.carddispatch.port.DeliveryPartnerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.List;

/**
 * Coordinates dispatch registration, partner lookups, and durable status snapshots.
 */
@Service
public class CardDispatchService {
    private static final String UNAUTHENTICATED_CUSTOMER_REFERENCE = "UNAUTHENTICATED";
    private static final Logger log = LoggerFactory.getLogger(CardDispatchService.class);
    private final CardDispatchRepository repository;
    private final DeliveryPartnerRegistry partnerRegistry;
    private final CardDispatchIdempotency idempotency;

    /**
     * Creates the application service using persistence and carrier-routing boundaries.
     */
    public CardDispatchService(
            CardDispatchRepository repository,
            DeliveryPartnerRegistry partnerRegistry,
            CardDispatchIdempotency idempotency
    ) {
        this.repository = repository;
        this.partnerRegistry = partnerRegistry;
        this.idempotency = idempotency;
    }

    /**
     * Persists the pending dispatch, then books with the selected carrier synchronously.
     * Existing idempotency keys are returned without another carrier booking attempt.
     */
    public CreateDispatchResponse createDispatch(
            CreateDispatchRequest request,
            String idempotencyKey
    ) {
        if (request == null || request.deliveryPartner() == null
                || request.recipient() == null || request.shipment() == null
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Delivery partner, shipment details, and Idempotency-Key are required");
        }
        DeliveryPartner deliveryPartner = request.deliveryPartner();
        log.info("Card dispatch requested deliveryPartner={}", deliveryPartner);
        if (!partnerRegistry.supports(deliveryPartner)) {
            log.warn("Card dispatch rejected because carrier is unavailable deliveryPartner={}", deliveryPartner);
            throw new PartnerNotConfiguredException();
        }
        var dispatchedAt = java.time.Instant.now();
        CardDispatch dispatch = new CardDispatch(UUID.randomUUID(), UNAUTHENTICATED_CUSTOMER_REFERENCE,
                deliveryPartner, null, dispatchedAt, idempotency.fingerprint(idempotencyKey.trim()),
                idempotency.requestFingerprint(request));
        BookingRegistration registration = repository.createBooking(dispatch);
        CardDispatch persisted = registration.dispatch();
        if (!registration.newlyCreated()) {
            // The original booking may have reached the carrier; replaying it could create a duplicate shipment.
            DispatchStatus savedStatus = repository.findStatus(persisted.dispatchId());
            log.info("Idempotent card dispatch replay deliveryPartner={} status={}",
                    deliveryPartner, savedStatus);
            return new CreateDispatchResponse(persisted.dispatchId(), savedStatus, persisted.dispatchedAt(),
                    savedStatus == DispatchStatus.BOOKING_PENDING);
        }
        try {
            // The remote call happens only after the pending row commits, so a process failure is recoverable by ID.
            CarrierBooking booking = partnerRegistry.book(persisted, request.recipient(), request.shipment());
            repository.completeBooking(persisted.dispatchId(), booking);
            log.info("Card shipment booked deliveryPartner={} status={}", deliveryPartner, DispatchStatus.DISPATCHED);
            return new CreateDispatchResponse(persisted.dispatchId(), DispatchStatus.DISPATCHED,
                    persisted.dispatchedAt(), false);
        } catch (PartnerUnavailableException exception) {
            // A timeout can hide a successful carrier booking; leave it pending rather than retrying unsafely.
            log.warn("Carrier booking outcome unresolved; dispatch remains pending partner={} errorType={}",
                    deliveryPartner, exception.getClass().getSimpleName());
            return new CreateDispatchResponse(persisted.dispatchId(), DispatchStatus.BOOKING_PENDING,
                    persisted.dispatchedAt(), true);
        }
    }


    /**
     * Resolves the selected carrier, refreshes the snapshot, and returns the stable bank view.
     */
    public TrackDispatchResponse track(UUID dispatchId) {
        CardDispatch dispatch = repository.find(dispatchId).orElseThrow(DispatchNotFoundException::new);
        log.debug("Card dispatch tracking requested deliveryPartner={}", dispatch.deliveryPartner());
        DispatchStatus status = repository.findStatus(dispatchId);
        if (status == DispatchStatus.BOOKING_PENDING && dispatch.trackingNumber() == null) {
            // The carrier has not returned a tracking number yet; tracking cannot be queried by AWB/reference.
            log.info("Carrier booking is still pending; returning local status dispatchId={}", dispatchId);
            return new TrackDispatchResponse(dispatchId, status, "Shipment booking is pending",
                    null, dispatch.dispatchedAt(), List.of());
        }
        if (dispatch.trackingNumber() == null && status != DispatchStatus.CANCELED) {
            log.error("Dispatch has no carrier tracking number despite status dispatchId={} status={}",
                    dispatchId, status);
            throw new PartnerUnavailableException();
        }
        boolean canceled = status == DispatchStatus.CANCELED;
        TrackingSnapshot snapshot = canceled
                ? repository.findSnapshot(dispatchId).orElseThrow(DispatchNotFoundException::new)
                : partnerRegistry.track(dispatch);
        if (!canceled) {
            repository.saveSnapshot(snapshot);
        }
        log.info("Card dispatch tracking refreshed status={}", snapshot.status());
        return new TrackDispatchResponse(snapshot.dispatchId(), snapshot.status(), snapshot.statusDescription(),
                snapshot.estimatedDeliveryDate(), snapshot.lastUpdatedAt(), snapshot.events());
    }

    /**
     * Cancels with the carrier first, then persists cancellation locally after carrier confirmation.
     */
    public CancelDispatchResponse cancel(UUID dispatchId) {
        CardDispatch dispatch = repository.find(dispatchId).orElseThrow(DispatchNotFoundException::new);
        DispatchStatus status = repository.findStatus(dispatchId);
        if (status == DispatchStatus.CANCELED) {
            TrackingSnapshot saved = repository.findSnapshot(dispatchId).orElseThrow(DispatchNotFoundException::new);
            return new CancelDispatchResponse(dispatchId, DispatchStatus.CANCELED, saved.lastUpdatedAt());
        }
        if (status == DispatchStatus.DELIVERED || status == DispatchStatus.RETURNED
                || status == DispatchStatus.EXCEPTION || status == DispatchStatus.BOOKING_PENDING) {
            throw new DispatchNotCancelableException();
        }
        if (dispatch.trackingNumber() == null) {
            log.error("Cannot cancel dispatch without carrier tracking number dispatchId={} status={}",
                    dispatchId, status);
            throw new PartnerUnavailableException();
        }
        if (!partnerRegistry.supports(dispatch.deliveryPartner())) {
            log.warn("Card dispatch cancellation rejected because carrier is unavailable deliveryPartner={}",
                    dispatch.deliveryPartner());
            throw new PartnerNotConfiguredException();
        }
        partnerRegistry.cancel(dispatch);
        var canceledAt = java.time.Instant.now();
        repository.markCanceled(dispatchId, canceledAt);
        log.info("Card dispatch canceled deliveryPartner={}", dispatch.deliveryPartner());
        return new CancelDispatchResponse(dispatchId, DispatchStatus.CANCELED, canceledAt);
    }
}
