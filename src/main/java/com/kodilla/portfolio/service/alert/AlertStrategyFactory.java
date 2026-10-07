package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.AlertType;
import com.kodilla.portfolio.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Factory pattern: resolves an {@link AlertType} to the strategy that handles it. */
@Component
public class AlertStrategyFactory {

    private final Map<AlertType, AlertStrategy> strategiesByType = new EnumMap<>(AlertType.class);

    public AlertStrategyFactory(List<AlertStrategy> strategies) {
        for (AlertStrategy strategy : strategies) {
            AlertStrategy previous = strategiesByType.put(strategy.supportedType(), strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two strategies registered for alert type " + strategy.supportedType()
                                + ": " + previous.getClass().getName()
                                + " and " + strategy.getClass().getName());
            }
        }
    }

    public Optional<AlertStrategy> strategyFor(AlertType type) {
        return Optional.ofNullable(strategiesByType.get(type));
    }

    /** As {@link #strategyFor}, but a missing strategy is the caller's error (HTTP 422). */
    public AlertStrategy require(AlertType type) {
        return strategyFor(type).orElseThrow(() ->
                new BusinessRuleException("Alert type " + type + " is not supported"));
    }
}
