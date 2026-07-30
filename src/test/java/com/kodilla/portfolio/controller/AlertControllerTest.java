package com.kodilla.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodilla.portfolio.domain.AlertType;
import com.kodilla.portfolio.dto.AlertDtos.AlertEventResponse;
import com.kodilla.portfolio.dto.AlertDtos.AlertRequest;
import com.kodilla.portfolio.dto.AlertDtos.AlertResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.service.AlertService;
import com.kodilla.portfolio.service.alert.AlertEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AlertService alertService;
    @MockitoBean
    private AlertEvaluationService alertEvaluationService;

    private final AlertResponse alert = new AlertResponse(1L, 1L, 1L, "BTC",
            AlertType.PRICE_ABOVE, new BigDecimal("70000"), true, LocalDateTime.now(), null);

    private final AlertEventResponse event = new AlertEventResponse(1L, 1L, 1L,
            "BTC rose above 70000 USD", new BigDecimal("71000"), false, LocalDateTime.now());

    @Test
    @DisplayName("GET /v1/alerts lists all by default")
    void listsAll() throws Exception {
        when(alertService.findAll()).thenReturn(List.of(alert));

        mockMvc.perform(get("/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("PRICE_ABOVE"));

        verify(alertService).findAll();
    }

    @Test
    @DisplayName("GET /v1/alerts?portfolioId= filters by portfolio")
    void filtersByPortfolio() throws Exception {
        when(alertService.findByPortfolio(1L)).thenReturn(List.of(alert));

        mockMvc.perform(get("/v1/alerts").param("portfolioId", "1"))
                .andExpect(status().isOk());

        verify(alertService).findByPortfolio(1L);
        verify(alertService, never()).findAll();
    }

    @Test
    @DisplayName("GET /v1/alerts/{id}")
    void getsOne() throws Exception {
        when(alertService.findById(1L)).thenReturn(alert);

        mockMvc.perform(get("/v1/alerts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC"));
    }

    @Test
    @DisplayName("GET /v1/alerts/events?portfolioId= lists fired alerts")
    void listsEvents() throws Exception {
        when(alertService.findEventsByPortfolio(1L)).thenReturn(List.of(event));

        mockMvc.perform(get("/v1/alerts/events").param("portfolioId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value(containsString("rose above")));
    }

    @Test
    @DisplayName("GET /v1/alerts/events/unacknowledged")
    void listsUnacknowledgedEvents() throws Exception {
        when(alertService.findUnacknowledgedEvents()).thenReturn(List.of(event));

        mockMvc.perform(get("/v1/alerts/events/unacknowledged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].acknowledged").value(false));
    }

    @Test
    @DisplayName("POST /v1/alerts returns 201")
    void creates() throws Exception {
        when(alertService.create(any(AlertRequest.class))).thenReturn(alert);

        mockMvc.perform(post("/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AlertRequest(
                                1L, 1L, AlertType.PRICE_ABOVE, new BigDecimal("70000"), null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/alerts/1"));
    }

    @Test
    @DisplayName("POST rejects a zero threshold")
    void rejectsZeroThreshold() throws Exception {
        mockMvc.perform(post("/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portfolioId\":1,\"type\":\"PRICE_ABOVE\",\"threshold\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.threshold").exists());
    }

    @Test
    @DisplayName("POST rejects an unknown alert type as a bad request")
    void rejectsUnknownType() throws Exception {
        mockMvc.perform(post("/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portfolioId\":1,\"type\":\"NOT_A_TYPE\",\"threshold\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a price alert with no asset maps to 422")
    void priceAlertWithoutAssetIs422() throws Exception {
        when(alertService.create(any(AlertRequest.class))).thenThrow(
                new BusinessRuleException("assetId is required for alert type PRICE_ABOVE"));

        mockMvc.perform(post("/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portfolioId\":1,\"type\":\"PRICE_ABOVE\",\"threshold\":70000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("assetId is required")));
    }

    @Test
    @DisplayName("POST /v1/alerts/evaluate reports how many fired")
    void evaluatesNow() throws Exception {
        when(alertEvaluationService.evaluateAll()).thenReturn(3);

        mockMvc.perform(post("/v1/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("alertsTriggered"))
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @DisplayName("PUT /v1/alerts/{id}")
    void updates() throws Exception {
        when(alertService.update(eq(1L), any(AlertRequest.class))).thenReturn(alert);

        mockMvc.perform(put("/v1/alerts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AlertRequest(
                                1L, 1L, AlertType.PRICE_ABOVE, new BigDecimal("80000"), true))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/alerts/events/{id}/acknowledge marks an event seen")
    void acknowledgesEvent() throws Exception {
        AlertEventResponse acknowledged = new AlertEventResponse(1L, 1L, 1L,
                "BTC rose above 70000 USD", new BigDecimal("71000"), true, LocalDateTime.now());
        when(alertService.acknowledgeEvent(1L)).thenReturn(acknowledged);

        mockMvc.perform(put("/v1/alerts/events/1/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(true));
    }

    @Test
    @DisplayName("DELETE /v1/alerts/{id} returns 204")
    void deletes() throws Exception {
        mockMvc.perform(delete("/v1/alerts/1"))
                .andExpect(status().isNoContent());

        verify(alertService).delete(1L);
    }
}
