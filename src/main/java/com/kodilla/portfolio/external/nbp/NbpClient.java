package com.kodilla.portfolio.external.nbp;

import com.kodilla.portfolio.external.ExchangeRateProvider;
import com.kodilla.portfolio.external.ExternalApiException;
import com.kodilla.portfolio.external.RateQuote;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

/** Reads the daily average ("mid") currency table published by NBP, the Polish National Bank. */
@Component
public class NbpClient implements ExchangeRateProvider {

    private static final String PROVIDER = "NBP";

    private final RestClient restClient;

    public NbpClient(@Qualifier("nbpRestClient") RestClient nbpRestClient) {
        this.restClient = nbpRestClient;
    }

    @Override
    public List<RateQuote> fetchLatestRates() {
        try {
            List<NbpTableResponse> tables = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/exchangerates/tables/A")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (tables == null || tables.isEmpty()) {
                throw new ExternalApiException(PROVIDER, "NBP returned no exchange rate tables");
            }

            NbpTableResponse table = tables.get(0);
            if (table.rates() == null) {
                throw new ExternalApiException(PROVIDER, "NBP table contained no rates");
            }

            return table.rates().stream()
                    .filter(rate -> rate.code() != null && rate.mid() != null)
                    .map(rate -> new RateQuote(rate.code(), rate.mid(), table.effectiveDate()))
                    .filter(quote -> Objects.nonNull(quote.effectiveDate()))
                    .toList();
        } catch (RestClientException e) {
            throw new ExternalApiException(PROVIDER, "Failed to fetch NBP exchange rates", e);
        }
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }
}
