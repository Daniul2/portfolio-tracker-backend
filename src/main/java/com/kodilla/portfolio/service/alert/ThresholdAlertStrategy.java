package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The shared shape of every alert: read one number, compare it with the threshold in one direction,
 * and describe what happened.
 */
public abstract class ThresholdAlertStrategy implements AlertStrategy {

    /** Which side of the threshold fires the alert. */
    protected enum Direction {
        ABOVE("rose above"),
        BELOW("fell below");

        private final String verb;

        Direction(String verb) {
            this.verb = verb;
        }

        /** Strict comparison: a value exactly at the threshold never fires. */
        boolean isCrossed(BigDecimal value, BigDecimal threshold) {
            int comparison = value.compareTo(threshold);
            return this == ABOVE ? comparison > 0 : comparison < 0;
        }
    }

    private final AlertType type;
    private final Direction direction;

    protected ThresholdAlertStrategy(AlertType type, Direction direction) {
        this.type = type;
        this.direction = direction;
    }

    @Override
    public final AlertType supportedType() {
        return type;
    }

    @Override
    public final Optional<AlertTrigger> evaluate(Alert alert, AlertContext context) {
        if (requiresAsset() && alert.getAsset() == null) {
            return Optional.empty();
        }
        Optional<BigDecimal> observed = observe(alert, context);
        if (observed.isEmpty() || !direction.isCrossed(observed.get(), alert.getThreshold())) {
            return Optional.empty();
        }
        String message = "%s %s %s USD (now %s USD)".formatted(
                subject(alert), direction.verb,
                alert.getThreshold().toPlainString(), observed.get().toPlainString());
        return Optional.of(new AlertTrigger(alert, observed.get(), message));
    }

    /** The current value to compare, or empty if it cannot be determined yet. */
    protected abstract Optional<BigDecimal> observe(Alert alert, AlertContext context);

    /** What the alert is about, as it should read at the start of the message. */
    protected abstract String subject(Alert alert);
}
