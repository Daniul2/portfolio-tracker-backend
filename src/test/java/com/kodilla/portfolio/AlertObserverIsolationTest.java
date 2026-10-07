package com.kodilla.portfolio;

import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.repository.*;
import com.kodilla.portfolio.service.alert.AlertEvaluationService;
import com.kodilla.portfolio.service.alert.AlertObserver;
import com.kodilla.portfolio.service.alert.AlertTrigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** A transactional observer that fails must not take the other observers' writes down with it. */
@SpringBootTest
@Import(AlertObserverIsolationTest.FailingObserverConfig.class)
class AlertObserverIsolationTest {

    @TestConfiguration
    static class FailingObserverConfig {
        @Bean
        FailingTransactionalObserver failingTransactionalObserver() {
            return new FailingTransactionalObserver();
        }
    }

    /** Transactional, and runs before every real observer: the worst case. */
    public static class FailingTransactionalObserver implements AlertObserver {
        @Override
        @Transactional
        public void onAlertTriggered(AlertTrigger trigger) {
            throw new IllegalStateException("observer failed on purpose");
        }

        @Override
        public int order() {
            return -1;
        }
    }

    @Autowired
    private AlertEvaluationService alertEvaluationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private PriceSnapshotRepository snapshotRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private AlertEventRepository alertEventRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        snapshotRepository.deleteAll();
        assetRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("the other observers still commit when one transactional observer throws")
    void failingObserverDoesNotRollBackTheOthers() {
        User user = userRepository.save(new User("isolation", "isolation@example.com", "Iso"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(user, "Main", "PLN"));
        Asset bitcoin = assetRepository.save(new Asset("bitcoin", "BTC", "Bitcoin"));
        snapshotRepository.save(new PriceSnapshot(bitcoin, new BigDecimal("64000"), BigDecimal.ZERO));
        Alert alert = alertRepository.save(
                new Alert(portfolio, bitcoin, AlertType.PRICE_ABOVE, new BigDecimal("1")));

        // Before the fix this threw UnexpectedRollbackException at commit.
        int fired = alertEvaluationService.evaluateAll();

        assertThat(fired).isEqualTo(1);
        // Persisting observer committed: the event exists and the alert is stamped.
        assertThat(alertEventRepository.count()).isEqualTo(1);
        assertThat(alertRepository.findById(alert.getId()).orElseThrow().getLastTriggeredAt())
                .isNotNull();
        // Auditing observer committed too.
        assertThat(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("Alert"))
                .extracting(AuditLog::getAction)
                .contains("ALERT_TRIGGERED");
    }
}
