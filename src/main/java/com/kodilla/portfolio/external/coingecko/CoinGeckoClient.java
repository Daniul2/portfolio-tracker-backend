package com.kodilla.portfolio.external.coingecko;

import com.kodilla.portfolio.external.CryptoPriceProvider;
import com.kodilla.portfolio.external.CryptoQuote;
import com.kodilla.portfolio.external.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Reads spot prices from CoinGecko's public API (no API key required). */
@Component
public class CoinGeckoClient implements CryptoPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);
    private static final String PROVIDER = "CoinGecko";
    private static final String PRICE_KEY = "usd";
    private static final String CHANGE_KEY = "usd_24h_change";

    private final RestClient restClient;

    public CoinGeckoClient(@Qualifier("coinGeckoRestClient") RestClient coinGeckoRestClient) {
        this.restClient = coinGeckoRestClient;
    }

    @Override
    public Map<String, CryptoQuote> fetchPrices(Collection<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty()) {
            return Map.of();
        }
        String ids = String.join(",", externalIds);
        try {
            Map<String, Map<String, BigDecimal>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/simple/price")
                            .queryParam("ids", ids)
                            .queryParam("vs_currencies", PRICE_KEY)
                            .queryParam("include_24hr_change", "true")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null) {
                throw new ExternalApiException(PROVIDER, "Empty response body from /simple/price");
            }
            return toQuotes(response);
        } catch (RestClientException e) {
            throw new ExternalApiException(PROVIDER, "Failed to fetch prices for [" + ids + "]", e);
        }
    }

    private Map<String, CryptoQuote> toQuotes(Map<String, Map<String, BigDecimal>> response) {
        Map<String, CryptoQuote> quotes = new HashMap<>();
        response.forEach((externalId, values) -> {
            BigDecimal price = values.get(PRICE_KEY);
            if (price == null) {
                // The provider knows the id but had no USD price for it; skip
                // rather than storing a bogus zero.
                log.warn("CoinGecko returned no USD price for '{}', skipping", externalId);
                return;
            }
            quotes.put(externalId, new CryptoQuote(externalId, price, values.get(CHANGE_KEY)));
        });
        return quotes;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }
}
