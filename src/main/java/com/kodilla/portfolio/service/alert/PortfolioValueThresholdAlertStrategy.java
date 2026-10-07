package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;

import java.math.BigDecimal;
import java.util.Optional;

/** Watches the total USD value of a whole portfolio. */
public abstract class PortfolioValueThresholdAlertStrategy extends ThresholdAlertStrategy {

    protected PortfolioValueThresholdAlertStrategy(AlertType type, Direction direction) {
        super(type, direction);
    }

    @Override
    protected Optional<BigDecimal> observe(Alert alert, AlertContext context) {
        return context.portfolioValueUsd(alert.getPortfolio());
    }

    @Override
    protected String subject(Alert alert) {
        return "Portfolio '" + alert.getPortfolio().getName() + "'";
    }
}
