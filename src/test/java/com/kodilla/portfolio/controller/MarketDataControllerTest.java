package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.AssetDtos.PriceSnapshotResponse;
import com.kodilla.portfolio.dto.CommonDtos.AuditLogResponse;
import com.kodilla.portfolio.dto.CommonDtos.ExchangeRateResponse;
import com.kodilla.portfolio.dto.DashboardDtos.DashboardResponse;
import com.kodilla.portfolio.dto.DashboardDtos.MarketRefreshResponse;
import com.kodilla.portfolio.dto.UserDtos.UserResponse;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.external.ExternalApiException;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.service.AuditService;
import com.kodilla.portfolio.service.ExchangeRateService;
import com.kodilla.portfolio.service.PriceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Covers the price, exchange-rate, dashboard and audit endpoints. */
class MarketDataControllerTest {

    @WebMvcTest(PriceController.class)
    @Nested
    class Prices {

        @Autowired
        private MockMvc mockMvc;
        @MockitoBean
        private PriceService priceService;

        private final PriceSnapshotResponse btc = new PriceSnapshotResponse(
                10L, 1L, "BTC", new BigDecimal("64000"), BigDecimal.ZERO, LocalDateTime.now());

        @Test
        @DisplayName("GET /v1/prices/latest lists the newest snapshot per asset")
        void listsLatest() throws Exception {
            when(priceService.findLatestSnapshots()).thenReturn(List.of(btc));

            mockMvc.perform(get("/v1/prices/latest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].symbol").value("BTC"))
                    .andExpect(jsonPath("$[0].priceUsd").value(64000));
        }

        @Test
        @DisplayName("GET the latest price for one asset")
        void getsLatestForAsset() throws Exception {
            when(priceService.findLatestForAsset(1L)).thenReturn(btc);

            mockMvc.perform(get("/v1/prices/assets/1/latest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assetId").value(1));
        }

        @Test
        @DisplayName("an asset never priced yields 404 rather than an empty object")
        void neverPricedIs404() throws Exception {
            when(priceService.findLatestForAsset(1L)).thenThrow(
                    new ResourceNotFoundException("No price has been recorded yet for asset 1"));

            mockMvc.perform(get("/v1/prices/assets/1/latest"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("No price")));
        }

        @Test
        @DisplayName("history defaults to 7 days and honours an explicit window")
        void getsHistory() throws Exception {
            when(priceService.findHistory(eq(1L), anyInt())).thenReturn(List.of(btc));

            mockMvc.perform(get("/v1/prices/assets/1/history"))
                    .andExpect(status().isOk());
            verify(priceService).findHistory(1L, 7);

            mockMvc.perform(get("/v1/prices/assets/1/history").param("days", "30"))
                    .andExpect(status().isOk());
            verify(priceService).findHistory(1L, 30);
        }

