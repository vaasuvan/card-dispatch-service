package com.bank.digitalcore.carddispatch.application;

import com.bank.digitalcore.carddispatch.adapter.partner.CardDispatchProperties;
import com.bank.digitalcore.carddispatch.api.CreateDispatchRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** Produces keyed digests so raw idempotency keys and recipient data are not stored. */
@Component
public class CardDispatchIdempotency {
    private final SecretKeySpec hmacKey;

    /** Loads the HMAC key used to fingerprint idempotency keys and canonical request data. */
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

    /** Fingerprints request fields in a stable, unambiguous order without storing recipient data. */
    public String requestFingerprint(CreateDispatchRequest request) {
        List<String> fields = new ArrayList<>();
        fields.add(request.deliveryPartner().name());
        fields.add(request.recipient().recipientName());
        fields.add(request.recipient().addressLine1());
        fields.add(request.recipient().addressLine2());
        fields.add(request.recipient().city());
        fields.add(request.recipient().stateOrProvince());
        fields.add(request.recipient().postalCode());
        fields.add(request.recipient().countryCode());
        fields.add(request.recipient().phoneNumber());
        fields.add(request.shipment().weightKg().stripTrailingZeros().toPlainString());
        fields.add(request.shipment().lengthCm().stripTrailingZeros().toPlainString());
        fields.add(request.shipment().widthCm().stripTrailingZeros().toPlainString());
        fields.add(request.shipment().heightCm().stripTrailingZeros().toPlainString());
        StringBuilder canonical = new StringBuilder();
        for (String field : fields) {
            if (field == null) {
                canonical.append("-1:");
            } else {
                canonical.append(field.length()).append(':').append(field);
            }
        }
        return fingerprint(canonical.toString());
    }
}
