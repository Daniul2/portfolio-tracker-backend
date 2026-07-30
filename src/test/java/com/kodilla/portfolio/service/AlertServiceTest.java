package com.kodilla.portfolio.service;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.dto.AlertDtos.AlertRequest;
import com.kodilla.portfolio.dto.AlertDtos.AlertResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.repository.AlertEventRepository;
import com.kodilla.portfolio.repository.AlertRepository;
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.PortfolioRepository;
import com.kodilla.portfolio.service.alert.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private AlertEventRepository alertEventRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AuditService auditService;

    private AlertService service;

    private final User user = TestFixtures.user(1L, "demo");
    private final Portfolio portfolio = TestFixtures.portfolio(10L, user, "Main", "PLN");
    private final Asset bitcoin = TestFixtures.asset(100L, "bitcoin", "BTC");

    @BeforeEach
    void setUp() {
        AlertStrategyFactory factory = new AlertStrategyFactory(List.of(
                new PriceAboveAlertStrategy(),
                new PriceBelowAlertStrategy(),
                new PortfolioValueAboveAlertStrategy(),
                new PortfolioValueBelowAlertStrategy()));
        service = new AlertService(alertRepository, alertEventRepository, portfolioRepository,
                assetRepository, factory, auditService);
    }

    @Test
    @DisplayName("creates a price alert bound to an asset")
    void createsPriceAlert() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            TestFixtures.setId(alert, 5L);
            return alert;
        });

        AlertResponse response = service.create(new AlertRequest(
                10L, 100L, AlertType.PRICE_ABOVE, new BigDecimal("70000"), null));

        assertThat(response.assetId()).isEqualTo(100L);
        assertThat(response.symbol()).isEqualTo("BTC");
        assertThat(response.active()).isTrue();
        verify(auditService).record(eq("ALERT_CREATED"), eq("Alert"), eq(5L), anyString());
    }

    @Test
    @DisplayName("a price alert without an asset is rejected")
    void priceAlertRequiresAsset() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> service.create(new AlertRequest(
                10L, null, AlertType.PRICE_ABOVE, new BigDecimal("70000"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("assetId is required");
    }

    @Test
    @DisplayName("a portfolio-level alert ignores any asset that was supplied")
    void portfolioAlertDropsAsset() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            TestFixtures.setId(alert, 6L);
            return alert;
        });

        AlertResponse response = service.create(new AlertRequest(
                10L, 100L, AlertType.PORTFOLIO_VALUE_ABOVE, new BigDecimal("5000"), null));

        // The strategy has no use for an asset, so the response must not imply one.
        assertThat(response.assetId()).isNull();
        verify(assetRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("an explicitly inactive alert is stored inactive")
    void honoursInactiveFlag() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            if (alert.getId() == null) {
                TestFixtures.setId(alert, 7L);
            }
            return alert;
        });

        AlertResponse response = service.create(new AlertRequest(
                10L, null, AlertType.PORTFOLIO_VALUE_BELOW, new BigDecimal("100"), false));

        assertThat(response.active()).isFalse();
    }

    @Test
    @DisplayName("creating for an unknown portfolio is a 404")
    void unknownPortfolioIsNotFound() {
        when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new AlertRequest(
                99L, null, AlertType.PORTFOLIO_VALUE_ABOVE, BigDecimal.TEN, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("creating against an unknown asset is a 404")
    void unknownAssetIsNotFound() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new AlertRequest(
                10L, 99L, AlertType.PRICE_ABOVE, BigDecimal.TEN, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updates threshold and active flag")
    void updatesAlert() {
        Alert existing = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "70000");
        when(alertRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertResponse response = service.update(5L, new AlertRequest(
                10L, 100L, AlertType.PRICE_BELOW, new BigDecimal("30000"), false));

        assertThat(response.type()).isEqualTo(AlertType.PRICE_BELOW);
        assertThat(response.threshold()).isEqualByComparingTo("30000");
        assertThat(response.active()).isFalse();
    }

    @Test
    @DisplayName("a null active flag on update leaves the flag untouched")
    void updateLeavesActiveWhenNull() {
        Alert existing = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "70000");
        existing.setActive(false);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertResponse response = service.update(5L, new AlertRequest(
                10L, 100L, AlertType.PRICE_ABOVE, new BigDecimal("80000"), null));

        assertThat(response.active()).isFalse();
    }

    @Test
    @DisplayName("deletes an alert and audits it")
    void deletesAlert() {
        Alert existing = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "70000");
        when(alertRepository.findById(5L)).thenReturn(Optional.of(existing));

        service.delete(5L);

        verify(alertRepository).delete(existing);
        verify(auditService).record(eq("ALERT_DELETED"), eq("Alert"), eq(5L), anyString());
    }

    @Test
    @DisplayName("acknowledging an event marks it seen and audits it")
    void acknowledgesEvent() {
        Alert alert = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "70000");
        AlertEvent event = new AlertEvent(alert, "fired", new BigDecimal("80000"));
        TestFixtures.setId(event, 3L);
        when(alertEventRepository.findById(3L)).thenReturn(Optional.of(event));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.acknowledgeEvent(3L).acknowledged()).isTrue();
        verify(auditService).record(eq("ALERT_EVENT_ACKNOWLEDGED"), eq("AlertEvent"), eq(3L), anyString());
    }

    @Test
    @DisplayName("acknowledging a missing event is a 404")
    void acknowledgeMissingEvent() {
        when(alertEventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledgeEvent(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lists alerts, portfolio alerts and events")
    void listsAlertsAndEvents() {
        Alert alert = TestFixtures.alert(5L, portfolio, bitcoin, AlertType.PRICE_ABOVE, "70000");
        AlertEvent event = new AlertEvent(alert, "fired", new BigDecimal("80000"));
        TestFixtures.setId(event, 3L);

        when(alertRepository.findAll()).thenReturn(List.of(alert));
        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(alertRepository.findByPortfolioId(10L)).thenReturn(List.of(alert));
        when(alertEventRepository.findByAlertPortfolioIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(event));
        when(alertEventRepository.findByAcknowledgedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(event));

        assertThat(service.findAll()).hasSize(1);
        assertThat(service.findByPortfolio(10L)).hasSize(1);
        assertThat(service.findEventsByPortfolio(10L)).hasSize(1);
        assertThat(service.findUnacknowledgedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("listing for an unknown portfolio is a 404")
    void listForUnknownPortfolio() {
        when(portfolioRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByPortfolio(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.findEventsByPortfolio(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("an unknown alert id is a 404")
    void unknownAlertIsNotFound() {
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
