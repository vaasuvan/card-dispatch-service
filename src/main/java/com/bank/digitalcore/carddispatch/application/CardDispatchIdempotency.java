package com.bank.digitalcore.carddispatch.application;

import com.bank.digitalcore.carddispatch.adapter.partner.CardDispatchProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/** Produces keyed digests so raw idempotency keys and recipient data are not stored. */
@Component
public class CardDispatchIdempotency {
    private final SecretKeySpec hmacKey;

    /** Loads the HMAC key when configured; enabled partner validation requires it at startup. */
    public CardDispatchIdempotency(CardDispatchProperties properties) {
        String encodedKey = properties.getIdempotencyHmacKey();
        if (encodedKey == null || encodedKey.isBlank()) {
            this.hmacKey = null;
            return;
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Card dispatch idempotency key must be base64 encoded", exception);
        }
        if (key.length != 32) {
            throw new IllegalStateException("Card dispatch idempotency key must be 32 bytes");
        }
        this.hmacKey = new SecretKeySpec(key, "HmacSHA256");
    }

    /** Returns an HMAC-SHA-256 hex digest for the supplied key or canonical request value. */
    public String fingerprint(String value) {
        if (hmacKey == null) {
            throw new IllegalStateException("Card dispatch idempotency HMAC key is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to fingerprint card dispatch data", exception);
        }
    }
}
