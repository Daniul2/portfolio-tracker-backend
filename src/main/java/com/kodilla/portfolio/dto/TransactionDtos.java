package com.kodilla.portfolio.dto;

import com.kodilla.portfolio.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record TransactionRequest(
            @NotNull(message = "portfolioId is required")
            Long portfolioId,

            @NotNull(message = "assetId is required")
            Long assetId,

            @NotNull(message = "type is required (BUY or SELL)")
            TransactionType type,

            @NotNull(message = "quantity is required")
            @DecimalMin(value = "0.00000001", message = "quantity must be greater than zero")
            @Digits(integer = 16, fraction = 8)
            BigDecimal quantity,

            @NotNull(message = "pricePerUnitUsd is required")
            @DecimalMin(value = "0.0", message = "pricePerUnitUsd cannot be negative")
            @Digits(integer = 12, fraction = 8)
            BigDecimal pricePerUnitUsd,

            @PositiveOrZero(message = "feeUsd cannot be negative")
            @Digits(integer = 12, fraction = 8)
            BigDecimal feeUsd,

            LocalDateTime executedAt,

            @Size(max = 255)
            String note) {
    }

    public record TransactionResponse(
            Long id,
            Long portfolioId,
            Long assetId,
            String symbol,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal pricePerUnitUsd,
            BigDecimal feeUsd,
            BigDecimal grossValueUsd,
            LocalDateTime executedAt,
            String note) {
    }
}
