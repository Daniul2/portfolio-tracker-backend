package com.kodilla.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PortfolioDtos {

    private PortfolioDtos() {
    }

    public record PortfolioRequest(
            @NotNull(message = "userId is required")
            Long userId,

            @NotBlank(message = "name is required")
            @Size(max = 120)
            String name,

            @NotBlank(message = "baseCurrency is required")
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "baseCurrency must be a 3-letter ISO code")
            String baseCurrency) {
    }

    public record PortfolioResponse(
            Long id,
            Long userId,
            String name,
            String baseCurrency,
            LocalDateTime createdAt,
            int transactionCount) {
    }

    /** One priced position inside a portfolio. */
    public record HoldingResponse(
            Long assetId,
            String symbol,
            String assetName,
            BigDecimal quantity,
            BigDecimal averageCostUsd,
            BigDecimal costBasisUsd,
            BigDecimal currentPriceUsd,
            BigDecimal marketValueUsd,
            BigDecimal unrealizedPnlUsd,
            BigDecimal unrealizedPnlPercent,
            BigDecimal marketValueBase,
            boolean priced) {
    }

    /**
     * Full portfolio valuation. {@code fxRate} and the base-currency totals are
     * null until exchange rates have been fetched at least once.
     */
    public record PortfolioSummaryResponse(
            Long portfolioId,
            String portfolioName,
            String baseCurrency,
            BigDecimal totalCostUsd,
            BigDecimal totalValueUsd,
            BigDecimal totalPnlUsd,
            BigDecimal totalPnlPercent,
            BigDecimal fxRate,
            BigDecimal totalValueBase,
            List<HoldingResponse> holdings,
            LocalDateTime valuedAt) {
    }
}
