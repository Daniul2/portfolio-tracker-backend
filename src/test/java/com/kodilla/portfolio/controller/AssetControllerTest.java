package com.kodilla.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodilla.portfolio.dto.AssetDtos.AssetRequest;
import com.kodilla.portfolio.dto.AssetDtos.AssetResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.service.AssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssetController.class)
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AssetService assetService;

    private final AssetResponse asset = new AssetResponse(
            1L, "bitcoin", "BTC", "Bitcoin", true, LocalDateTime.now());

    @Test
    @DisplayName("GET /v1/assets lists all by default")
    void listsAll() throws Exception {
        when(assetService.findAll()).thenReturn(List.of(asset));

        mockMvc.perform(get("/v1/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].externalId").value("bitcoin"));

        verify(assetService).findAll();
    }

    @Test
    @DisplayName("GET /v1/assets?activeOnly=true filters to active assets")
    void listsActiveOnly() throws Exception {
        when(assetService.findActive()).thenReturn(List.of(asset));

        mockMvc.perform(get("/v1/assets").param("activeOnly", "true"))
                .andExpect(status().isOk());

        verify(assetService).findActive();
        verify(assetService, never()).findAll();
    }

    @Test
    @DisplayName("GET /v1/assets/{id}")
    void getsOne() throws Exception {
        when(assetService.findById(1L)).thenReturn(asset);

        mockMvc.perform(get("/v1/assets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC"));
    }

    @Test
    @DisplayName("GET /v1/assets/by-symbol/{symbol}")
    void getsBySymbol() throws Exception {
        when(assetService.findBySymbol("btc")).thenReturn(asset);

        mockMvc.perform(get("/v1/assets/by-symbol/btc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bitcoin"));
    }

    @Test
    @DisplayName("POST /v1/assets returns 201")
    void creates() throws Exception {
        when(assetService.create(any(AssetRequest.class))).thenReturn(asset);

        mockMvc.perform(post("/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssetRequest("bitcoin", "BTC", "Bitcoin"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/assets/1"));
    }

    @Test
    @DisplayName("POST with a blank symbol returns 400")
    void rejectsBlankSymbol() throws Exception {
        mockMvc.perform(post("/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"bitcoin\",\"symbol\":\"\",\"name\":\"Bitcoin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.symbol").exists());
    }

    @Test
    @DisplayName("PUT /v1/assets/{id}")
    void updates() throws Exception {
        when(assetService.update(eq(1L), any(AssetRequest.class))).thenReturn(asset);

        mockMvc.perform(put("/v1/assets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssetRequest("bitcoin", "BTC", "Bitcoin"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/assets/{id}/active toggles price tracking")
    void togglesActive() throws Exception {
        AssetResponse inactive = new AssetResponse(
                1L, "bitcoin", "BTC", "Bitcoin", false, LocalDateTime.now());
        when(assetService.setActive(1L, false)).thenReturn(inactive);

        mockMvc.perform(put("/v1/assets/1/active").param("value", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("DELETE /v1/assets/{id} returns 204")
    void deletes() throws Exception {
        mockMvc.perform(delete("/v1/assets/1"))
                .andExpect(status().isNoContent());

        verify(assetService).delete(1L);
    }

    @Test
    @DisplayName("deleting a referenced asset maps to 422 with a helpful message")
    void referencedAssetIs422() throws Exception {
        doThrow(new BusinessRuleException("Cannot delete asset BTC: it is referenced by 3 transaction(s)."))
                .when(assetService).delete(1L);

        mockMvc.perform(delete("/v1/assets/1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("referenced by 3")));
    }
}
