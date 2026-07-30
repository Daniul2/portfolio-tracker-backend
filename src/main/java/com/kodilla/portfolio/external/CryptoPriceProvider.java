package com.kodilla.portfolio.external;

import java.util.Collection;
import java.util.Map;

/** Abstraction over whichever service supplies crypto prices. */
public interface CryptoPriceProvider {

    /** Returns quotes keyed by external id; ids the provider does not know are omitted. */
    Map<String, CryptoQuote> fetchPrices(Collection<String> externalIds);

    /** Name used in logs and audit entries. */
    String providerName();
}
