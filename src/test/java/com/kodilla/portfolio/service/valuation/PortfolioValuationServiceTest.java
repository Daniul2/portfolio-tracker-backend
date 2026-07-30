package com.kodilla.portfolio.service.valuation;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.repository.TransactionRepository;
import com.kodilla.portfolio.service.ExchangeRateService;
import com.kodilla.portfolio.service.PriceService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The heart of the application: USD prices from one provider combined with an
 * exchange rate from another.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioValuationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PriceService priceService;
    @Mock
    private ExchangeRateService exchangeRateService;

    private PortfolioValuationService service;

    private final User user = TestFixtures.user(1L, "demo");
    private final Portfolio portfolio = TestFixtures.portfolio(10L, user, "Main", "PLN");
    private final Asset bitcoin = TestFixtures.asset(100L, "bitcoin", "BTC");
    private final Asset ethereum = TestFixtures.asset(200L, "ethereum", "ETH");
    private final LocalDateTime past = LocalDateTime.now().minusDays(10);

    @BeforeEach
    void setUp() {
        service = new PortfolioValuationService(transactionRepository, new HoldingCalculator(),
                priceService, exchangeRateService);
    }

    @Test
    @DisplayName("prices a holding in USD and converts the total into the base currency")
    void valuesInUsdAndBaseCurrency() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(
                List.of(TestFixtures.buy(1L, portfolio, bitcoin, "0.5", "40000", past)));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "60000")));
        when(exchangeRateService.usdToCurrencyRate("PLN"))
                .thenReturn(Optional.of(new BigDecimal("4.00")));

        PortfolioValuation valuation = service.value(portfolio);

        assertThat(valuation.totalCostUsd()).isEqualByComparingTo("20000.00");
        assertThat(valuation.totalValueUsd()).isEqualByComparingTo("30000.00");
        assertThat(valuation.totalPnlUsd()).isEqualByComparingTo("10000.00");
        assertThat(valuation.totalPnlPercent()).isEqualByComparingTo("50.00");
        assertThat(valuation.fxRate()).isEqualByComparingTo("4.00");
        // 30000 USD x 4.00 PLN/USD
        assertThat(valuation.totalValueBase()).isEqualByComparingTo("120000.00");
    }

    @Test
    @DisplayName("reports a loss with a negative profit figure")
    void reportsLoss() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(
                List.of(TestFixtures.buy(1L, portfolio, bitcoin, "1", "60000", past)));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "45000")));
        when(exchangeRateService.usdToCurrencyRate("PLN")).thenReturn(Optional.empty());

        PortfolioValuation valuation = service.value(portfolio);

        assertThat(valuation.totalPnlUsd()).isEqualByComparingTo("-15000.00");
        assertThat(valuation.totalPnlPercent()).isEqualByComparingTo("-25.00");
    }

    @Test
    @DisplayName("leaves base-currency figures null rather than wrong when no rate is available")
    void omitsBaseCurrencyWithoutRate() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(
                List.of(TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", past)));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "50000")));
        when(exchangeRateService.usdToCurrencyRate("PLN")).thenReturn(Optional.empty());

        PortfolioValuation valuation = service.value(portfolio);

        assertThat(valuation.totalValueUsd()).isEqualByComparingTo("50000.00");
        assertThat(valuation.fxRate()).isNull();
        assertThat(valuation.totalValueBase()).isNull();
        assertThat(valuation.holdings().get(0).marketValueBase()).isNull();
    }

    @Test
    @DisplayName("an unpriced asset is listed but contributes no market value")
    void unpricedAssetHasNoMarketValue() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(List.of(
                TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", past),
                TestFixtures.buy(2L, portfolio, ethereum, "10", "2000", past)));
        // Only bitcoin has a price.
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "50000")));
        when(exchangeRateService.usdToCurrencyRate("PLN")).thenReturn(Optional.empty());

        PortfolioValuation valuation = service.value(portfolio);

        assertThat(valuation.holdings()).hasSize(2);
        HoldingValuation eth = valuation.holdings().stream()
                .filter(h -> h.holding().asset().getSymbol().equals("ETH"))
                .findFirst().orElseThrow();
        assertThat(eth.isPriced()).isFalse();
        assertThat(eth.marketValueUsd()).isNull();
        assertThat(eth.unrealizedPnlUsd()).isNull();

        // Its cost still counts, but its value does not.
        assertThat(valuation.totalCostUsd()).isEqualByComparingTo("60000.00");
        assertThat(valuation.totalValueUsd()).isEqualByComparingTo("50000.00");
    }

    @Test
    @DisplayName("an empty portfolio values to zero with an undefined percentage")
    void valuesEmptyPortfolio() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(List.of());
        when(priceService.findLatestPrices()).thenReturn(Map.of());
        when(exchangeRateService.usdToCurrencyRate("PLN"))
                .thenReturn(Optional.of(new BigDecimal("4.00")));

        PortfolioValuation valuation = service.value(portfolio);

        assertThat(valuation.holdings()).isEmpty();
        assertThat(valuation.totalCostUsd()).isEqualByComparingTo("0.00");
        assertThat(valuation.totalValueUsd()).isEqualByComparingTo("0.00");
        // Percent change on a zero cost basis is undefined, not zero.
        assertThat(valuation.totalPnlPercent()).isNull();
    }

    @Test
    @DisplayName("per-holding profit and percentage are computed from that holding's own cost")
    void computesPerHoldingFigures() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(List.of(
                TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", past),
                TestFixtures.buy(2L, portfolio, ethereum, "10", "2000", past)));
        when(priceService.findLatestPrices()).thenReturn(Map.of(
                100L, TestFixtures.snapshot(1L, bitcoin, "50000"),
                200L, TestFixtures.snapshot(2L, ethereum, "1500")));
        when(exchangeRateService.usdToCurrencyRate("PLN"))
                .thenReturn(Optional.of(new BigDecimal("4.00")));

        PortfolioValuation valuation = service.value(portfolio);

        HoldingValuation btc = valuation.holdings().stream()
                .filter(h -> h.holding().asset().getSymbol().equals("BTC"))
                .findFirst().orElseThrow();
        assertThat(btc.unrealizedPnlUsd()).isEqualByComparingTo("10000.00");
        assertThat(btc.unrealizedPnlPercent()).isEqualByComparingTo("25.00");
        assertThat(btc.marketValueBase()).isEqualByComparingTo("200000.00");

        HoldingValuation eth = valuation.holdings().stream()
                .filter(h -> h.holding().asset().getSymbol().equals("ETH"))
                .findFirst().orElseThrow();
        assertThat(eth.unrealizedPnlUsd()).isEqualByComparingTo("-5000.00");
        assertThat(eth.unrealizedPnlPercent()).isEqualByComparingTo("-25.00");
    }

    @Test
    @DisplayName("a sold-out position does not appear in the valuation")
    void excludesSoldOutPositions() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(List.of(
                TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", past),
                TestFixtures.sell(2L, portfolio, bitcoin, "1", "50000", past.plusDays(1))));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "50000")));
        when(exchangeRateService.usdToCurrencyRate("PLN")).thenReturn(Optional.empty());

        PortfolioValuation valuation = service.value(portfolio);

        assertThat(valuation.holdings()).isEmpty();
        assertThat(valuation.totalValueUsd()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a partial sell leaves a clean cost basis with no rounding noise")
    void partialSellLeavesCleanCostBasis() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(List.of(
                TestFixtures.buy(1L, portfolio, ethereum, "3.5", "2100", past),
                TestFixtures.sell(2L, portfolio, ethereum, "1", "2600", past.plusDays(1))));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(200L, TestFixtures.snapshot(1L, ethereum, "2000")));
        when(exchangeRateService.usdToCurrencyRate("PLN")).thenReturn(Optional.empty());

        PortfolioValuation valuation = service.value(portfolio);

        // 7350 x (2.5/3.5) = exactly 5250, not 5250.0000000021
        assertThat(valuation.holdings().get(0).holding().costBasisUsd())
                .isEqualByComparingTo("5250");
        assertThat(valuation.totalCostUsd()).isEqualByComparingTo("5250.00");
    }

    @Test
    @DisplayName("holdingsOf returns the derived positions")
    void exposesHoldings() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(
                List.of(TestFixtures.buy(1L, portfolio, bitcoin, "2", "40000", past)));

        List<Holding> holdings = service.holdingsOf(portfolio);

        assertThat(holdings).hasSize(1);
        assertThat(holdings.get(0).quantity()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("totalValueUsd is the same figure the full valuation reports")
    void totalValueMatchesValuation() {
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(
                List.of(TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", past)));
        when(priceService.findLatestPrices())
                .thenReturn(Map.of(100L, TestFixtures.snapshot(1L, bitcoin, "50000")));
        when(exchangeRateService.usdToCurrencyRate("PLN")).thenReturn(Optional.empty());

        assertThat(service.totalValueUsd(portfolio)).isEqualByComparingTo("50000.00");
    }
}
