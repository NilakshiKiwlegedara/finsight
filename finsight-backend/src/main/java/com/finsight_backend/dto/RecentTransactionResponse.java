package com.finsight_backend.dto;

import com.finsight_backend.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecentTransactionResponse(Long id, BigDecimal amount, TransactionType type,
                                        LocalDate date, String description, CategorySummary category) {}
