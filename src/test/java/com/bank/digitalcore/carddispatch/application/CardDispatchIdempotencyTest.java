package com.bank.digitalcore.carddispatch.application;

import com.bank.digitalcore.carddispatch.adapter.partner.CardDispatchProperties;
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

    private CardDispatchProperties configuredProperties() {
        CardDispatchProperties configured = new CardDispatchProperties();
        configured.setIdempotencyHmacKey(Base64.getEncoder().encodeToString(new byte[32]));
        return configured;
    }
}
