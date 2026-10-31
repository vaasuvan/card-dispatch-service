package com.bank.digitalcore.carddispatch.application;

import com.bank.digitalcore.carddispatch.adapter.partner.CardDispatchProperties;
import com.bank.digitalcore.carddispatch.api.CancelDispatchResponse;
import com.bank.digitalcore.carddispatch.api.CreateDispatchRequest;
import com.bank.digitalcore.carddispatch.api.CreateDispatchResponse;
import com.bank.digitalcore.carddispatch.api.TrackDispatchResponse;
import com.bank.digitalcore.carddispatch.domain.CardDispatch;
import com.bank.digitalcore.carddispatch.domain.CarrierBooking;
import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.DispatchNotCancelableException;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerNotConfiguredException;
import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.PartnerUnavailableException;
import com.bank.digitalcore.carddispatch.domain.DispatchStatus;
import com.bank.digitalcore.carddispatch.domain.TrackingSnapshot;
import com.bank.digitalcore.carddispatch.domain.RecipientAddress;
import com.bank.digitalcore.carddispatch.domain.ShipmentDetails;
import com.bank.digitalcore.carddispatch.port.BookingRegistration;
import com.bank.digitalcore.carddispatch.port.CardDispatchRepository;
import com.bank.digitalcore.carddispatch.port.DeliveryPartnerRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers unauthenticated dispatch creation, tracking, and cancellation orchestration.
 */
class CardDispatchServiceTest {
    private final CardDispatchRepository repository = mock(CardDispatchRepository.class);
    private final DeliveryPartnerRegistry partnerRegistry = mock(DeliveryPartnerRegistry.class);
    private final CardDispatchProperties properties = configuredProperties();
    private final CardDispatchIdempotency idempotency = new CardDispatchIdempotency(properties);
    private final CardDispatchService service =
            new CardDispatchService(repository, partnerRegistry, idempotency);

    /** Books through the selected partner without exposing its tracking reference in the response. */
    @Test
    void createsDispatchUsingConfiguredPartner() {
        when(partnerRegistry.supports(DeliveryPartner.DHL)).thenReturn(true);
        when(repository.createBooking(any())).thenAnswer(invocation ->
                new BookingRegistration(invocation.getArgument(0), true));
        when(partnerRegistry.book(any(), any(), any())).thenReturn(
                new CarrierBooking("tracking-reference", Instant.parse("2026-10-30T08:01:00Z")));

        CreateDispatchResponse response = service.createDispatch(
                request(), "request-key");

        assertEquals(DispatchStatus.DISPATCHED, response.status());
        verify(repository).createBooking(org.mockito.ArgumentMatchers.argThat((CardDispatch dispatch) ->
                dispatch.dispatchId().equals(response.dispatchId())
                        && dispatch.deliveryPartner() == DeliveryPartner.DHL
                        && dispatch.trackingNumber() == null));
        verify(repository).completeBooking(response.dispatchId(),
                new CarrierBooking("tracking-reference", Instant.parse("2026-10-30T08:01:00Z")));
    }

    /**
     * Does not register dispatches when their dedicated partner client is disabled.
     */
    @Test
    void rejectsCreationForDisabledPartner() {
        when(partnerRegistry.supports(DeliveryPartner.DHL)).thenReturn(false);

        assertThrows(PartnerNotConfiguredException.class, () -> service.createDispatch(
                request(), "request-key"));

        verify(repository, never()).createBooking(any());
    }

    /** Routes a create request to the carrier explicitly selected in its API body. */
    @Test
    void honorsPartnerSelectedInCreateRequest() {
        when(partnerRegistry.supports(DeliveryPartner.FEDEX)).thenReturn(true);
        when(repository.createBooking(any())).thenAnswer(invocation ->
                new BookingRegistration(invocation.getArgument(0), true));
        when(partnerRegistry.book(any(), any(), any())).thenReturn(
                new CarrierBooking("fedex-reference", Instant.parse("2026-10-30T08:01:00Z")));

        CreateDispatchResponse response = service.createDispatch(request(DeliveryPartner.FEDEX), "fedex-key");

        assertEquals(DispatchStatus.DISPATCHED, response.status());
        verify(repository).createBooking(org.mockito.ArgumentMatchers.argThat((CardDispatch dispatch) ->
                dispatch.dispatchId().equals(response.dispatchId())
                        && dispatch.deliveryPartner() == DeliveryPartner.FEDEX));
        verify(partnerRegistry).book(org.mockito.ArgumentMatchers.argThat(dispatch ->
                dispatch.deliveryPartner() == DeliveryPartner.FEDEX), any(), any());
    }

