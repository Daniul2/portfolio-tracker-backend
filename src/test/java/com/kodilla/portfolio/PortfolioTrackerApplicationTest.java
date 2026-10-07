package com.kodilla.portfolio;

import com.kodilla.portfolio.controller.*;
import com.kodilla.portfolio.external.CryptoPriceProvider;
import com.kodilla.portfolio.external.ExchangeRateProvider;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.scheduler.MarketDataScheduler;
import com.kodilla.portfolio.service.alert.AlertStrategyFactory;
import com.kodilla.portfolio.domain.AlertType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application context. Catches wiring mistakes that unit tests
 * with mocks cannot see, such as an ambiguous bean or a bad property binding.
 */
@SpringBootTest
class PortfolioTrackerApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the application context loads with every layer wired")
    void contextLoads() {
        assertThat(context.getBean(UserController.class)).isNotNull();
        assertThat(context.getBean(PortfolioController.class)).isNotNull();
        assertThat(context.getBean(AssetController.class)).isNotNull();
        assertThat(context.getBean(TransactionController.class)).isNotNull();
        assertThat(context.getBean(AlertController.class)).isNotNull();
        assertThat(context.getBean(PriceController.class)).isNotNull();
        assertThat(context.getBean(ExchangeRateController.class)).isNotNull();
        assertThat(context.getBean(DashboardController.class)).isNotNull();
        assertThat(context.getBean(AuditController.class)).isNotNull();
        assertThat(context.getBean(PortfolioFacade.class)).isNotNull();
    }

    @Test
    @DisplayName("both external providers resolve to exactly one implementation each")
    void externalProvidersAreUnambiguous() {
        // Two RestClient beans of the same type exist, so this would fail if the
        // qualifiers were wrong.
        assertThat(context.getBean(CryptoPriceProvider.class).providerName()).isEqualTo("CoinGecko");
        assertThat(context.getBean(ExchangeRateProvider.class).providerName()).isEqualTo("NBP");
    }

    @Test
    @DisplayName("a strategy is registered for every alert type the enum declares")
    void everyAlertTypeIsImplemented() {
        AlertStrategyFactory factory = context.getBean(AlertStrategyFactory.class);

        for (AlertType type : AlertType.values()) {
            assertThat(factory.strategyFor(type)).as("strategy for %s", type).isPresent();
        }
    }

    @Test
    @DisplayName("the scheduler is absent when disabled by configuration")
    void schedulerDisabledInTests() {
        // The test profile sets app.scheduler.enabled=false, so tests never
        // reach out to the network on a timer.
        assertThat(context.getBeanNamesForType(MarketDataScheduler.class)).isEmpty();
    }
}
