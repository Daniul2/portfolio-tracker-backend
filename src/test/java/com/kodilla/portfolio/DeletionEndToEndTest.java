package com.kodilla.portfolio;

import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.repository.*;
import com.kodilla.portfolio.service.alert.AlertEvaluationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deletes through the real HTTP API against a real database, after an alert has fired through the
 * real evaluation path.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeletionEndToEndTest {

    @Autowired
    private MockMvc mockMvc;
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

    private User user;
    private Portfolio portfolio;
    private Asset bitcoin;
    private Alert alert;

    @BeforeEach
    void fireAnAlert() {
        user = userRepository.save(new User("e2e", "e2e@example.com", "E2E"));
        portfolio = portfolioRepository.save(new Portfolio(user, "Main", "PLN"));
        bitcoin = assetRepository.save(new Asset("bitcoin", "BTC", "Bitcoin"));
        snapshotRepository.save(new PriceSnapshot(bitcoin, new BigDecimal("64000"), BigDecimal.ZERO));
        alert = alertRepository.save(
                new Alert(portfolio, bitcoin, AlertType.PRICE_ABOVE, new BigDecimal("1")));

        // Fire it the way the scheduler does, so the event row is written by the
        // real observer rather than hand-inserted by the test.
        assertThat(alertEvaluationService.evaluateAll()).isEqualTo(1);
        assertThat(alertEventRepository.count()).isEqualTo(1);
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        snapshotRepository.deleteAll();
        assetRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("DELETE an alert that has fired is 204 and takes its events with it")
    void deletesFiredAlert() throws Exception {
        mockMvc.perform(delete("/v1/alerts/{id}", alert.getId()))
                .andExpect(status().isNoContent());

        assertThat(alertRepository.count()).isZero();
        assertThat(alertEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("DELETE a portfolio whose alert has fired is 204")
    void deletesPortfolioWithFiredAlert() throws Exception {
        mockMvc.perform(delete("/v1/portfolios/{id}", portfolio.getId()))
                .andExpect(status().isNoContent());

        assertThat(portfolioRepository.count()).isZero();
        assertThat(alertEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("DELETE a user whose alert has fired is 204")
    void deletesUserWithFiredAlert() throws Exception {
        mockMvc.perform(delete("/v1/users/{id}", user.getId()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.count()).isZero();
        assertThat(alertEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("DELETE an asset an alert is watching is a 422 explaining why, not a 500")
    void refusesAssetWatchedByAlert() throws Exception {
        mockMvc.perform(delete("/v1/assets/{id}", bitcoin.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("alert")));

        assertThat(assetRepository.existsById(bitcoin.getId())).isTrue();
    }

    @Test
    @DisplayName("DELETE an asset with only price history is 204 and removes that history")
    void deletesAssetWithPriceHistory() throws Exception {
        Asset ethereum = assetRepository.save(new Asset("ethereum", "ETH", "Ethereum"));
        snapshotRepository.save(new PriceSnapshot(ethereum, new BigDecimal("1900"), BigDecimal.ZERO));

        mockMvc.perform(delete("/v1/assets/{id}", ethereum.getId()))
                .andExpect(status().isNoContent());

        assertThat(assetRepository.existsById(ethereum.getId())).isFalse();
        // Bitcoin's snapshot is untouched; only the deleted asset's history went.
        assertThat(snapshotRepository.count()).isEqualTo(1);
    }
}
