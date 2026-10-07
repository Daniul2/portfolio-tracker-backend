package com.kodilla.portfolio.scheduler;

import com.kodilla.portfolio.dto.DashboardDtos.MarketRefreshResponse;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.service.PriceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataSchedulerTest {

    @Mock
    private PortfolioFacade portfolioFacade;
    @Mock
    private PriceService priceService;

    private MarketDataScheduler scheduler() {
        return new MarketDataScheduler(portfolioFacade, priceService, 90);
    }

    @Test
    @DisplayName("the refresh job delegates to the facade")
    void refreshDelegates() {
        when(portfolioFacade.refreshMarketData())
                .thenReturn(new MarketRefreshResponse(4, 32, 1, List.of(), LocalDateTime.now()));

        scheduler().refreshMarketData();

        verify(portfolioFacade).refreshMarketData();
    }

    @Test
    @DisplayName("a partial failure is logged, not rethrown")
    void tolerantOfPartialFailure() {
        when(portfolioFacade.refreshMarketData()).thenReturn(new MarketRefreshResponse(
                0, 32, 0, List.of("CoinGecko: timed out"), LocalDateTime.now()));

        assertThatCode(() -> scheduler().refreshMarketData()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unexpected exception never escapes the scheduled method")
    void swallowsUnexpectedFailure() {
        // The job reports its own failure instead of relying on the framework's log.
        when(portfolioFacade.refreshMarketData()).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> scheduler().refreshMarketData()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the purge job uses the configured retention window")
    void purgeUsesRetentionWindow() {
        when(priceService.purgeOlderThan(90)).thenReturn(7L);

        scheduler().purgeOldPriceHistory();

        verify(priceService).purgeOlderThan(90);
    }

    @Test
    @DisplayName("a purge failure never escapes either")
    void swallowsPurgeFailure() {
        when(priceService.purgeOlderThan(90)).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> scheduler().purgeOldPriceHistory()).doesNotThrowAnyException();
    }
}
