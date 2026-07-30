package com.kodilla.portfolio.facade;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.domain.User;
import com.kodilla.portfolio.dto.AlertDtos.AlertEventResponse;
import com.kodilla.portfolio.dto.DashboardDtos.DashboardResponse;
import com.kodilla.portfolio.dto.DashboardDtos.MarketRefreshResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionRequest;
import com.kodilla.portfolio.dto.UserDtos.UserResponse;
import com.kodilla.portfolio.domain.TransactionType;
import com.kodilla.portfolio.external.ExternalApiException;
import com.kodilla.portfolio.service.*;
import com.kodilla.portfolio.service.alert.AlertEvaluationService;
import com.kodilla.portfolio.service.valuation.PortfolioValuation;
import com.kodilla.portfolio.service.valuation.PortfolioValuationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioFacadeTest {

    @Mock
    private UserService userService;
    @Mock
    private PortfolioService portfolioService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private AlertService alertService;
    @Mock
    private PriceService priceService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private PortfolioValuationService valuationService;
    @Mock
    private AlertEvaluationService alertEvaluationService;
    @InjectMocks
    private PortfolioFacade facade;

    private final User user = TestFixtures.user(1L, "demo");
    private final Portfolio portfolio = TestFixtures.portfolio(10L, user, "Main", "PLN");


    private final com.kodilla.portfolio.domain.Asset asset =
            TestFixtures.asset(100L, "bitcoin", "BTC");

    /** Only the size of these lists matters to the facade. */
    private List<com.kodilla.portfolio.domain.PriceSnapshot> snapshots(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> TestFixtures.snapshot((long) i, asset, "50000"))
                .toList();
    }

    private List<com.kodilla.portfolio.domain.ExchangeRate> rates(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> TestFixtures.rate((long) i, "C" + i, "3.79"))
                .toList();
    }

    private PortfolioValuation valuation(String totalUsd) {
        return new PortfolioValuation(10L, "Main", "PLN",
                new BigDecimal("100"), new BigDecimal(totalUsd), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("3.7962"), new BigDecimal("1000"),
                List.of(), LocalDateTime.now());
    }

    @Test
    @DisplayName("summarise prices the requested portfolio")
    void summarises() {
        when(portfolioService.requirePortfolio(10L)).thenReturn(portfolio);
        when(valuationService.value(portfolio)).thenReturn(valuation("500"));

        assertThat(facade.summarise(10L).totalValueUsd()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("the dashboard sums the value of every portfolio the user owns")
    void sumsPortfolioValues() {
        UserResponse userResponse = new UserResponse(
                1L, "demo", "demo@example.com", "Demo", LocalDateTime.now(), 2);
        when(userService.findById(1L)).thenReturn(userResponse);
        when(portfolioService.findByUser(1L)).thenReturn(List.of(
                new PortfolioResponse(10L, 1L, "Main", "PLN", LocalDateTime.now(), 3),
                new PortfolioResponse(11L, 1L, "Second", "USD", LocalDateTime.now(), 1)));
        when(portfolioService.requirePortfolio(anyLong())).thenReturn(portfolio);
        when(valuationService.value(portfolio))
                .thenReturn(valuation("500"), valuation("300"));
        when(alertService.findUnacknowledgedEvents()).thenReturn(List.of());

        DashboardResponse dashboard = facade.dashboardFor(1L);

        assertThat(dashboard.portfolios()).hasSize(2);
        assertThat(dashboard.combinedValueUsd()).isEqualByComparingTo("800");
    }

    @Test
    @DisplayName("the dashboard only shows alerts belonging to that user's portfolios")
    void filtersAlertsToOwnPortfolios() {
        when(userService.findById(1L)).thenReturn(new UserResponse(
                1L, "demo", "demo@example.com", "Demo", LocalDateTime.now(), 1));
        when(portfolioService.findByUser(1L)).thenReturn(List.of(
                new PortfolioResponse(10L, 1L, "Main", "PLN", LocalDateTime.now(), 3)));
        when(portfolioService.requirePortfolio(10L)).thenReturn(portfolio);
        when(valuationService.value(portfolio)).thenReturn(valuation("500"));
        when(alertService.findUnacknowledgedEvents()).thenReturn(List.of(
                new AlertEventResponse(1L, 1L, 10L, "mine", BigDecimal.ONE, false, LocalDateTime.now()),
                new AlertEventResponse(2L, 2L, 99L, "someone else's", BigDecimal.ONE, false,
                        LocalDateTime.now())));

        DashboardResponse dashboard = facade.dashboardFor(1L);

        assertThat(dashboard.unacknowledgedAlerts()).hasSize(1);
        assertThat(dashboard.unacknowledgedAlerts().get(0).message()).isEqualTo("mine");
    }

    @Test
    @DisplayName("a portfolio that cannot be valued does not break the combined total")
    void toleratesUnvaluablePortfolio() {
        when(userService.findById(1L)).thenReturn(new UserResponse(
                1L, "demo", "demo@example.com", "Demo", LocalDateTime.now(), 1));
        when(portfolioService.findByUser(1L)).thenReturn(List.of(
                new PortfolioResponse(10L, 1L, "Main", "PLN", LocalDateTime.now(), 0)));
        when(portfolioService.requirePortfolio(10L)).thenReturn(portfolio);
        when(valuationService.value(portfolio)).thenReturn(new PortfolioValuation(
                10L, "Main", "PLN", BigDecimal.ZERO, null, null, null, null, null,
                List.of(), LocalDateTime.now()));
        when(alertService.findUnacknowledgedEvents()).thenReturn(List.of());

        assertThat(facade.dashboardFor(1L).combinedValueUsd()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("recording a trade returns the freshly repriced portfolio")
    void recordsAndRevalues() {
        TransactionRequest request = new TransactionRequest(10L, 100L, TransactionType.BUY,
                BigDecimal.ONE, new BigDecimal("50000"), null, LocalDateTime.now().minusDays(1), null);
        when(portfolioService.requirePortfolio(10L)).thenReturn(portfolio);
        when(valuationService.value(portfolio)).thenReturn(valuation("50000"));

        assertThat(facade.recordTransactionAndRevalue(request).totalValueUsd())
                .isEqualByComparingTo("50000");

        verify(transactionService).create(request);
    }

    @Test
    @DisplayName("a successful refresh reports both sources and the alerts fired")
    void refreshesEverything() {
        when(priceService.refreshPrices()).thenReturn(snapshots(4));
        when(exchangeRateService.refreshRates()).thenReturn(rates(2));
        when(alertEvaluationService.evaluateAll()).thenReturn(2);

        MarketRefreshResponse result = facade.refreshMarketData();

        assertThat(result.pricesSaved()).isEqualTo(4);
        assertThat(result.ratesSaved()).isEqualTo(2);
        assertThat(result.alertsTriggered()).isEqualTo(2);
        assertThat(result.isFullySuccessful()).isTrue();
    }

    @Test
    @DisplayName("a price outage still lets exchange rates refresh")
    void priceFailureDoesNotBlockRates() {
        when(priceService.refreshPrices())
                .thenThrow(new ExternalApiException("CoinGecko", "timed out"));
        when(exchangeRateService.refreshRates()).thenReturn(rates(2));

        MarketRefreshResponse result = facade.refreshMarketData();

        assertThat(result.pricesSaved()).isZero();
        assertThat(result.ratesSaved()).isEqualTo(2);
        assertThat(result.isFullySuccessful()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0)).contains("CoinGecko");
    }

    @Test
    @DisplayName("a rate outage still lets prices refresh")
    void rateFailureDoesNotBlockPrices() {
        when(priceService.refreshPrices()).thenReturn(snapshots(1));
        when(exchangeRateService.refreshRates())
                .thenThrow(new ExternalApiException("NBP", "unreachable"));
        when(alertEvaluationService.evaluateAll()).thenReturn(0);

        MarketRefreshResponse result = facade.refreshMarketData();

        assertThat(result.pricesSaved()).isEqualTo(1);
        assertThat(result.ratesSaved()).isZero();
        assertThat(result.failures().get(0)).contains("NBP");
    }

    @Test
    @DisplayName("both sources failing is reported without throwing")
    void bothFailuresAreCollected() {
        when(priceService.refreshPrices())
                .thenThrow(new ExternalApiException("CoinGecko", "timed out"));
        when(exchangeRateService.refreshRates())
                .thenThrow(new ExternalApiException("NBP", "unreachable"));

        MarketRefreshResponse result = facade.refreshMarketData();

        assertThat(result.failures()).hasSize(2);
        verify(alertEvaluationService, never()).evaluateAll();
    }

    @Test
    @DisplayName("alerts are not evaluated when no price was refreshed")
    void skipsAlertsWithoutFreshPrices() {
        when(priceService.refreshPrices()).thenReturn(List.of());
        when(exchangeRateService.refreshRates()).thenReturn(rates(1));

        MarketRefreshResponse result = facade.refreshMarketData();

        assertThat(result.alertsTriggered()).isZero();
        // Re-running rules against stale prices would only produce noise.
        verify(alertEvaluationService, never()).evaluateAll();
    }
}
