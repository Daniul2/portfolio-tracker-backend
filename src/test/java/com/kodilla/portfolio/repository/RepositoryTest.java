package com.kodilla.portfolio.repository;

import com.kodilla.portfolio.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the derived query methods and the database constraints actually
 * behave as the services assume, against a real (in-memory) database.
 */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private PriceSnapshotRepository snapshotRepository;
    @Autowired
    private ExchangeRateRepository rateRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private AlertEventRepository alertEventRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private User user;
    private Portfolio portfolio;
    private Asset bitcoin;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("demo", "demo@example.com", "Demo"));
        portfolio = portfolioRepository.save(new Portfolio(user, "Main", "PLN"));
        bitcoin = assetRepository.save(new Asset("bitcoin", "BTC", "Bitcoin"));
    }

    @Test
    @DisplayName("createdAt is populated automatically on insert")
    void populatesCreatedAt() {
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(portfolio.getCreatedAt()).isNotNull();
        assertThat(bitcoin.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("username and email uniqueness is enforced by the database")
    void enforcesUserUniqueness() {
        assertThat(userRepository.existsByUsername("demo")).isTrue();
        assertThat(userRepository.existsByEmail("demo@example.com")).isTrue();
        assertThat(userRepository.findByUsername("demo")).isPresent();
        assertThat(userRepository.findByUsername("nobody")).isEmpty();

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("demo", "other@example.com", "Clash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("an asset's external id is unique")
    void enforcesAssetExternalIdUniqueness() {
        assertThat(assetRepository.existsByExternalId("bitcoin")).isTrue();
        assertThat(assetRepository.findByExternalId("bitcoin")).isPresent();
        assertThat(assetRepository.findBySymbolIgnoreCase("btc")).isPresent();

        assertThatThrownBy(() -> assetRepository.saveAndFlush(
                new Asset("bitcoin", "XBT", "Bitcoin clone")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("only active assets are returned by findByActiveTrue")
    void filtersActiveAssets() {
        Asset inactive = new Asset("dogecoin", "DOGE", "Dogecoin");
        inactive.setActive(false);
        assetRepository.save(inactive);

        assertThat(assetRepository.findByActiveTrue())
                .extracting(Asset::getSymbol).containsExactly("BTC");
    }

    @Test
    @DisplayName("one rate per currency per effective date is enforced")
    void enforcesRateUniqueness() {
        LocalDate today = LocalDate.now();
        rateRepository.saveAndFlush(new ExchangeRate("USD", new BigDecimal("3.7962"), today));

        // This is the exact collision the concurrent-refresh guard prevents.
        assertThatThrownBy(() -> rateRepository.saveAndFlush(
                new ExchangeRate("USD", new BigDecimal("3.8000"), today)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the same currency on a different date is allowed")
    void allowsSameCurrencyOnDifferentDates() {
        LocalDate today = LocalDate.now();
        rateRepository.save(new ExchangeRate("USD", new BigDecimal("3.7962"), today));
        rateRepository.save(new ExchangeRate("USD", new BigDecimal("3.8100"), today.minusDays(1)));

        assertThat(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("USD"))
                .get()
                .extracting(ExchangeRate::getEffectiveDate)
                .isEqualTo(today);
        assertThat(rateRepository.findByEffectiveDate(today)).hasSize(1);
    }

    @Test
    @DisplayName("transactions are returned newest first and counted per portfolio and asset")
    void ordersAndCountsTransactions() {
        LocalDateTime now = LocalDateTime.now();
        transactionRepository.save(new Transaction(portfolio, bitcoin, TransactionType.BUY,
                new BigDecimal("1"), new BigDecimal("40000"), now.minusDays(10)));
        transactionRepository.save(new Transaction(portfolio, bitcoin, TransactionType.BUY,
                new BigDecimal("1"), new BigDecimal("50000"), now.minusDays(1)));

        var ordered = transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(portfolio.getId());
        assertThat(ordered).hasSize(2);
        // Compared by value: the database applies its own scale to the column.
        assertThat(ordered.get(0).getPricePerUnitUsd()).isEqualByComparingTo("50000");
        assertThat(ordered.get(1).getPricePerUnitUsd()).isEqualByComparingTo("40000");
        assertThat(transactionRepository.countByPortfolioId(portfolio.getId())).isEqualTo(2);
        assertThat(transactionRepository.countByAssetId(bitcoin.getId())).isEqualTo(2);
        assertThat(transactionRepository.findByPortfolioIdAndAssetId(
                portfolio.getId(), bitcoin.getId())).hasSize(2);
    }

    @Test
    @DisplayName("the newest snapshot per asset is found, and history is windowed")
    void queriesSnapshots() {
        PriceSnapshot older = new PriceSnapshot(bitcoin, new BigDecimal("40000"), BigDecimal.ZERO);
        older.setCapturedAt(LocalDateTime.now().minusDays(5));
        snapshotRepository.save(older);

        PriceSnapshot newer = new PriceSnapshot(bitcoin, new BigDecimal("60000"), BigDecimal.ZERO);
        newer.setCapturedAt(LocalDateTime.now().minusHours(1));
        snapshotRepository.save(newer);

        assertThat(snapshotRepository.findFirstByAssetIdOrderByCapturedAtDesc(bitcoin.getId()))
                .get()
                .extracting(PriceSnapshot::getPriceUsd, org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("60000");

        // A two-day window excludes the five-day-old reading.
        assertThat(snapshotRepository.findByAssetIdAndCapturedAtAfterOrderByCapturedAtAsc(
                bitcoin.getId(), LocalDateTime.now().minusDays(2))).hasSize(1);
    }

    @Test
    @DisplayName("purging removes only snapshots older than the cutoff")
    void purgesOldSnapshots() {
        PriceSnapshot old = new PriceSnapshot(bitcoin, new BigDecimal("40000"), BigDecimal.ZERO);
        old.setCapturedAt(LocalDateTime.now().minusDays(200));
        snapshotRepository.save(old);
        PriceSnapshot recent = new PriceSnapshot(bitcoin, new BigDecimal("60000"), BigDecimal.ZERO);
        recent.setCapturedAt(LocalDateTime.now());
        snapshotRepository.save(recent);

        long removed = snapshotRepository.deleteByCapturedAtBefore(LocalDateTime.now().minusDays(90));

        assertThat(removed).isEqualTo(1);
        assertThat(snapshotRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("active alerts and per-portfolio alerts are queried correctly")
    void queriesAlerts() {
        alertRepository.save(new Alert(portfolio, bitcoin, AlertType.PRICE_ABOVE, new BigDecimal("70000")));
        Alert inactive = new Alert(portfolio, null, AlertType.PORTFOLIO_VALUE_BELOW, new BigDecimal("100"));
        inactive.setActive(false);
        alertRepository.save(inactive);

        assertThat(alertRepository.findByPortfolioId(portfolio.getId())).hasSize(2);
        assertThat(alertRepository.findByActiveTrue()).hasSize(1);
    }

    @Test
    @DisplayName("a portfolio-level alert may have no asset")
    void allowsAlertWithoutAsset() {
        Alert saved = alertRepository.saveAndFlush(new Alert(
                portfolio, null, AlertType.PORTFOLIO_VALUE_ABOVE, new BigDecimal("5000")));

        assertThat(saved.getAsset()).isNull();
    }

    @Test
    @DisplayName("alert events are found through their portfolio and by acknowledgement")
    void queriesAlertEvents() {
        Alert alert = alertRepository.save(
                new Alert(portfolio, bitcoin, AlertType.PRICE_ABOVE, new BigDecimal("70000")));
        alertEventRepository.save(new AlertEvent(alert, "fired", new BigDecimal("71000")));
        AlertEvent seen = new AlertEvent(alert, "older", new BigDecimal("72000"));
        seen.setAcknowledged(true);
        alertEventRepository.save(seen);

        assertThat(alertEventRepository
                .findByAlertPortfolioIdOrderByCreatedAtDesc(portfolio.getId())).hasSize(2);
        assertThat(alertEventRepository.findByAcknowledgedFalseOrderByCreatedAtDesc()).hasSize(1);
    }

    @Test
    @DisplayName("deleting a portfolio cascades to its transactions and alerts")
    void cascadesPortfolioDelete() {
        portfolio.getTransactions().add(new Transaction(portfolio, bitcoin, TransactionType.BUY,
                new BigDecimal("1"), new BigDecimal("40000"), LocalDateTime.now()));
        portfolio.getAlerts().add(
                new Alert(portfolio, bitcoin, AlertType.PRICE_ABOVE, new BigDecimal("70000")));
        portfolioRepository.saveAndFlush(portfolio);

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(alertRepository.count()).isEqualTo(1);

        portfolioRepository.delete(portfolio);
        portfolioRepository.flush();

        assertThat(transactionRepository.count()).isZero();
        assertThat(alertRepository.count()).isZero();
    }

    @Test
    @DisplayName("audit entries are listed newest first and filterable by entity type")
    void queriesAuditLog() {
        auditLogRepository.save(new AuditLog("USER_CREATED", "User", 1L, "a"));
        auditLogRepository.save(new AuditLog("ALERT_TRIGGERED", "Alert", 2L, "b"));

        assertThat(auditLogRepository.findTop100ByOrderByCreatedAtDesc()).hasSize(2);
        assertThat(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("Alert")).hasSize(1);
    }

    @Test
    @DisplayName("portfolios are found by user and name uniqueness is checked per user")
    void queriesPortfolios() {
        assertThat(portfolioRepository.findByUserId(user.getId())).hasSize(1);
        assertThat(portfolioRepository.countByUserId(user.getId())).isEqualTo(1);
        assertThat(portfolioRepository.existsByUserIdAndName(user.getId(), "Main")).isTrue();
        assertThat(portfolioRepository.existsByUserIdAndName(user.getId(), "Other")).isFalse();
    }
}
