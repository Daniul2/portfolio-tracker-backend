package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers all four alert strategies, including the boundary cases. */
class AlertStrategyTest {

    private final User user = TestFixtures.user(1L, "tester");
    private final Portfolio portfolio = TestFixtures.portfolio(10L, user, "Main", "PLN");
    private final Asset bitcoin = TestFixtures.asset(100L, "bitcoin", "BTC");

    /** Hand-built context: no Spring, no mocks. */
    private AlertContext contextWith(Map<Long, String> pricesByAsset, String portfolioValue) {
        return new AlertContext() {
            @Override
            public Optional<BigDecimal> priceOf(Long assetId) {
                return Optional.ofNullable(pricesByAsset.get(assetId)).map(BigDecimal::new);
            }

            @Override
            public Optional<BigDecimal> portfolioValueUsd(Portfolio ignored) {
                return Optional.ofNullable(portfolioValue).map(BigDecimal::new);
            }
        };
    }

    @Nested
    @DisplayName("PRICE_ABOVE")
    class PriceAbove {

        private final PriceAboveAlertStrategy strategy = new PriceAboveAlertStrategy();

        @Test
        void declaresItsType() {
            assertThat(strategy.supportedType()).isEqualTo(AlertType.PRICE_ABOVE);
        }

        @Test
        @DisplayName("fires when the price exceeds the threshold")
        void firesAboveThreshold() {
            Alert alert = TestFixtures.alert(1L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "50000");

            Optional<AlertTrigger> trigger =
                    strategy.evaluate(alert, contextWith(Map.of(100L, "60000"), null));

            assertThat(trigger).isPresent();
            assertThat(trigger.get().observedValue()).isEqualByComparingTo("60000");
            assertThat(trigger.get().message()).contains("BTC").contains("rose above");
        }

        @Test
        @DisplayName("does not fire exactly at the threshold")
        void doesNotFireAtThreshold() {
            Alert alert = TestFixtures.alert(1L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "50000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(100L, "50000"), null))).isEmpty();
        }

        @Test
        @DisplayName("does not fire below the threshold")
        void doesNotFireBelow() {
            Alert alert = TestFixtures.alert(1L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "50000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(100L, "40000"), null))).isEmpty();
        }

        @Test
        @DisplayName("does not fire when the asset has no price yet")
        void doesNotFireWithoutPrice() {
            Alert alert = TestFixtures.alert(1L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "50000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(), null))).isEmpty();
        }

        @Test
        @DisplayName("ignores a price alert that has no asset attached")
        void doesNotFireWithoutAsset() {
            Alert alert = TestFixtures.alert(1L, portfolio, null, AlertType.PRICE_ABOVE, "50000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(100L, "60000"), null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("PRICE_BELOW")
    class PriceBelow {

        private final PriceBelowAlertStrategy strategy = new PriceBelowAlertStrategy();

        @Test
        void declaresItsType() {
            assertThat(strategy.supportedType()).isEqualTo(AlertType.PRICE_BELOW);
        }

        @Test
        @DisplayName("fires when the price drops under the threshold")
        void firesBelowThreshold() {
            Alert alert = TestFixtures.alert(1L, portfolio, bitcoin, AlertType.PRICE_BELOW, "50000");

            Optional<AlertTrigger> trigger =
                    strategy.evaluate(alert, contextWith(Map.of(100L, "40000"), null));

            assertThat(trigger).isPresent();
            assertThat(trigger.get().message()).contains("fell below");
        }

        @Test
        @DisplayName("does not fire exactly at the threshold")
        void doesNotFireAtThreshold() {
            Alert alert = TestFixtures.alert(1L, portfolio, bitcoin, AlertType.PRICE_BELOW, "50000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(100L, "50000"), null))).isEmpty();
        }

        @Test
        @DisplayName("does not fire above the threshold")
        void doesNotFireAbove() {
            Alert alert = TestFixtures.alert(1L, portfolio, bitcoin, AlertType.PRICE_BELOW, "50000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(100L, "60000"), null))).isEmpty();
        }

        @Test
        @DisplayName("ignores an alert with no asset attached")
        void doesNotFireWithoutAsset() {
            Alert alert = TestFixtures.alert(1L, portfolio, null, AlertType.PRICE_BELOW, "50000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(), null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("PORTFOLIO_VALUE_ABOVE")
    class PortfolioValueAbove {

        private final PortfolioValueAboveAlertStrategy strategy = new PortfolioValueAboveAlertStrategy();

        @Test
        void declaresItsType() {
            assertThat(strategy.supportedType()).isEqualTo(AlertType.PORTFOLIO_VALUE_ABOVE);
        }

        @Test
        @DisplayName("fires when total value exceeds the threshold")
        void firesAboveThreshold() {
            Alert alert = TestFixtures.alert(
                    1L, portfolio, null, AlertType.PORTFOLIO_VALUE_ABOVE, "10000");

            Optional<AlertTrigger> trigger = strategy.evaluate(alert, contextWith(Map.of(), "15000"));

            assertThat(trigger).isPresent();
            assertThat(trigger.get().message()).contains("Main").contains("rose above");
        }

        @Test
        @DisplayName("does not fire at or below the threshold")
        void doesNotFireAtOrBelow() {
            Alert alert = TestFixtures.alert(
                    1L, portfolio, null, AlertType.PORTFOLIO_VALUE_ABOVE, "10000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(), "10000"))).isEmpty();
            assertThat(strategy.evaluate(alert, contextWith(Map.of(), "9000"))).isEmpty();
        }

        @Test
        @DisplayName("does not fire when the portfolio cannot be valued")
        void doesNotFireWithoutValue() {
            Alert alert = TestFixtures.alert(
                    1L, portfolio, null, AlertType.PORTFOLIO_VALUE_ABOVE, "10000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(), null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("PORTFOLIO_VALUE_BELOW")
    class PortfolioValueBelow {

        private final PortfolioValueBelowAlertStrategy strategy = new PortfolioValueBelowAlertStrategy();

        @Test
        void declaresItsType() {
            assertThat(strategy.supportedType()).isEqualTo(AlertType.PORTFOLIO_VALUE_BELOW);
        }

        @Test
        @DisplayName("fires when total value drops under the threshold")
        void firesBelowThreshold() {
            Alert alert = TestFixtures.alert(
                    1L, portfolio, null, AlertType.PORTFOLIO_VALUE_BELOW, "10000");

            Optional<AlertTrigger> trigger = strategy.evaluate(alert, contextWith(Map.of(), "5000"));

            assertThat(trigger).isPresent();
            assertThat(trigger.get().message()).contains("fell below");
        }

        @Test
        @DisplayName("does not fire at or above the threshold")
        void doesNotFireAtOrAbove() {
            Alert alert = TestFixtures.alert(
                    1L, portfolio, null, AlertType.PORTFOLIO_VALUE_BELOW, "10000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(), "10000"))).isEmpty();
            assertThat(strategy.evaluate(alert, contextWith(Map.of(), "20000"))).isEmpty();
        }

        @Test
        @DisplayName("does not fire when the portfolio cannot be valued")
        void doesNotFireWithoutValue() {
            Alert alert = TestFixtures.alert(
                    1L, portfolio, null, AlertType.PORTFOLIO_VALUE_BELOW, "10000");

            assertThat(strategy.evaluate(alert, contextWith(Map.of(), null))).isEmpty();
        }
    }
}
