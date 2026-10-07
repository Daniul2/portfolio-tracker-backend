package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.AlertType;
import org.springframework.stereotype.Component;

/** Fires when the whole portfolio's USD value falls strictly below the threshold. */
@Component
public class PortfolioValueBelowAlertStrategy extends PortfolioValueThresholdAlertStrategy {

    public PortfolioValueBelowAlertStrategy() {
        super(AlertType.PORTFOLIO_VALUE_BELOW, Direction.BELOW);
    }
}
