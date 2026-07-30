package com.kodilla.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AssetDtos {

    private AssetDtos() {
    }

    public record AssetRequest(
            @NotBlank(message = "externalId is required")
            @Size(max = 80)
            String externalId,

            @NotBlank(message = "symbol is required")
            @Size(max = 20)
            String symbol,

            @NotBlank(message = "name is required")
            @Size(max = 120)
            String name) {
    }

    public record AssetResponse(
            Long id,
            String externalId,
            String symbol,
            String name,
            boolean active,
            LocalDateTime createdAt) {
    }

    public record PriceSnapshotResponse(
            Long id,
            Long assetId,
            String symbol,
            BigDecimal priceUsd,
            BigDecimal change24hPercent,
            LocalDateTime capturedAt) {
    }
}
