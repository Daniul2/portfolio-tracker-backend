package com.kodilla.portfolio.service.valuation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** A whole portfolio priced up, in USD and in the user's reporting currency. */
public record PortfolioValuation(
        Long portfolioId,
        String portfolioName,
        String baseCurrency,
        BigDecimal totalCostUsd,
        BigDecimal totalValueUsd,
        BigDecimal totalPnlUsd,
        BigDecimal totalPnlPercent,
        BigDecimal fxRate,
        BigDecimal totalValueBase,
        boolean fullyPriced,
        List<HoldingValuation> holdings,
        LocalDateTime valuedAt) {
}
