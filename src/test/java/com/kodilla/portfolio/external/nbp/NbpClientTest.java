package com.kodilla.portfolio.external.nbp;

import com.kodilla.portfolio.external.ExternalApiException;
import com.kodilla.portfolio.external.RateQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Exercises the NBP adapter against a stubbed HTTP server. */
class NbpClientTest {

    private MockRestServiceServer server;
    private NbpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.nbp.test/api");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NbpClient(builder.build());
    }

    private static final String TABLE = """
            [{"table":"A","no":"145/A/NBP/2026","effectiveDate":"2026-07-29","rates":[
              {"currency":"dolar ameryka\\u0144ski","code":"USD","mid":3.7962},
              {"currency":"euro","code":"EUR","mid":4.3262},
              {"currency":"frank szwajcarski","code":"CHF","mid":4.6510}]}]
            """;

    @Test
    @DisplayName("maps the NBP table into rate quotes")
    void parsesRates() {
        server.expect(anything()).andRespond(withSuccess(TABLE, MediaType.APPLICATION_JSON));

        List<RateQuote> rates = client.fetchLatestRates();

        server.verify();
        assertThat(rates).hasSize(3);
        assertThat(rates).extracting(RateQuote::currencyCode).containsExactly("USD", "EUR", "CHF");
        assertThat(rates.get(0).ratePln()).isEqualByComparingTo("3.7962");
        assertThat(rates.get(0).effectiveDate()).isEqualTo(LocalDate.of(2026, 7, 29));
    }

    @Test
    @DisplayName("skips rate entries missing a code or a value")
    void skipsIncompleteRates() {
        String body = """
                [{"table":"A","effectiveDate":"2026-07-29","rates":[
                  {"currency":"good","code":"USD","mid":3.79},
                  {"currency":"no code","mid":1.23},
                  {"currency":"no mid","code":"XXX"}]}]
                """;
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<RateQuote> rates = client.fetchLatestRates();

        assertThat(rates).extracting(RateQuote::currencyCode).containsExactly("USD");
    }

    @Test
    @DisplayName("ignores unknown fields the provider might add")
    void ignoresUnknownFields() {
        String body = """
                [{"table":"A","effectiveDate":"2026-07-29","somethingNew":true,"rates":[
                  {"currency":"dolar","code":"USD","mid":3.79,"extraField":"ignored"}]}]
                """;
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.fetchLatestRates()).hasSize(1);
    }

    @Test
    @DisplayName("an empty table array is a provider failure, not silently zero rates")
    void rejectsEmptyTableList() {
        server.expect(anything()).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchLatestRates())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("no exchange rate tables");
    }

    @Test
    @DisplayName("a table with a null rates array is a provider failure")
    void rejectsNullRates() {
        server.expect(anything())
                .andRespond(withSuccess("[{\"table\":\"A\",\"effectiveDate\":\"2026-07-29\"}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchLatestRates())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("no rates");
    }

    @Test
    @DisplayName("wraps a transport failure in ExternalApiException naming the provider")
    void wrapsServerError() {
        server.expect(anything()).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchLatestRates())
                .isInstanceOf(ExternalApiException.class)
                .extracting(e -> ((ExternalApiException) e).getProvider())
                .isEqualTo("NBP");
    }

    @Test
    void reportsItsProviderName() {
        assertThat(client.providerName()).isEqualTo("NBP");
    }
}
