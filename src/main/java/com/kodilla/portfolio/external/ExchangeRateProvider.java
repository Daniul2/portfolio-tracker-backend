package com.kodilla.portfolio.external;

import java.util.List;

/** Abstraction over whichever service supplies currency rates. */
public interface ExchangeRateProvider {

    /** Returns every rate published in the provider's latest table. */
    List<RateQuote> fetchLatestRates();

    String providerName();
}
