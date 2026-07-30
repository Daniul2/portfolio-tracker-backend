package com.kodilla.portfolio.external;

import java.math.BigDecimal;

/** Provider-neutral price quote. */
public record CryptoQuote(String externalId, BigDecimal priceUsd, BigDecimal change24hPercent) {
}
