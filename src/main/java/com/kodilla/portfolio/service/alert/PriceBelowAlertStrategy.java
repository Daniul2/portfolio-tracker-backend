package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.AlertType;
import org.springframework.stereotype.Component;

/** Fires when an asset's USD price falls strictly below the threshold. */
@Component
public class PriceBelowAlertStrategy extends PriceThresholdAlertStrategy {

    public PriceBelowAlertStrategy() {
        super(AlertType.PRICE_BELOW, Direction.BELOW);
    }
}
