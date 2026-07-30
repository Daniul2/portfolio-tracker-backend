package com.kodilla.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code app.external.*} entries from application.properties. */
@ConfigurationProperties(prefix = "app.external")
public record ExternalApiProperties(ApiConfig coingecko, ApiConfig nbp) {

    public record ApiConfig(String baseUrl, int timeoutSeconds) {
    }
}
