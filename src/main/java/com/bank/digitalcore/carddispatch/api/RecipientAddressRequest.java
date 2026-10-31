package com.bank.digitalcore.carddispatch.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Immutable request representation of the trusted shipment recipient address. */
public record RecipientAddressRequest(
    @NotBlank @Size(max = 128) String recipientName,
    @NotBlank @Size(max = 200) String addressLine1,
    @Size(max = 200) String addressLine2,
    @NotBlank @Size(max = 100) String city,
    @Size(max = 100) String stateOrProvince,
    @NotBlank @Size(max = 24) String postalCode,
    @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
    @NotBlank @Size(max = 32) String phoneNumber
) { }
