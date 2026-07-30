package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;
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
        assertThat(factory.supportedTypes()).containsExactlyInAnyOrder(AlertType.values());
    }

    @Test
    @DisplayName("returns empty rather than null for a type with no strategy")
    void emptyForUnregisteredType() {
        AlertStrategyFactory sparse = new AlertStrategyFactory(List.of(new PriceAboveAlertStrategy()));

        assertThat(sparse.strategyFor(AlertType.PRICE_BELOW)).isEmpty();
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
    @DisplayName("supportedTypes cannot be mutated by callers")
    void supportedTypesIsUnmodifiable() {
        assertThatThrownBy(() -> factory.supportedTypes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
