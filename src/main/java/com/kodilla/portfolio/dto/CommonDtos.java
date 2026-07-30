package com.kodilla.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

        public static ErrorResponse of(int status, String error, String message) {
            return new ErrorResponse(status, error, message, Map.of(), LocalDateTime.now());
        }
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

    public record ListResponse<T>(List<T> items, int count) {

        public static <T> ListResponse<T> of(List<T> items) {
            return new ListResponse<>(items, items.size());
        }
    }
}
