package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;
import com.kodilla.portfolio.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertStrategyFactoryTest {

    private final AlertStrategyFactory factory = new AlertStrategyFactory(List.of(
            new PriceAboveAlertStrategy(),
            new PriceBelowAlertStrategy(),
            new PortfolioValueAboveAlertStrategy(),
            new PortfolioValueBelowAlertStrategy()));

    @Test
    @DisplayName("resolves each alert type to the strategy that declares it")
    void resolvesEveryType() {
        assertThat(factory.strategyFor(AlertType.PRICE_ABOVE))
                .containsInstanceOf(PriceAboveAlertStrategy.class);
        assertThat(factory.strategyFor(AlertType.PRICE_BELOW))
                .containsInstanceOf(PriceBelowAlertStrategy.class);
        assertThat(factory.strategyFor(AlertType.PORTFOLIO_VALUE_ABOVE))
                .containsInstanceOf(PortfolioValueAboveAlertStrategy.class);
        assertThat(factory.strategyFor(AlertType.PORTFOLIO_VALUE_BELOW))
                .containsInstanceOf(PortfolioValueBelowAlertStrategy.class);
    }

    @Test
    @DisplayName("every alert type in the enum has an implementation")
    void coversTheWholeEnum() {
        for (AlertType type : AlertType.values()) {
            assertThat(factory.strategyFor(type)).as("strategy for %s", type).isPresent();
        }
    }

    @Test
    @DisplayName("returns empty rather than null for a type with no strategy")
    void emptyForUnregisteredType() {
        AlertStrategyFactory sparse = new AlertStrategyFactory(List.of(new PriceAboveAlertStrategy()));

        assertThat(sparse.strategyFor(AlertType.PRICE_BELOW)).isEmpty();
    }

    @Test
    @DisplayName("require returns the strategy when one is registered")
    void requireReturnsStrategy() {
        assertThat(factory.require(AlertType.PRICE_BELOW)).isInstanceOf(PriceBelowAlertStrategy.class);
    }

    @Test
    @DisplayName("require rejects an unsupported type as a business-rule error")
    void requireRejectsUnsupportedType() {
        AlertStrategyFactory sparse = new AlertStrategyFactory(List.of(new PriceAboveAlertStrategy()));

        assertThatThrownBy(() -> sparse.require(AlertType.PORTFOLIO_VALUE_BELOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PORTFOLIO_VALUE_BELOW");
    }

    @Test
    @DisplayName("rejects two strategies claiming the same type instead of silently dropping one")
    void rejectsDuplicateRegistration() {
        AlertStrategy duplicate = new AlertStrategy() {
            @Override
            public AlertType supportedType() {
                return AlertType.PRICE_ABOVE;
            }

            @Override
            public Optional<AlertTrigger> evaluate(Alert alert, AlertContext context) {
                return Optional.empty();
            }
        };

        assertThatThrownBy(() ->
                new AlertStrategyFactory(List.of(new PriceAboveAlertStrategy(), duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRICE_ABOVE");
    }

    @Test
    @DisplayName("an alert type watches an asset only if its strategy says so")
    void strategiesDeclareWhetherTheyNeedAnAsset() {
        assertThat(factory.require(AlertType.PRICE_ABOVE).requiresAsset()).isTrue();
        assertThat(factory.require(AlertType.PRICE_BELOW).requiresAsset()).isTrue();
        assertThat(factory.require(AlertType.PORTFOLIO_VALUE_ABOVE).requiresAsset()).isFalse();
        assertThat(factory.require(AlertType.PORTFOLIO_VALUE_BELOW).requiresAsset()).isFalse();
    }
}
