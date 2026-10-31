package com.bank.digitalcore.carddispatch.api;

import com.bank.digitalcore.carddispatch.application.CardDispatchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Stable bank-facing API; partner selection remains an internal service concern.
 */
@RestController
@RequestMapping(CardDispatchConstants.API_BASE_PATH)
@Validated
public class CardDispatchController {
    private static final Logger log = LoggerFactory.getLogger(CardDispatchController.class);
    private final CardDispatchService cardDispatchService;

    /**
     * Creates the controller using the dispatch application service.
     */
    public CardDispatchController(CardDispatchService cardDispatchService) {
        this.cardDispatchService = cardDispatchService;
    }

    /**
     * Creates a dispatch and returns a stable, carrier-neutral receipt.
     */
    @PostMapping
    public ResponseEntity<CreateDispatchResponse> createDispatch(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDispatchRequest request
    ) {
        log.info("Create dispatch endpoint reached deliveryPartner={}", request.deliveryPartner());
        CreateDispatchResponse response = cardDispatchService.createDispatch(request, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path(CardDispatchConstants.TRACKING_PATH).buildAndExpand(response.dispatchId()).toUri();
        ResponseEntity<CreateDispatchResponse> result = response.bookingPending()
                ? ResponseEntity.accepted().location(location).body(response)
                : ResponseEntity.created(location).body(response);
        log.info("Create dispatch endpoint completed dispatchId={} status={} bookingPending={}",
                response.dispatchId(), response.status(), response.bookingPending());
        return result;
    }

    /**
     * Gets a fresh normalized tracking response for the bank mobile application.
     */
    @GetMapping(CardDispatchConstants.TRACKING_PATH)
    public TrackDispatchResponse track(@PathVariable("dispatchId") UUID dispatchId) {
        log.info("Tracking endpoint reached dispatchId={}", dispatchId);
        TrackDispatchResponse response = cardDispatchService.track(dispatchId);
        log.info("Tracking endpoint completed dispatchId={} status={} eventCount={}",
                dispatchId, response.status(), response.events().size());
        return response;
    }

    /**
     * Cancels a registered dispatch through its carrier before changing local state.
     */
    @DeleteMapping("/{dispatchId}")
    public CancelDispatchResponse cancel(@PathVariable("dispatchId") UUID dispatchId) {
        log.info("Cancel dispatch endpoint reached dispatchId={}", dispatchId);
        CancelDispatchResponse response = cardDispatchService.cancel(dispatchId);
        log.info("Cancel dispatch endpoint completed dispatchId={} status={}", dispatchId, response.status());
        return response;
    }
}
