package com.finsight_backend.dto;

import com.finsight_backend.enums.TransactionType;

public record CategorySummary(Long id, String name, TransactionType type) {}
