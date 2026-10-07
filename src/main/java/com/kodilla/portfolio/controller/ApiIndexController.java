package com.kodilla.portfolio.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Describes the API at its root. */
@RestController
public class ApiIndexController {

    private final String applicationName;

    public ApiIndexController(
            @Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/")
    public Map<String, Object> index() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", applicationName);
        body.put("status", "running");
        body.put("description", "REST API for tracking a cryptocurrency investment portfolio. "
                + "Prices come from CoinGecko, currency rates from NBP.");
        body.put("documentation", "See README.md and DOCUMENTATION.md in the repository");
        body.put("frontend", "The Vaadin user interface runs separately, by default on port 8081");
        body.put("endpoints", endpointGroups());
        return body;
    }

    private Map<String, Object> endpointGroups() {
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("users", "/v1/users");
        groups.put("portfolios", "/v1/portfolios");
        groups.put("portfolioValuation", "/v1/portfolios/{id}/summary");
        groups.put("assets", "/v1/assets");
        groups.put("transactions", "/v1/transactions?portfolioId={id}");
        groups.put("alerts", "/v1/alerts");
        groups.put("prices", "/v1/prices/latest");
        groups.put("exchangeRates", "/v1/rates");
        groups.put("dashboard", "/v1/dashboard/{userId}");
        groups.put("auditTrail", "/v1/audit");
        groups.put("refreshEverything", "POST /v1/dashboard/refresh");
        groups.put("gettingStarted", List.of(
                "POST /v1/dashboard/refresh   - pull live prices and exchange rates",
                "GET  /v1/portfolios/1/summary - see the demo portfolio priced up"));
        return groups;
    }
}
