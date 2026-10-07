package com.kodilla.portfolio.scheduler;

import com.kodilla.portfolio.dto.DashboardDtos.MarketRefreshResponse;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.service.PriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The application's two scheduled jobs. */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataScheduler.class);

    private final PortfolioFacade portfolioFacade;
    private final PriceService priceService;
    private final int retentionDays;

    public MarketDataScheduler(PortfolioFacade portfolioFacade,
                               PriceService priceService,
                               @Value("${app.scheduler.price-retention-days}") int retentionDays) {
        this.portfolioFacade = portfolioFacade;
        this.priceService = priceService;
        this.retentionDays = retentionDays;
    }

    /** Refreshes market data and fires any alerts whose threshold was crossed. */
    @Scheduled(fixedDelayString = "${app.scheduler.market-refresh-ms}",
            initialDelayString = "${app.scheduler.initial-delay-ms}")
    public void refreshMarketData() {
        try {
            MarketRefreshResponse result = portfolioFacade.refreshMarketData();
            if (result.isFullySuccessful()) {
                log.info("Scheduled refresh: {} price(s), {} rate(s), {} alert(s) triggered",
                        result.pricesSaved(), result.ratesSaved(), result.alertsTriggered());
            } else {
                log.warn("Scheduled refresh finished with problems: {}", result.failures());
            }
        } catch (RuntimeException e) {
            // Spring would log and keep scheduling anyway; catching here keeps the
            // log message readable and in one place.
            log.error("Scheduled market data refresh failed", e);
        }
    }

    /** Keeps the snapshot table to the retention window. */
    @Scheduled(cron = "${app.scheduler.purge-cron}")
    public void purgeOldPriceHistory() {
        try {
            long removed = priceService.purgeOlderThan(retentionDays);
            log.info("Purged {} price snapshot(s) older than {} day(s)", removed, retentionDays);
        } catch (RuntimeException e) {
            log.error("Scheduled price history purge failed", e);
        }
    }
}
