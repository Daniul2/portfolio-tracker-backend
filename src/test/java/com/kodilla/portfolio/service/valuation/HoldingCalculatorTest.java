package com.kodilla.portfolio.service.valuation;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.domain.Transaction;
import com.kodilla.portfolio.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingCalculatorTest {

    private final HoldingCalculator calculator = new HoldingCalculator();

    private final User user = TestFixtures.user(1L, "tester");
    private final Portfolio portfolio = TestFixtures.portfolio(10L, user, "Main", "PLN");
    private final Asset bitcoin = TestFixtures.asset(100L, "bitcoin", "BTC");
    private final Asset ethereum = TestFixtures.asset(200L, "ethereum", "ETH");
    private final LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    @DisplayName("returns nothing for a null or empty ledger")
    void handlesEmptyInput() {
        assertThat(calculator.calculate(null)).isEmpty();
        assertThat(calculator.calculate(List.of())).isEmpty();
    }

    @Test
    @DisplayName("a single buy becomes one holding with cost basis equal to what was paid")
    void singleBuy() {
        List<Transaction> ledger = List.of(
                TestFixtures.buy(1L, portfolio, bitcoin, "0.5", "40000", base));

        List<Holding> holdings = calculator.calculate(ledger);

        assertThat(holdings).hasSize(1);
        Holding holding = holdings.get(0);
        assertThat(holding.asset()).isEqualTo(bitcoin);
        assertThat(holding.quantity()).isEqualByComparingTo("0.5");
        assertThat(holding.costBasisUsd()).isEqualByComparingTo("20000");
        assertThat(holding.averageCostUsd()).isEqualByComparingTo("40000");
    }

    @Test
    @DisplayName("two buys at different prices average out")
    void averagesMultipleBuys() {
        List<Transaction> ledger = List.of(
                TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", base),
                TestFixtures.buy(2L, portfolio, bitcoin, "1", "60000", base.plusDays(1)));

        Holding holding = calculator.calculate(ledger).get(0);

        assertThat(holding.quantity()).isEqualByComparingTo("2");
        assertThat(holding.costBasisUsd()).isEqualByComparingTo("100000");
        assertThat(holding.averageCostUsd()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("buy fees are added to the cost basis")
    void includesFeesInCostBasis() {
        Transaction withFee = TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", base);
        withFee.setFeeUsd(new BigDecimal("150"));

        Holding holding = calculator.calculate(List.of(withFee)).get(0);

        assertThat(holding.costBasisUsd()).isEqualByComparingTo("40150");
    }

    @Test
    @DisplayName("a partial sell reduces quantity but leaves the average cost unchanged")
    void partialSellKeepsAverageCost() {
        List<Transaction> ledger = List.of(
                TestFixtures.buy(1L, portfolio, bitcoin, "2", "50000", base),
                TestFixtures.sell(2L, portfolio, bitcoin, "1", "70000", base.plusDays(5)));

        Holding holding = calculator.calculate(ledger).get(0);

        assertThat(holding.quantity()).isEqualByComparingTo("1");
        assertThat(holding.costBasisUsd()).isEqualByComparingTo("50000");
        // The gain on the sold half must not distort what the remainder cost.
        assertThat(holding.averageCostUsd()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("selling everything removes the position entirely")
    void fullSellDropsHolding() {
        List<Transaction> ledger = List.of(
                TestFixtures.buy(1L, portfolio, bitcoin, "1", "50000", base),
                TestFixtures.sell(2L, portfolio, bitcoin, "1", "70000", base.plusDays(5)));

        assertThat(calculator.calculate(ledger)).isEmpty();
    }

    @Test
    @DisplayName("a sell larger than the position is capped instead of going negative")
    void oversizedSellCannotGoNegative() {
        List<Transaction> ledger = List.of(
                TestFixtures.buy(1L, portfolio, bitcoin, "1", "50000", base),
                TestFixtures.sell(2L, portfolio, bitcoin, "5", "70000", base.plusDays(1)));

        assertThat(calculator.calculate(ledger)).isEmpty();
    }

    @Test
    @DisplayName("a sell with no prior buy is ignored")
    void sellWithoutPositionIsIgnored() {
        List<Transaction> ledger = List.of(
                TestFixtures.sell(1L, portfolio, bitcoin, "1", "70000", base));

        assertThat(calculator.calculate(ledger)).isEmpty();
    }

    @Test
    @DisplayName("transactions are applied in chronological order regardless of input order")
    void sortsChronologically() {
        // The sell is listed first but happened last; applying it first would
        // discard it as a sell against an empty position.
        List<Transaction> ledger = List.of(
                TestFixtures.sell(2L, portfolio, bitcoin, "1", "70000", base.plusDays(5)),
                TestFixtures.buy(1L, portfolio, bitcoin, "2", "50000", base));

        Holding holding = calculator.calculate(ledger).get(0);

        assertThat(holding.quantity()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("separate assets produce separate holdings, sorted by asset name")
    void separatesAssets() {
        List<Transaction> ledger = List.of(
                TestFixtures.buy(1L, portfolio, ethereum, "10", "2000", base),
                TestFixtures.buy(2L, portfolio, bitcoin, "1", "50000", base));

        List<Holding> holdings = calculator.calculate(ledger);

        assertThat(holdings).hasSize(2);
        assertThat(holdings).extracting(h -> h.asset().getSymbol())
                .containsExactly("BTC", "ETH");
    }

    @Test
    @DisplayName("average cost of an empty position is zero rather than a divide-by-zero")
    void averageCostOfZeroQuantity() {
        Holding empty = new Holding(bitcoin, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(empty.averageCostUsd()).isEqualByComparingTo("0");
    }
}