    /**
     * Refreshes and persists normalized partner status only for the authenticated owner.
     */
    @Test
    void tracksDispatchWithoutCustomerIdentity() {
        UUID id = UUID.randomUUID();
        CardDispatch dispatch = dispatch(id, "customer-1");
        TrackingSnapshot snapshot = new TrackingSnapshot(id, DispatchStatus.IN_TRANSIT, "Moving",
                LocalDate.parse("2026-11-02"), Instant.parse("2026-10-30T09:00:00Z"), List.of());
        when(repository.find(id)).thenReturn(Optional.of(dispatch));
        when(repository.findStatus(id)).thenReturn(DispatchStatus.REGISTERED);
        when(partnerRegistry.track(dispatch)).thenReturn(snapshot);

        TrackDispatchResponse response = service.track(id);

        assertEquals(DispatchStatus.IN_TRANSIT, response.status());
        verify(repository).saveSnapshot(snapshot);
    }

    /**
     * Returns the locally confirmed canceled view without recontacting the carrier.
     */
    @Test
    void trackingCanceledDispatchDoesNotCallCarrier() {
        UUID id = UUID.randomUUID();
        CardDispatch dispatch = dispatch(id, "customer-1");
        TrackingSnapshot snapshot = new TrackingSnapshot(id, DispatchStatus.CANCELED, "Dispatch canceled",
                null, Instant.parse("2026-10-30T10:00:00Z"), List.of());
        when(repository.find(id)).thenReturn(Optional.of(dispatch));
        when(repository.findStatus(id)).thenReturn(DispatchStatus.CANCELED);
        when(repository.findSnapshot(id)).thenReturn(Optional.of(snapshot));

        TrackDispatchResponse response = service.track(id);

        assertEquals(DispatchStatus.CANCELED, response.status());
        verify(partnerRegistry, never()).track(any());
        verify(repository, never()).saveSnapshot(any());
    }

    @Test
    void trackingPendingBookingReturnsPendingStatusWithoutCallingCarrier() {
        UUID id = UUID.randomUUID();
        CardDispatch pending = new CardDispatch(id, "customer-1", DeliveryPartner.DHL, null,
                Instant.parse("2026-10-30T08:00:00Z"), "request-key-hash", "request-fingerprint");
        when(repository.find(id)).thenReturn(Optional.of(pending));
        when(repository.findStatus(id)).thenReturn(DispatchStatus.BOOKING_PENDING);

        TrackDispatchResponse response = service.track(id);

        assertEquals(DispatchStatus.BOOKING_PENDING, response.status());
        assertEquals("Shipment booking is pending", response.statusDescription());
        verify(partnerRegistry, never()).track(any());
        verify(repository, never()).saveSnapshot(any());
    }

    /**
     * Tracks a dispatch without requiring customer identity.
     */
    @Test
    void trackDoesNotRequireCustomerOwnershipHeader() {
        UUID id = UUID.randomUUID();
        when(repository.find(id)).thenReturn(Optional.of(dispatch(id, "customer-1")));
        TrackingSnapshot snapshot = new TrackingSnapshot(id, DispatchStatus.IN_TRANSIT, "Moving",
                LocalDate.parse("2026-11-02"), Instant.parse("2026-10-30T09:00:00Z"), List.of());
        when(repository.findStatus(id)).thenReturn(DispatchStatus.REGISTERED);
        when(partnerRegistry.track(any())).thenReturn(snapshot);

        assertEquals(DispatchStatus.IN_TRANSIT, service.track(id).status());

        verify(partnerRegistry).track(any());
    }

