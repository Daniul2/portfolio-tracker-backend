package com.kodilla.portfolio.dto;

import com.kodilla.portfolio.dto.AlertDtos.AlertEventResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioSummaryResponse;
import com.kodilla.portfolio.dto.UserDtos.UserResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** Everything one screen of the frontend needs, in a single response. */
    public record DashboardResponse(
            UserResponse user,
            List<PortfolioSummaryResponse> portfolios,
            BigDecimal combinedValueUsd,
            List<AlertEventResponse> unacknowledgedAlerts,
            LocalDateTime generatedAt) {
    }

    /** Outcome of a full market-data refresh. */
    public record MarketRefreshResponse(
            int pricesSaved,
            int ratesSaved,
            int alertsTriggered,
            List<String> failures,
            LocalDateTime completedAt) {

        public boolean isFullySuccessful() {
            return failures.isEmpty();
        }
    }
}
