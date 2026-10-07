package com.kodilla.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public final class CommonDtos {

    private CommonDtos() {
    }

    public record ExchangeRateResponse(
            Long id,
            String currencyCode,
            BigDecimal ratePln,
            LocalDate effectiveDate,
            LocalDateTime fetchedAt) {
    }

    public record AuditLogResponse(
            Long id,
            String action,
            String entityType,
            Long entityId,
            String details,
            LocalDateTime createdAt) {
    }

    /** Uniform error body for every failure, so clients can parse one shape. */
    public record ErrorResponse(
            int status,
            String error,
            String message,
            Map<String, String> fieldErrors,
            LocalDateTime timestamp) {
    }

    /** Returned by the manual refresh endpoints. */
    public record RefreshResultResponse(
            String provider,
            int recordsSaved,
            String message,
            LocalDateTime completedAt) {
    }

    public record CountResponse(String label, long count) {
    }
}
