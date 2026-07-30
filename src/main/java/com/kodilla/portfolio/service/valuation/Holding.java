package com.kodilla.portfolio.service.valuation;

import com.kodilla.portfolio.domain.Asset;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A net position in one asset, derived from the transaction ledger rather than stored. */
public record Holding(Asset asset, BigDecimal quantity, BigDecimal costBasisUsd) {

    /** Average price paid per unit still held; zero when nothing is held. */
    public BigDecimal averageCostUsd() {
        if (quantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return costBasisUsd.divide(quantity, 8, RoundingMode.HALF_UP);
    }
}
