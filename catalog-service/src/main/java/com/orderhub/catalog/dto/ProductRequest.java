package com.orderhub.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotNull @Positive @DecimalMax("999999.99") BigDecimal price,
        @Size(max = 60) String category,
        @NotNull @PositiveOrZero @Max(1_000_000) Integer stock
) {}
