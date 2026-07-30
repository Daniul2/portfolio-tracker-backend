package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** Fires when the whole portfolio's USD value falls below the threshold. */
@Component
public class PortfolioValueBelowAlertStrategy implements AlertStrategy {

    @Override
    public AlertType supportedType() {
        return AlertType.PORTFOLIO_VALUE_BELOW;
    }

    @Override
    public Optional<AlertTrigger> evaluate(Alert alert, AlertContext context) {
        Optional<BigDecimal> value = context.portfolioValueUsd(alert.getPortfolio());
        if (value.isEmpty() || value.get().compareTo(alert.getThreshold()) >= 0) {
            return Optional.empty();
        }
        String message = "Portfolio '%s' fell below %s USD (now %s USD)".formatted(
                alert.getPortfolio().getName(), alert.getThreshold().toPlainString(),
                value.get().toPlainString());
        return Optional.of(new AlertTrigger(alert, value.get(), message));
    }
}
