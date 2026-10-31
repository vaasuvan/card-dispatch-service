package com.bank.digitalcore.carddispatch.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Minimal physical shipment dimensions needed by standard carrier booking APIs.
 */
public record ShipmentDetails(
        @NotNull @DecimalMin("0.01")
        @DecimalMax("50.0")
        BigDecimal weightKg,
        @NotNull @DecimalMin("1.0") @DecimalMax("150.0") BigDecimal lengthCm,
        @NotNull @DecimalMin("1.0") @DecimalMax("150.0") BigDecimal widthCm,
        @NotNull @DecimalMin("1.0") @DecimalMax("150.0") BigDecimal heightCm
) {
}
