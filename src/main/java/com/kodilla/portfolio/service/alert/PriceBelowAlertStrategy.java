package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** Fires when an asset's USD price falls strictly below the threshold. */
@Component
public class PriceBelowAlertStrategy implements AlertStrategy {

    @Override
    public AlertType supportedType() {
        return AlertType.PRICE_BELOW;
    }

    @Override
    public Optional<AlertTrigger> evaluate(Alert alert, AlertContext context) {
        if (alert.getAsset() == null) {
            return Optional.empty();
        }
        Optional<BigDecimal> price = context.priceOf(alert.getAsset().getId());
        if (price.isEmpty() || price.get().compareTo(alert.getThreshold()) >= 0) {
            return Optional.empty();
        }
        String message = "%s fell below %s USD (now %s USD)".formatted(
                alert.getAsset().getSymbol(), alert.getThreshold().toPlainString(),
                price.get().toPlainString());
        return Optional.of(new AlertTrigger(alert, price.get(), message));
    }
}
