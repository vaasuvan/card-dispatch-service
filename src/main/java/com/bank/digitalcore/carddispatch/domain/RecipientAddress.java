package com.bank.digitalcore.carddispatch.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Recipient address supplied by the trusted card-fulfillment backend for carrier booking.
 */
public record RecipientAddress(
        @NotBlank @Size(max = 128)
        String recipientName,
        @NotBlank @Size(max = 200)
        String addressLine1,
        @Size(max = 200)
        String addressLine2,
        @NotBlank
        @Size(max = 100)
        String city,
        String stateOrProvince,
        @NotBlank @Size(max = 24)
        String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}")
        String countryCode,
        @NotBlank @Size(max = 32)
        String phoneNumber
) {
}
