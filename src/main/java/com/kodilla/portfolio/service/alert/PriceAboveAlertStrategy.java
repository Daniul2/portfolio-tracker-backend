package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.AlertType;
import org.springframework.stereotype.Component;

/** Fires when an asset's USD price rises strictly above the threshold. */
@Component
public class PriceAboveAlertStrategy extends PriceThresholdAlertStrategy {

    public PriceAboveAlertStrategy() {
        super(AlertType.PRICE_ABOVE, Direction.ABOVE);
    }
}
