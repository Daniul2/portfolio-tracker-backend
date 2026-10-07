package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;
import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AlertPublisherTest {

    private final User user = TestFixtures.user(1L, "tester");
    private final Portfolio portfolio = TestFixtures.portfolio(10L, user, "Main", "PLN");
    private final Alert alert = TestFixtures.alert(
            5L, portfolio, null, AlertType.PORTFOLIO_VALUE_ABOVE, "100");
    private final AlertTrigger trigger = new AlertTrigger(alert, new BigDecimal("150"), "fired");

    /** Records the order observers were called in. */
    private static final class RecordingObserver implements AlertObserver {
        private final String name;
        private final int order;
        private final List<String> log;

        RecordingObserver(String name, int order, List<String> log) {
            this.name = name;
            this.order = order;
            this.log = log;
        }

        @Override
        public void onAlertTriggered(AlertTrigger trigger) {
            log.add(name);
        }

        @Override
        public int order() {
            return order;
        }
    }

    @Test
    @DisplayName("notifies every registered observer")
    void notifiesAllObservers() {
        List<String> calls = new ArrayList<>();
        AlertPublisher publisher = publisher(List.of(
                new RecordingObserver("a", 0, calls),
                new RecordingObserver("b", 1, calls)));

        publisher.publish(trigger);

        assertThat(calls).containsExactly("a", "b");
    }

    @Test
    @DisplayName("respects the declared order regardless of registration order")
    void sortsByOrder() {
        List<String> calls = new ArrayList<>();
        AlertPublisher publisher = publisher(List.of(
                new RecordingObserver("last", 9, calls),
                new RecordingObserver("first", 0, calls),
                new RecordingObserver("middle", 5, calls)));

        publisher.publish(trigger);

        assertThat(calls).containsExactly("first", "middle", "last");
    }

    @Test
    @DisplayName("one failing observer does not stop the others")
    void isolatesObserverFailures() {
        List<String> calls = new ArrayList<>();
        AlertObserver exploding = new AlertObserver() {
            @Override
            public void onAlertTriggered(AlertTrigger trigger) {
                throw new IllegalStateException("observer blew up");
            }

            @Override
            public int order() {
                return 1;
            }
        };

        AlertPublisher publisher = publisher(List.of(
                new RecordingObserver("before", 0, calls),
                exploding,
                new RecordingObserver("after", 2, calls)));

        publisher.publish(trigger);

        // The observer between them threw, yet both survivors ran.
        assertThat(calls).containsExactly("before", "after");
    }

    @Test
    @DisplayName("publishing with no observers registered is harmless")
    void handlesNoObservers() {
        assertThatCode(() -> publisher(List.of()).publish(trigger)).doesNotThrowAnyException();
    }

    /**
     * Transactions are a no-op here; real transactional isolation is covered by
     * AlertObserverIsolationTest against the full application context.
     */
    private static AlertPublisher publisher(List<AlertObserver> observers) {
        return new AlertPublisher(observers, TestFixtures.noOpTransactionManager());
    }
}
