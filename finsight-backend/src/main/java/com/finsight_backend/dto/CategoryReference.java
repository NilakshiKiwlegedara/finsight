package com.finsight_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CategoryReference(@NotNull @Positive Long id) {}
