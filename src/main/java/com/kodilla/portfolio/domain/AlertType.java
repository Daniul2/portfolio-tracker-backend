package com.kodilla.portfolio.domain;

/**
 * The kinds of user-defined threshold an alert can watch. Each value is backed
 * by its own evaluation strategy (see the {@code service.alert} package).
 */
public enum AlertType {
    /** Fires when the asset's USD price rises above the threshold. */
    PRICE_ABOVE,
    /** Fires when the asset's USD price falls below the threshold. */
    PRICE_BELOW,
    /** Fires when total portfolio value rises above the threshold. */
    PORTFOLIO_VALUE_ABOVE,
    /** Fires when total portfolio value falls below the threshold. */
    PORTFOLIO_VALUE_BELOW
}
