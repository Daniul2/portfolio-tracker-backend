package com.kodilla.portfolio.dto;

import com.kodilla.portfolio.domain.AlertType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AlertDtos {

    private AlertDtos() {
    }

    /** {@code assetId} is required for PRICE_* types and ignored for portfolio-level ones. */
    public record AlertRequest(
            @NotNull(message = "portfolioId is required")
            Long portfolioId,

            Long assetId,

            @NotNull(message = "type is required")
            AlertType type,

            @NotNull(message = "threshold is required")
            @DecimalMin(value = "0.0", inclusive = false, message = "threshold must be greater than zero")
            @Digits(integer = 12, fraction = 8)
            BigDecimal threshold,

            Boolean active) {
    }

    public record AlertResponse(
            Long id,
            Long portfolioId,
            Long assetId,
            String symbol,
            AlertType type,
            BigDecimal threshold,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime lastTriggeredAt) {
    }

    public record AlertEventResponse(
            Long id,
            Long alertId,
            Long portfolioId,
            String message,
            BigDecimal valueAtTrigger,
            boolean acknowledged,
            LocalDateTime createdAt) {
    }
}
