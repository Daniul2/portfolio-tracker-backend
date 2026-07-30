package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Portfolio;

import java.math.BigDecimal;
import java.util.Optional;

/** The market data a strategy is allowed to look at. */
public interface AlertContext {

    /** Latest known USD price for an asset, empty if it has never been priced. */
    Optional<BigDecimal> priceOf(Long assetId);

    /** Current total market value of a portfolio in USD. */
    Optional<BigDecimal> portfolioValueUsd(Portfolio portfolio);
}
