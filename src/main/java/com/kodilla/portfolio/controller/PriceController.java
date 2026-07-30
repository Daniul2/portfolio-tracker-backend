package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.AssetDtos.PriceSnapshotResponse;
import com.kodilla.portfolio.dto.CommonDtos.RefreshResultResponse;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.service.PriceService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/prices")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    /** Endpoint 36. Latest stored price for every asset that has one. */
    @GetMapping("/latest")
    public List<PriceSnapshotResponse> latest() {
        return priceService.findLatestPrices().values().stream()
                .map(DtoMapper::toPriceSnapshotResponse)
                .toList();
    }

    /** Endpoint 37. */
    @GetMapping("/assets/{assetId}/latest")
    public PriceSnapshotResponse latestForAsset(@PathVariable Long assetId) {
        return priceService.findLatestForAsset(assetId)
                .map(DtoMapper::toPriceSnapshotResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No price has been recorded yet for asset " + assetId));
    }

    /** Endpoint 38. Price history for charting. */
    @GetMapping("/assets/{assetId}/history")
    public List<PriceSnapshotResponse> history(@PathVariable Long assetId,
                                               @RequestParam(defaultValue = "7") int days) {
        return priceService.findHistory(assetId, days).stream()
                .map(DtoMapper::toPriceSnapshotResponse)
                .toList();
    }

    /** Endpoint 39. Pulls fresh prices from CoinGecko on demand. */
    @PostMapping("/refresh")
    public RefreshResultResponse refresh() {
        int saved = priceService.refreshPrices().size();
        return new RefreshResultResponse("CoinGecko", saved,
                "Stored " + saved + " price snapshot(s)", LocalDateTime.now());
    }

    /** Endpoint 40. Trims stored history to the given number of days. */
    @DeleteMapping("/history")
    public RefreshResultResponse purge(@RequestParam(defaultValue = "90") int olderThanDays) {
        long removed = priceService.purgeOlderThan(olderThanDays);
        return new RefreshResultResponse("local", (int) removed,
                "Removed " + removed + " snapshot(s)", LocalDateTime.now());
    }
}
