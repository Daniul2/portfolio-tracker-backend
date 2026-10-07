package com.kodilla.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodilla.portfolio.dto.PortfolioDtos.*;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.service.PortfolioService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private PortfolioService portfolioService;
    @MockitoBean
    private PortfolioFacade portfolioFacade;

    private final PortfolioResponse portfolio = new PortfolioResponse(
            1L, 1L, "Long term", "PLN", LocalDateTime.now(), 6);

    @Test
    @DisplayName("GET /v1/portfolios lists everything when no filter is given")
    void listsAll() throws Exception {
        when(portfolioService.findAll()).thenReturn(List.of(portfolio));

        mockMvc.perform(get("/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(portfolioService).findAll();
        verify(portfolioService, never()).findByUser(any());
    }

    @Test
    @DisplayName("GET /v1/portfolios?userId= filters by user")
    void filtersByUser() throws Exception {
        when(portfolioService.findByUser(1L)).thenReturn(List.of(portfolio));

        mockMvc.perform(get("/v1/portfolios").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1));

        verify(portfolioService).findByUser(1L);
        verify(portfolioService, never()).findAll();
    }

    @Test
    @DisplayName("GET /v1/portfolios/{id}")
    void getsOne() throws Exception {
        when(portfolioService.findById(1L)).thenReturn(portfolio);

        mockMvc.perform(get("/v1/portfolios/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Long term"))
                .andExpect(jsonPath("$.transactionCount").value(6));
    }

    @Test
    @DisplayName("GET /v1/portfolios/{id}/summary returns the priced valuation")
    void getsSummary() throws Exception {
        HoldingResponse holding = new HoldingResponse(1L, "BTC", "Bitcoin",
                new BigDecimal("0.35"), new BigDecimal("46571.43"), new BigDecimal("16300.00"),
                new BigDecimal("63988.00"), new BigDecimal("22395.80"), new BigDecimal("6095.80"),
                new BigDecimal("37.40"), new BigDecimal("85018.94"), true);
        PortfolioSummaryResponse summary = new PortfolioSummaryResponse(1L, "Long term", "PLN",
                new BigDecimal("21550.00"), new BigDecimal("27153.23"), new BigDecimal("5603.22"),
                new BigDecimal("26.00"), new BigDecimal("3.7962"), new BigDecimal("103079.07"),
                List.of(holding), LocalDateTime.now());
        when(portfolioFacade.summarise(1L)).thenReturn(summary);

        mockMvc.perform(get("/v1/portfolios/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("PLN"))
                .andExpect(jsonPath("$.totalValueUsd").value(27153.23))
                .andExpect(jsonPath("$.fxRate").value(3.7962))
                .andExpect(jsonPath("$.totalValueBase").value(103079.07))
                .andExpect(jsonPath("$.holdings", hasSize(1)))
                .andExpect(jsonPath("$.holdings[0].symbol").value("BTC"))
                .andExpect(jsonPath("$.holdings[0].priced").value(true));
    }

    @Test
    @DisplayName("POST /v1/portfolios returns 201")
    void creates() throws Exception {
        when(portfolioService.create(any(PortfolioRequest.class))).thenReturn(portfolio);

        mockMvc.perform(post("/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PortfolioRequest(1L, "Long term", "PLN"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/portfolios/1"));
    }

    @Test
    @DisplayName("POST rejects a currency code that is not three letters")
    void rejectsBadCurrency() throws Exception {
        mockMvc.perform(post("/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"X\",\"baseCurrency\":\"ZLOTY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.baseCurrency").exists());
    }

    @Test
    @DisplayName("POST rejects a missing userId")
    void rejectsMissingUserId() throws Exception {
        mockMvc.perform(post("/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"baseCurrency\":\"PLN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").exists());
    }

    @Test
    @DisplayName("PUT /v1/portfolios/{id}")
    void updates() throws Exception {
        when(portfolioService.update(eq(1L), any(PortfolioRequest.class))).thenReturn(portfolio);

        mockMvc.perform(put("/v1/portfolios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PortfolioRequest(1L, "Long term", "PLN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /v1/portfolios/{id} returns 204")
    void deletes() throws Exception {
        mockMvc.perform(delete("/v1/portfolios/1"))
                .andExpect(status().isNoContent());

        verify(portfolioService).delete(1L);
    }

    @Test
    @DisplayName("a missing portfolio maps to 404")
    void missingIs404() throws Exception {
        when(portfolioService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Portfolio", 99L));

        mockMvc.perform(get("/v1/portfolios/99"))
                .andExpect(status().isNotFound());
    }
}
