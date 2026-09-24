package com.finsight_backend.dto;

import com.finsight_backend.enums.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type,
        @NotNull LocalDate date,
        @Size(max = 255) String description,
        @NotNull @Valid CategoryReference category) {}
