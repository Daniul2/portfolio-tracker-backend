package com.kodilla.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodilla.portfolio.domain.TransactionType;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioSummaryResponse;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionRequest;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.service.TransactionService;
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

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private TransactionService transactionService;
    @MockitoBean
    private PortfolioFacade portfolioFacade;

    private final TransactionResponse transaction = new TransactionResponse(1L, 1L, 1L, "BTC",
            TransactionType.BUY, new BigDecimal("0.25"), new BigDecimal("42000"),
            BigDecimal.ZERO, new BigDecimal("10500"), LocalDateTime.now(), "first buy");

    private TransactionRequest validRequest() {
        return new TransactionRequest(1L, 1L, TransactionType.BUY, new BigDecimal("0.25"),
                new BigDecimal("42000"), BigDecimal.ZERO, LocalDateTime.now().minusDays(1), "note");
    }

    @Test
    @DisplayName("GET /v1/transactions?portfolioId= lists a portfolio's ledger")
    void listsByPortfolio() throws Exception {
        when(transactionService.findByPortfolio(1L)).thenReturn(List.of(transaction));

        mockMvc.perform(get("/v1/transactions").param("portfolioId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].grossValueUsd").value(10500));
    }

    @Test
    @DisplayName("GET /v1/transactions without portfolioId returns 400")
    void requiresPortfolioId() throws Exception {
        mockMvc.perform(get("/v1/transactions"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/transactions/{id}")
    void getsOne() throws Exception {
        when(transactionService.findById(1L)).thenReturn(transaction);

        mockMvc.perform(get("/v1/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("first buy"));
    }

    @Test
    @DisplayName("POST /v1/transactions returns 201")
    void creates() throws Exception {
        when(transactionService.create(any(TransactionRequest.class))).thenReturn(transaction);

        mockMvc.perform(post("/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/transactions/1"));
    }

    @Test
    @DisplayName("POST /v1/transactions/with-summary returns the repriced portfolio")
    void createsAndSummarises() throws Exception {
        PortfolioSummaryResponse summary = new PortfolioSummaryResponse(1L, "Long term", "PLN",
                new BigDecimal("10500.00"), new BigDecimal("12000.00"), new BigDecimal("1500.00"),
                new BigDecimal("14.29"), new BigDecimal("3.7962"), new BigDecimal("45554.40"),
                List.of(), LocalDateTime.now());
        when(portfolioFacade.recordTransactionAndRevalue(any(TransactionRequest.class)))
                .thenReturn(summary);

        mockMvc.perform(post("/v1/transactions/with-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValueUsd").value(12000.00));
    }

    @Test
    @DisplayName("POST rejects a zero or negative quantity")
    void rejectsNonPositiveQuantity() throws Exception {
        String body = """
                {"portfolioId":1,"assetId":1,"type":"BUY","quantity":0,"pricePerUnitUsd":42000}
                """;

        mockMvc.perform(post("/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    @Test
    @DisplayName("POST rejects a negative fee")
    void rejectsNegativeFee() throws Exception {
        String body = """
                {"portfolioId":1,"assetId":1,"type":"BUY","quantity":1,
                 "pricePerUnitUsd":42000,"feeUsd":-5}
                """;

        mockMvc.perform(post("/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeUsd").exists());
    }

    @Test
    @DisplayName("POST rejects a missing transaction type")
    void rejectsMissingType() throws Exception {
        String body = """
                {"portfolioId":1,"assetId":1,"quantity":1,"pricePerUnitUsd":42000}
                """;

        mockMvc.perform(post("/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
    }

    @Test
    @DisplayName("an oversized sell maps to 422 explaining what is held")
    void oversizedSellIs422() throws Exception {
        when(transactionService.create(any(TransactionRequest.class))).thenThrow(
                new BusinessRuleException("Cannot sell 99 BTC: portfolio only holds 0.35000000"));

        mockMvc.perform(post("/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("only holds")));
    }

    @Test
    @DisplayName("PUT /v1/transactions/{id}")
    void updates() throws Exception {
        when(transactionService.update(eq(1L), any(TransactionRequest.class))).thenReturn(transaction);

        mockMvc.perform(put("/v1/transactions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /v1/transactions/{id} returns 204")
    void deletes() throws Exception {
        mockMvc.perform(delete("/v1/transactions/1"))
                .andExpect(status().isNoContent());

        verify(transactionService).delete(1L);
    }
}
