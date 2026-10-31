package com.bank.digitalcore.carddispatch.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Immutable request representation of the physical shipment dimensions. */
public record ShipmentDetailsRequest(
    @NotNull @DecimalMin("0.01") @DecimalMax("50.0") BigDecimal weightKg,
    @NotNull @DecimalMin("1.0") @DecimalMax("150.0") BigDecimal lengthCm,
    @NotNull @DecimalMin("1.0") @DecimalMax("150.0") BigDecimal widthCm,
    @NotNull @DecimalMin("1.0") @DecimalMax("150.0") BigDecimal heightCm
) { }
