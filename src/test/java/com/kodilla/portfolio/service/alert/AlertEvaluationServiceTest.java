package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.repository.AlertRepository;
import com.kodilla.portfolio.service.PriceService;
import com.kodilla.portfolio.service.valuation.PortfolioValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private AlertPublisher alertPublisher;
    @Mock
    private PriceService priceService;
    @Mock
    private PortfolioValuationService valuationService;

    private AlertStrategyFactory strategyFactory;
    private AlertEvaluationService service;

    private final User user = TestFixtures.user(1L, "tester");
    private final Portfolio portfolio = TestFixtures.portfolio(10L, user, "Main", "PLN");
    private final Asset bitcoin = TestFixtures.asset(100L, "bitcoin", "BTC");

    @BeforeEach
    void setUp() {
        strategyFactory = new AlertStrategyFactory(List.of(
                new PriceAboveAlertStrategy(),
                new PriceBelowAlertStrategy(),
                new PortfolioValueAboveAlertStrategy(),
                new PortfolioValueBelowAlertStrategy()));
        service = new AlertEvaluationService(alertRepository, strategyFactory, alertPublisher,
                priceService, valuationService, 60);
    }

    @Test
    @DisplayName("does nothing and touches no collaborators when there are no active alerts")
    void noActiveAlerts() {
        when(alertRepository.findByActiveTrue()).thenReturn(List.of());

        assertThat(service.evaluateAll()).isZero();

        verifyNoInteractions(priceService, alertPublisher);
    }

    @Test
    @DisplayName("publishes a trigger for an alert whose condition is met")
    void publishesTriggeredAlert() {
        Alert alert = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "50000");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "60000")));

        assertThat(service.evaluateAll()).isEqualTo(1);

        verify(alertPublisher).publish(any(AlertTrigger.class));
    }

    @Test
    @DisplayName("publishes nothing when the condition is not met")
    void skipsUntriggeredAlert() {
        Alert alert = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "70000");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "60000")));

        assertThat(service.evaluateAll()).isZero();

        verify(alertPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("an alert inside its cooldown is skipped without being evaluated")
    void respectsCooldown() {
        Alert alert = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "50000");
        alert.setLastTriggeredAt(LocalDateTime.now().minusMinutes(10));
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));

        assertThat(service.evaluateAll()).isZero();

        verify(alertPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("an alert whose cooldown has expired fires again")
    void firesAfterCooldownExpires() {
        Alert alert = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "50000");
        alert.setLastTriggeredAt(LocalDateTime.now().minusMinutes(90));
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "60000")));

        assertThat(service.evaluateAll()).isEqualTo(1);

        verify(alertPublisher).publish(any(AlertTrigger.class));
    }

    @Test
    @DisplayName("an alert type with no registered strategy is skipped, not fatal")
    void skipsUnsupportedType() {
        AlertStrategyFactory sparse = new AlertStrategyFactory(List.of(new PriceBelowAlertStrategy()));
        AlertEvaluationService sparseService = new AlertEvaluationService(alertRepository, sparse,
                alertPublisher, priceService, valuationService, 60);

        Alert alert = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "50000");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(priceService.findLatestPrices()).thenReturn(Map.of());

        assertThat(sparseService.evaluateAll()).isZero();

        verify(alertPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("portfolio value is computed once even when several alerts watch it")
    void memoizesPortfolioValuation() {
        Alert first = TestFixtures.alert(1L, portfolio, null, AlertType.PORTFOLIO_VALUE_ABOVE, "100");
        Alert second = TestFixtures.alert(2L, portfolio, null, AlertType.PORTFOLIO_VALUE_ABOVE, "200");
        Alert third = TestFixtures.alert(3L, portfolio, null, AlertType.PORTFOLIO_VALUE_BELOW, "5000");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(first, second, third));
        when(priceService.findLatestPrices()).thenReturn(Map.of());
        when(valuationService.totalValueUsd(portfolio)).thenReturn(new BigDecimal("1000"));

        assertThat(service.evaluateAll()).isEqualTo(3);

        // Three alerts on one portfolio must not mean three valuations.
        verify(valuationService, times(1)).totalValueUsd(portfolio);
    }

    @Test
    @DisplayName("prices are read once per run, not once per alert")
    void readsPricesOncePerRun() {
        Alert first = TestFixtures.alert(1L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "1");
        Alert second = TestFixtures.alert(2L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "2");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(first, second));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "60000")));

        service.evaluateAll();

        verify(priceService, times(1)).findLatestPrices();
    }

    @Test
    @DisplayName("a portfolio-value alert does not fire when the value cannot be computed")
    void handlesUnvaluablePortfolio() {
        Alert alert = TestFixtures.alert(1L, portfolio, null, AlertType.PORTFOLIO_VALUE_ABOVE, "100");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(priceService.findLatestPrices()).thenReturn(Map.of());
        when(valuationService.totalValueUsd(portfolio)).thenReturn(null);

        assertThat(service.evaluateAll()).isZero();
    }
}
