package com.bank.digitalcore.carddispatch.application;

import com.bank.digitalcore.carddispatch.adapter.partner.CardDispatchProperties;
import com.bank.digitalcore.carddispatch.api.CreateDispatchRequest;
import com.bank.digitalcore.carddispatch.domain.DeliveryPartner;
import com.bank.digitalcore.carddispatch.domain.RecipientAddress;
import com.bank.digitalcore.carddispatch.domain.ShipmentDetails;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Covers keyed request fingerprints used to protect booking idempotency records. */
class CardDispatchIdempotencyTest {
    private final CardDispatchProperties properties = configuredProperties();
    private final CardDispatchIdempotency idempotency = new CardDispatchIdempotency(properties);

    @Test
    void fingerprintsAreStableAndDifferentForDifferentValues() {
        assertEquals(idempotency.fingerprint("request-a"), idempotency.fingerprint("request-a"));
        assertNotEquals(idempotency.fingerprint("request-a"), idempotency.fingerprint("request-b"));
    }

    @Test
    void requestFingerprintIsStableAndDoesNotDependOnDecimalScale() {
        CreateDispatchRequest first = request("0.20");
        CreateDispatchRequest second = request("0.2");

        assertEquals(idempotency.requestFingerprint(first), idempotency.requestFingerprint(second));
        assertNotEquals(idempotency.requestFingerprint(first), idempotency.requestFingerprint(request("0.3")));
    }

    private CreateDispatchRequest request(String weight) {
        return new CreateDispatchRequest(DeliveryPartner.DHL,
                new RecipientAddress("Recipient", "Street", null, "Colombo",
                        null, "00100", "LK", "+94112345678"),
                new ShipmentDetails(new java.math.BigDecimal(weight), new java.math.BigDecimal("10"),
                        new java.math.BigDecimal("8"), new java.math.BigDecimal("2")));
    }

    private CardDispatchProperties configuredProperties() {
        CardDispatchProperties configured = new CardDispatchProperties();
        configured.setIdempotencyHmacKey(Base64.getEncoder().encodeToString(new byte[32]));
        return configured;
    }
}