    /**
     * Calls the carrier before marking a dispatch canceled in local storage.
     */
    @Test
    void cancelsDispatchAndPersistsCarrierConfirmation() {
        UUID id = UUID.randomUUID();
        CardDispatch dispatch = dispatch(id, "customer-1");
        when(repository.find(id)).thenReturn(Optional.of(dispatch));
        when(repository.findStatus(id)).thenReturn(DispatchStatus.IN_TRANSIT);
        when(partnerRegistry.supports(DeliveryPartner.DHL)).thenReturn(true);

        CancelDispatchResponse response = service.cancel(id);

        assertEquals(DispatchStatus.CANCELED, response.status());
        verify(partnerRegistry).cancel(dispatch);
        verify(repository).markCanceled(id, response.canceledAt());
    }

    /**
     * Returns the previous cancellation result without repeating carrier-side cancellation.
     */
    @Test
    void cancellationIsIdempotentAfterLocalConfirmation() {
        UUID id = UUID.randomUUID();
        Instant canceledAt = Instant.parse("2026-10-30T10:00:00Z");
        CardDispatch dispatch = dispatch(id, "customer-1");
        TrackingSnapshot saved = new TrackingSnapshot(id, DispatchStatus.CANCELED, "Dispatch canceled",
                null, canceledAt, List.of());
        when(repository.find(id)).thenReturn(Optional.of(dispatch));
        when(repository.findStatus(id)).thenReturn(DispatchStatus.CANCELED);
        when(repository.findSnapshot(id)).thenReturn(Optional.of(saved));

        CancelDispatchResponse response = service.cancel(id);

        assertEquals(canceledAt, response.canceledAt());
        verify(partnerRegistry, never()).cancel(any());
    }

    /**
     * Rejects cancellation after a terminal delivery outcome.
     */
    @Test
    void deliveredDispatchCannotBeCanceled() {
        UUID id = UUID.randomUUID();
        when(repository.find(id)).thenReturn(Optional.of(dispatch(id, "customer-1")));
        when(repository.findStatus(id)).thenReturn(DispatchStatus.DELIVERED);

        assertThrows(DispatchNotCancelableException.class, () -> service.cancel(id));

        verify(partnerRegistry, never()).cancel(any());
    }

    /**
     * Does not persist cancellation when the carrier rejects or cannot confirm the request.
     */
    @Test
    void carrierFailureDoesNotMarkDispatchCanceled() {
        UUID id = UUID.randomUUID();
        CardDispatch dispatch = dispatch(id, "customer-1");
        when(repository.find(id)).thenReturn(Optional.of(dispatch));
        when(repository.findStatus(id)).thenReturn(DispatchStatus.IN_TRANSIT);
        when(partnerRegistry.supports(DeliveryPartner.DHL)).thenReturn(true);
        org.mockito.Mockito.doThrow(new PartnerUnavailableException()).when(partnerRegistry).cancel(dispatch);

        assertThrows(PartnerUnavailableException.class, () -> service.cancel(id));

        verify(repository, never()).markCanceled(any(), any());
    }

    private CardDispatch dispatch(UUID id, String customerReference) {
        return new CardDispatch(id, customerReference, DeliveryPartner.DHL, "tracking-reference",
                Instant.parse("2026-10-30T08:00:00Z"), "request-key-hash", "request-fingerprint");
    }

    private CreateDispatchRequest request() {
        return request(DeliveryPartner.DHL);
    }

    private CreateDispatchRequest request(DeliveryPartner partner) {
        return new CreateDispatchRequest(partner,
                new RecipientAddress("Card recipient", "100 Main Street", null, "Colombo",
                        null, "00100", "LK", "+94112345678"),
                new ShipmentDetails(new BigDecimal("0.2"), new BigDecimal("10"),
                        new BigDecimal("8"), new BigDecimal("2")));
    }

    private CardDispatchProperties configuredProperties() {
        CardDispatchProperties configured = new CardDispatchProperties();
        configured.setIdempotencyHmacKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        return configured;
    }
}
