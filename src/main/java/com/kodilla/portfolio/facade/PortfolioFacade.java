package com.kodilla.portfolio.facade;

import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.dto.AlertDtos.AlertEventResponse;
import com.kodilla.portfolio.dto.DashboardDtos.DashboardResponse;
import com.kodilla.portfolio.dto.DashboardDtos.MarketRefreshResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioSummaryResponse;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionRequest;
import com.kodilla.portfolio.dto.UserDtos.UserResponse;
import com.kodilla.portfolio.external.ExternalApiException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.service.*;
import com.kodilla.portfolio.service.alert.AlertEvaluationService;
import com.kodilla.portfolio.service.valuation.PortfolioValuationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Facade pattern: a small, task-shaped API over six collaborating services. */
@Component
public class PortfolioFacade {

    private static final Logger log = LoggerFactory.getLogger(PortfolioFacade.class);

    private final UserService userService;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final AlertService alertService;
    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;
    private final PortfolioValuationService valuationService;
    private final AlertEvaluationService alertEvaluationService;

    public PortfolioFacade(UserService userService,
                           PortfolioService portfolioService,
                           TransactionService transactionService,
                           AlertService alertService,
                           PriceService priceService,
                           ExchangeRateService exchangeRateService,
                           PortfolioValuationService valuationService,
                           AlertEvaluationService alertEvaluationService) {
        this.userService = userService;
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.alertService = alertService;
        this.priceService = priceService;
        this.exchangeRateService = exchangeRateService;
        this.valuationService = valuationService;
        this.alertEvaluationService = alertEvaluationService;
    }

    /** Prices one portfolio, holdings and all. */
    @Transactional(readOnly = true)
    public PortfolioSummaryResponse summarise(Long portfolioId) {
        Portfolio portfolio = portfolioService.requirePortfolio(portfolioId);
        return DtoMapper.toPortfolioSummaryResponse(valuationService.value(portfolio));
    }

    /** Everything the frontend's main screen needs, assembled in one pass. */
    @Transactional(readOnly = true)
    public DashboardResponse dashboardFor(Long userId) {
        UserResponse user = userService.findById(userId);
        List<PortfolioResponse> portfolios = portfolioService.findByUser(userId);

        List<PortfolioSummaryResponse> summaries = new ArrayList<>();
        BigDecimal combined = BigDecimal.ZERO;

        for (PortfolioResponse portfolio : portfolios) {
            PortfolioSummaryResponse summary = summarise(portfolio.id());
            summaries.add(summary);
            if (summary.totalValueUsd() != null) {
                combined = combined.add(summary.totalValueUsd());
            }
        }

        List<AlertEventResponse> unacknowledged = alertService.findUnacknowledgedEvents().stream()
                .filter(event -> portfolios.stream()
                        .anyMatch(portfolio -> portfolio.id().equals(event.portfolioId())))
                .toList();

        return new DashboardResponse(user, summaries, combined, unacknowledged, LocalDateTime.now());
    }

    /** Records a trade and hands back the portfolio's updated valuation. */
    @Transactional
    public PortfolioSummaryResponse recordTransactionAndRevalue(TransactionRequest request) {
        transactionService.create(request);
        return summarise(request.portfolioId());
    }

    /** Refreshes both external sources and re-runs the alert rules. */
    public MarketRefreshResponse refreshMarketData() {
        List<String> failures = new ArrayList<>();
        int pricesSaved = 0;
        int ratesSaved = 0;
        int alertsTriggered = 0;

        try {
            pricesSaved = priceService.refreshPrices();
        } catch (ExternalApiException e) {
            log.warn("Price refresh failed: {}", e.getMessage());
            failures.add(e.getProvider() + ": " + e.getMessage());
        }

        try {
            ratesSaved = exchangeRateService.refreshRates();
        } catch (ExternalApiException e) {
            log.warn("Exchange rate refresh failed: {}", e.getMessage());
            failures.add(e.getProvider() + ": " + e.getMessage());
        }

        // Only worth evaluating alerts if at least one price landed.
        if (pricesSaved > 0) {
            alertsTriggered = alertEvaluationService.evaluateAll();
        }

        return new MarketRefreshResponse(
                pricesSaved, ratesSaved, alertsTriggered, failures, LocalDateTime.now());
    }
}