        @Test
        @DisplayName("POST /v1/prices/refresh reports how many snapshots were saved")
        void refreshes() throws Exception {
            when(priceService.refreshPrices()).thenReturn(1);

            mockMvc.perform(post("/v1/prices/refresh"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provider").value("CoinGecko"))
                    .andExpect(jsonPath("$.recordsSaved").value(1));
        }

        @Test
        @DisplayName("a provider outage maps to 503, not 500")
        void providerOutageIs503() throws Exception {
            when(priceService.refreshPrices())
                    .thenThrow(new ExternalApiException("CoinGecko", "connection timed out"));

            mockMvc.perform(post("/v1/prices/refresh"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.message").value(containsString("CoinGecko")));
        }

        @Test
        @DisplayName("DELETE /v1/prices/history trims stored snapshots")
        void purgesHistory() throws Exception {
            when(priceService.purgeOlderThan(30)).thenReturn(12L);

            mockMvc.perform(delete("/v1/prices/history").param("olderThanDays", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recordsSaved").value(12));
        }

        @Test
        @DisplayName("without a window the purge uses the configured retention, not a hard-coded one")
        void purgeDefaultsToConfiguredRetention() throws Exception {
            when(priceService.purgeOlderThan(90)).thenReturn(0L);

            mockMvc.perform(delete("/v1/prices/history"))
                    .andExpect(status().isOk());

            // 90 comes from app.scheduler.price-retention-days in the test profile.
            verify(priceService).purgeOlderThan(90);
        }
    }

    @WebMvcTest(ExchangeRateController.class)
    @Nested
    class Rates {

        @Autowired
        private MockMvc mockMvc;
        @MockitoBean
        private ExchangeRateService exchangeRateService;

        private final ExchangeRateResponse usd = new ExchangeRateResponse(
                1L, "USD", new BigDecimal("3.7962"), LocalDate.now(), LocalDateTime.now());

        @Test
        @DisplayName("GET /v1/rates lists stored rates")
        void listsRates() throws Exception {
            when(exchangeRateService.findAll()).thenReturn(List.of(usd));

            mockMvc.perform(get("/v1/rates"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].currencyCode").value("USD"))
                    .andExpect(jsonPath("$[0].ratePln").value(3.7962));
        }

        @Test
        @DisplayName("GET /v1/rates/{code} returns the newest rate")
        void getsOneRate() throws Exception {
            when(exchangeRateService.requireLatest("USD")).thenReturn(usd);

            mockMvc.perform(get("/v1/rates/USD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currencyCode").value("USD"));
        }

        @Test
        @DisplayName("an unknown currency is a 404")
        void unknownCurrencyIs404() throws Exception {
            when(exchangeRateService.requireLatest("XYZ"))
                    .thenThrow(new ResourceNotFoundException("No exchange rate stored for currency XYZ"));

            mockMvc.perform(get("/v1/rates/XYZ"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /v1/rates/convert defaults to PLN")
        void convertsToPlnByDefault() throws Exception {
            when(exchangeRateService.usdToCurrencyRate("PLN"))
                    .thenReturn(Optional.of(new BigDecimal("3.7962")));

            mockMvc.perform(get("/v1/rates/convert"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value("USD"))
                    .andExpect(jsonPath("$.to").value("PLN"))
                    .andExpect(jsonPath("$.rate").value(3.7962));
        }

        @Test
        @DisplayName("GET /v1/rates/convert?to= upper-cases the target")
        void convertsToRequestedCurrency() throws Exception {
            when(exchangeRateService.usdToCurrencyRate("eur"))
                    .thenReturn(Optional.of(new BigDecimal("0.877")));

            mockMvc.perform(get("/v1/rates/convert").param("to", "eur"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.to").value("EUR"));
        }

        @Test
        @DisplayName("no available rate is a 404 rather than a wrong number")
        void missingRateIs404() throws Exception {
            when(exchangeRateService.usdToCurrencyRate("GBP")).thenReturn(Optional.empty());

            mockMvc.perform(get("/v1/rates/convert").param("to", "GBP"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST /v1/rates/refresh reports how many rates were stored")
        void refreshes() throws Exception {
            when(exchangeRateService.refreshRates()).thenReturn(1);

            mockMvc.perform(post("/v1/rates/refresh"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provider").value("NBP"))
                    .andExpect(jsonPath("$.recordsSaved").value(1));
        }
    }

    @WebMvcTest(DashboardController.class)
    @Nested
    class Dashboard {

        @Autowired
        private MockMvc mockMvc;
        @MockitoBean
        private PortfolioFacade portfolioFacade;

        @Test
        @DisplayName("GET /v1/dashboard/{userId} assembles the whole screen")
        void getsDashboard() throws Exception {
            DashboardResponse dashboard = new DashboardResponse(
                    new UserResponse(1L, "demo", "demo@example.com", "Demo", LocalDateTime.now(), 2),
                    List.of(), new BigDecimal("27153.23"), List.of(), LocalDateTime.now());
            when(portfolioFacade.dashboardFor(1L)).thenReturn(dashboard);

            mockMvc.perform(get("/v1/dashboard/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.username").value("demo"))
                    .andExpect(jsonPath("$.combinedValueUsd").value(27153.23));
        }

        @Test
        @DisplayName("POST /v1/dashboard/refresh reports both sources and alerts")
        void refreshesEverything() throws Exception {
            when(portfolioFacade.refreshMarketData()).thenReturn(
                    new MarketRefreshResponse(4, 32, 1, List.of(), LocalDateTime.now()));

            mockMvc.perform(post("/v1/dashboard/refresh"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pricesSaved").value(4))
                    .andExpect(jsonPath("$.ratesSaved").value(32))
                    .andExpect(jsonPath("$.alertsTriggered").value(1))
                    .andExpect(jsonPath("$.failures", hasSize(0)));
        }

        @Test
        @DisplayName("a partial failure is reported in the body, not as an error status")
        void reportsPartialFailure() throws Exception {
            when(portfolioFacade.refreshMarketData()).thenReturn(new MarketRefreshResponse(
                    0, 32, 0, List.of("CoinGecko: timed out"), LocalDateTime.now()));

            mockMvc.perform(post("/v1/dashboard/refresh"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ratesSaved").value(32))
                    .andExpect(jsonPath("$.failures", hasSize(1)));
        }

        @Test
        @DisplayName("an unknown user is a 404")
        void unknownUserIs404() throws Exception {
            when(portfolioFacade.dashboardFor(99L))
                    .thenThrow(new ResourceNotFoundException("User", 99L));

            mockMvc.perform(get("/v1/dashboard/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @WebMvcTest(AuditController.class)
    @Nested
    class Audit {

        @Autowired
        private MockMvc mockMvc;
        @MockitoBean
        private AuditService auditService;

        @Test
        @DisplayName("GET /v1/audit lists recent state changes")
        void listsRecent() throws Exception {
            when(auditService.findRecent())
                    .thenReturn(List.of(new AuditLogResponse(
                            1L, "USER_CREATED", "User", 1L, "username=demo", LocalDateTime.now())));

            mockMvc.perform(get("/v1/audit"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].action").value("USER_CREATED"))
                    .andExpect(jsonPath("$[0].details").value("username=demo"));
        }

        @Test
        @DisplayName("GET /v1/audit/by-type/{entityType} filters the trail")
        void filtersByType() throws Exception {
            when(auditService.findByEntityType("Alert"))
                    .thenReturn(List.of(new AuditLogResponse(
                            2L, "ALERT_TRIGGERED", "Alert", 4L, "fired", LocalDateTime.now())));

            mockMvc.perform(get("/v1/audit/by-type/Alert"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].entityType").value("Alert"));
        }
    }
}
