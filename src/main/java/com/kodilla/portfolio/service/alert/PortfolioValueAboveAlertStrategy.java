package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.AlertType;
import org.springframework.stereotype.Component;

/** Fires when the whole portfolio's USD value rises strictly above the threshold. */
@Component
public class PortfolioValueAboveAlertStrategy extends PortfolioValueThresholdAlertStrategy {

    public PortfolioValueAboveAlertStrategy() {
        super(AlertType.PORTFOLIO_VALUE_ABOVE, Direction.ABOVE);
    }
}
