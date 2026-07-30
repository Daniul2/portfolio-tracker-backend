package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.repository.AlertEventRepository;
import com.kodilla.portfolio.repository.AlertRepository;
import com.kodilla.portfolio.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertObserverTest {

    private static final User USER = TestFixtures.user(1L, "tester");
    private static final Portfolio PORTFOLIO = TestFixtures.portfolio(10L, USER, "Main", "PLN");
    private static final Asset BITCOIN = TestFixtures.asset(100L, "bitcoin", "BTC");

    private static AlertTrigger trigger() {
        Alert alert = TestFixtures.alert(7L, PORTFOLIO, BITCOIN, AlertType.PRICE_ABOVE, "50000");
        return new AlertTrigger(alert, new BigDecimal("60000"), "BTC rose above 50000 USD");
    }

    @ExtendWith(MockitoExtension.class)
    static class Persisting {

        @Mock
        private AlertEventRepository alertEventRepository;
        @Mock
        private AlertRepository alertRepository;
        @InjectMocks
        private PersistingAlertObserver observer;

        @Test
        @DisplayName("saves an event carrying the message and the observed value")
        void savesEvent() {
            AlertTrigger trigger = trigger();
            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            observer.onAlertTriggered(trigger);

            ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
            verify(alertEventRepository).save(captor.capture());
            AlertEvent saved = captor.getValue();
            assertThat(saved.getMessage()).isEqualTo("BTC rose above 50000 USD");
            assertThat(saved.getValueAtTrigger()).isEqualByComparingTo("60000");
            assertThat(saved.isAcknowledged()).isFalse();
        }

        @Test
        @DisplayName("stamps the alert so the cooldown can suppress repeats")
        void stampsAlert() {
            AlertTrigger trigger = trigger();
            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            observer.onAlertTriggered(trigger);

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());
            assertThat(captor.getValue().getLastTriggeredAt()).isNotNull();
        }

        @Test
        @DisplayName("runs before the other observers")
        void runsFirst() {
            assertThat(observer.order()).isZero();
        }
    }

    @ExtendWith(MockitoExtension.class)
    static class Auditing {

        @Mock
        private AuditService auditService;
        @InjectMocks
        private AuditingAlertObserver observer;

        @Test
        @DisplayName("writes an ALERT_TRIGGERED audit row")
        void writesAuditRow() {
            observer.onAlertTriggered(trigger());

            verify(auditService).record(eq("ALERT_TRIGGERED"), eq("Alert"), eq(7L),
                    eq("BTC rose above 50000 USD"));
        }

        @Test
        void runsAfterPersisting() {
            assertThat(observer.order()).isEqualTo(1);
        }
    }

    static class Logging {

        @Test
        @DisplayName("logging an alert does not throw")
        void logsWithoutFailing() {
            LoggingAlertObserver observer = new LoggingAlertObserver();

            observer.onAlertTriggered(trigger());

            assertThat(observer.order()).isEqualTo(2);
        }
    }

    static class Ordering {

        @Test
        @DisplayName("the three real observers have distinct, increasing orders")
        void ordersAreDistinct() {
            List<Integer> orders = List.of(
                    new PersistingAlertObserver(null, null).order(),
                    new AuditingAlertObserver(null).order(),
                    new LoggingAlertObserver().order());

            assertThat(orders).containsExactly(0, 1, 2);
        }
    }
}
