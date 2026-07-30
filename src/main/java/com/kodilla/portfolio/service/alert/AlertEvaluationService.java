package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.domain.PriceSnapshot;
import com.kodilla.portfolio.repository.AlertRepository;
import com.kodilla.portfolio.service.PriceService;
import com.kodilla.portfolio.service.valuation.PortfolioValuationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Walks every active alert, asks the matching strategy whether it should fire,
 * and publishes the ones that do. This is the class the scheduler drives.
 */
@Service
public class AlertEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

    private final AlertRepository alertRepository;
    private final AlertStrategyFactory strategyFactory;
    private final AlertPublisher alertPublisher;
    private final PriceService priceService;
    private final PortfolioValuationService valuationService;
    private final Duration cooldown;

    public AlertEvaluationService(AlertRepository alertRepository,
                                  AlertStrategyFactory strategyFactory,
                                  AlertPublisher alertPublisher,
                                  PriceService priceService,
                                  PortfolioValuationService valuationService,
                                  @Value("${app.alerts.cooldown-minutes:60}") long cooldownMinutes) {
        this.alertRepository = alertRepository;
        this.strategyFactory = strategyFactory;
        this.alertPublisher = alertPublisher;
        this.priceService = priceService;
        this.valuationService = valuationService;
        this.cooldown = Duration.ofMinutes(cooldownMinutes);
    }

    /** Evaluates all active alerts once. */
    @Transactional
    public int evaluateAll() {
        List<Alert> activeAlerts = alertRepository.findByActiveTrue();
        if (activeAlerts.isEmpty()) {
            return 0;
        }

        AlertContext context = new SnapshotAlertContext();
        int triggered = 0;

        for (Alert alert : activeAlerts) {
            if (isCoolingDown(alert)) {
                continue;
            }
            Optional<AlertStrategy> strategy = strategyFactory.strategyFor(alert.getType());
            if (strategy.isEmpty()) {
                log.warn("No strategy registered for alert type {} (alert {})",
                        alert.getType(), alert.getId());
                continue;
            }
            Optional<AlertTrigger> trigger = strategy.get().evaluate(alert, context);
            if (trigger.isPresent()) {
                alertPublisher.publish(trigger.get());
                triggered++;
            }
        }

        log.debug("Evaluated {} active alert(s), {} triggered", activeAlerts.size(), triggered);
        return triggered;
    }

    /**
     * An alert whose condition stays true would otherwise fire on every run.
     * Suppress it until the cooldown has elapsed.
     */
    private boolean isCoolingDown(Alert alert) {
        LocalDateTime lastTriggered = alert.getLastTriggeredAt();
        return lastTriggered != null && lastTriggered.plus(cooldown).isAfter(LocalDateTime.now());
    }

    /**
     * Reads prices once per run and memoizes portfolio valuations, so evaluating
     * fifty alerts on one portfolio does not price it fifty times.
     */
    private final class SnapshotAlertContext implements AlertContext {

        private final Map<Long, PriceSnapshot> prices = priceService.findLatestPrices();
        private final Map<Long, Optional<BigDecimal>> portfolioValues = new HashMap<>();

        @Override
        public Optional<BigDecimal> priceOf(Long assetId) {
            return Optional.ofNullable(prices.get(assetId)).map(PriceSnapshot::getPriceUsd);
        }

        @Override
        public Optional<BigDecimal> portfolioValueUsd(Portfolio portfolio) {
            return portfolioValues.computeIfAbsent(portfolio.getId(),
                    key -> Optional.ofNullable(valuationService.totalValueUsd(portfolio)));
        }
    }
}
