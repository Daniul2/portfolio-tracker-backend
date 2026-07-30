package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;

import java.util.Optional;

/** Strategy pattern: one implementation per {@link AlertType}. */
public interface AlertStrategy {

    /** The alert type this strategy knows how to evaluate. */
    AlertType supportedType();

    /** Returns a trigger if the alert's condition is currently met, otherwise empty. */
    Optional<AlertTrigger> evaluate(Alert alert, AlertContext context);
}
