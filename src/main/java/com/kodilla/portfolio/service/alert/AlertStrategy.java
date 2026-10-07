package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;

import java.util.Optional;

/** Strategy pattern: one implementation per {@link AlertType}. */
public interface AlertStrategy {

    /** The alert type this strategy knows how to evaluate. */
    AlertType supportedType();

    /**
     * Whether alerts of this type watch one asset (and so must name one), as
     * opposed to the portfolio as a whole.
     */
    default boolean requiresAsset() {
        return false;
    }

    /** Returns a trigger if the alert's condition is currently met, otherwise empty. */
    Optional<AlertTrigger> evaluate(Alert alert, AlertContext context);
}
