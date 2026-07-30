package com.kodilla.portfolio.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * One {@link RestClient} per external provider. Timeouts are set explicitly so a
 * slow third-party API can never hang a scheduler thread indefinitely.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient coinGeckoRestClient(ExternalApiProperties properties) {
        return build(properties.coingecko());
    }

    @Bean
    public RestClient nbpRestClient(ExternalApiProperties properties) {
        return build(properties.nbp());
    }

    private RestClient build(ExternalApiProperties.ApiConfig config) {
        Duration timeout = Duration.ofSeconds(config.timeoutSeconds());
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(timeout)
                .withReadTimeout(timeout);
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
