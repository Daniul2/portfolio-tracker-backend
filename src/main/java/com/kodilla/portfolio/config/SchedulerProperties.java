package com.kodilla.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code app.scheduler.*} entries from application.properties. */
@ConfigurationProperties(prefix = "app.scheduler")
public record SchedulerProperties(boolean enabled, long priceRefreshMs, long rateRefreshMs) {
}
