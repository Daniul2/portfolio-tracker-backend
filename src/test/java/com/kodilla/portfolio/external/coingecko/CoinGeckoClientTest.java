package com.kodilla.portfolio.external.coingecko;

import com.kodilla.portfolio.external.CryptoQuote;
import com.kodilla.portfolio.external.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;

/** Exercises the CoinGecko adapter against a stubbed HTTP server. */
class CoinGeckoClientTest {

    private MockRestServiceServer server;
    private CoinGeckoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.coingecko.test/api/v3");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CoinGeckoClient(builder.build());
    }

    @Test
    @DisplayName("maps the nested response into flat quotes")
    void parsesQuotes() {
        String body = """
                {"bitcoin":{"usd":64009.5,"usd_24h_change":0.58},
                 "ethereum":{"usd":1905.52,"usd_24h_change":-0.09}}
                """;
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/simple/price")))
                .andExpect(queryParam("ids", "bitcoin,ethereum"))
                .andExpect(queryParam("vs_currencies", "usd"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Map<String, CryptoQuote> quotes = client.fetchPrices(List.of("bitcoin", "ethereum"));

        server.verify();
        assertThat(quotes).hasSize(2);
        assertThat(quotes.get("bitcoin").priceUsd()).isEqualByComparingTo("64009.5");
        assertThat(quotes.get("bitcoin").change24hPercent()).isEqualByComparingTo("0.58");
        assertThat(quotes.get("ethereum").priceUsd()).isEqualByComparingTo("1905.52");
        assertThat(quotes.get("ethereum").change24hPercent()).isEqualByComparingTo("-0.09");
    }

    @Test
    @DisplayName("keeps a quote whose 24h change is missing")
    void toleratesMissingChange() {
        server.expect(anything())
                .andRespond(withSuccess("{\"bitcoin\":{\"usd\":50000}}", MediaType.APPLICATION_JSON));

        Map<String, CryptoQuote> quotes = client.fetchPrices(List.of("bitcoin"));

        assertThat(quotes).hasSize(1);
        assertThat(quotes.get("bitcoin").priceUsd()).isEqualByComparingTo("50000");
        assertThat(quotes.get("bitcoin").change24hPercent()).isNull();
    }

    @Test
    @DisplayName("skips an entry with no USD price rather than recording zero")
    void skipsEntryWithoutPrice() {
        String body = "{\"bitcoin\":{\"usd\":50000},\"mystery-coin\":{\"eur\":10}}";
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Map<String, CryptoQuote> quotes = client.fetchPrices(List.of("bitcoin", "mystery-coin"));

        assertThat(quotes).containsOnlyKeys("bitcoin");
    }

    @Test
    @DisplayName("an unknown id simply does not appear in the result")
    void omitsUnknownIds() {
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchPrices(List.of("not-a-coin"))).isEmpty();
    }

    @Test
    @DisplayName("does not call the API at all for an empty or null id list")
    void shortCircuitsEmptyInput() {
        // No server.expect(...) is registered, so any HTTP call would fail the test.
        assertThat(client.fetchPrices(List.of())).isEmpty();
        assertThat(client.fetchPrices(null)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("wraps a server error in ExternalApiException naming the provider")
    void wrapsServerError() {
        server.expect(anything()).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchPrices(List.of("bitcoin")))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("bitcoin")
                .extracting(e -> ((ExternalApiException) e).getProvider())
                .isEqualTo("CoinGecko");
    }

    @Test
    @DisplayName("treats a rate-limit response as a provider failure, not empty data")
    void wrapsRateLimit() {
        server.expect(anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchPrices(List.of("bitcoin")))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void reportsItsProviderName() {
        assertThat(client.providerName()).isEqualTo("CoinGecko");
    }
}
