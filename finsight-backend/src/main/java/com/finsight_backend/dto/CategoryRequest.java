package com.finsight_backend.dto;

import com.finsight_backend.enums.TransactionType;
import jakarta.validation.constraints.*;

public record CategoryRequest(@NotBlank @Size(max = 255) String name,
                              @NotNull TransactionType type) {}
