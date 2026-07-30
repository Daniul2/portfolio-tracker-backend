package com.kodilla.portfolio.service.valuation;

import java.math.BigDecimal;

/** One holding priced at the latest known market price. */
public record HoldingValuation(
        Holding holding,
        BigDecimal currentPriceUsd,
        BigDecimal marketValueUsd,
        BigDecimal unrealizedPnlUsd,
        BigDecimal unrealizedPnlPercent,
        BigDecimal marketValueBase) {

    /** True when no price could be found, so the figures are cost-basis only. */
    public boolean isPriced() {
        return currentPriceUsd != null;
    }
}
