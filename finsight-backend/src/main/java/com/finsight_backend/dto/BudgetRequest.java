package com.finsight_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BudgetRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull @Min(1) @Max(12) Integer month,
        @NotNull @Min(1900) @Max(2100) Integer year,
        @NotNull @Valid CategoryReference category) {}
