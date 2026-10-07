package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;

import java.math.BigDecimal;
import java.util.Optional;

/** Watches one asset's USD price. */
public abstract class PriceThresholdAlertStrategy extends ThresholdAlertStrategy {

    protected PriceThresholdAlertStrategy(AlertType type, Direction direction) {
        super(type, direction);
    }

    @Override
    public boolean requiresAsset() {
        return true;
    }

    @Override
    protected Optional<BigDecimal> observe(Alert alert, AlertContext context) {
        return context.priceOf(alert.getAsset().getId());
    }

    @Override
    protected String subject(Alert alert) {
        return alert.getAsset().getSymbol();
    }
}
