package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.AssetDtos.PriceSnapshotResponse;
import com.kodilla.portfolio.dto.CommonDtos.RefreshResultResponse;
import com.kodilla.portfolio.service.PriceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/prices")
public class PriceController {

    private final PriceService priceService;
    private final int defaultRetentionDays;

    public PriceController(PriceService priceService,
                           @Value("${app.scheduler.price-retention-days}") int defaultRetentionDays) {
        this.priceService = priceService;
        this.defaultRetentionDays = defaultRetentionDays;
    }

    @GetMapping("/latest")
    public List<PriceSnapshotResponse> latest() {
        return priceService.findLatestSnapshots();
    }

    @GetMapping("/assets/{assetId}/latest")
    public PriceSnapshotResponse latestForAsset(@PathVariable Long assetId) {
        return priceService.findLatestForAsset(assetId);
    }

    /** Price history for charting. */
    @GetMapping("/assets/{assetId}/history")
    public List<PriceSnapshotResponse> history(@PathVariable Long assetId,
                                               @RequestParam(defaultValue = "7") int days) {
        return priceService.findHistory(assetId, days);
    }

    /** Pulls fresh prices from CoinGecko on demand. */
    @PostMapping("/refresh")
    public RefreshResultResponse refresh() {
        int saved = priceService.refreshPrices();
        return new RefreshResultResponse("CoinGecko", saved,
                "Stored " + saved + " price snapshot(s)", LocalDateTime.now());
    }

    /**
     * Trims stored history. Without a parameter it uses the same retention
     * window as the nightly scheduled purge.
     */
    @DeleteMapping("/history")
    public RefreshResultResponse purge(@RequestParam(required = false) Integer olderThanDays) {
        int days = olderThanDays == null ? defaultRetentionDays : olderThanDays;
        long removed = priceService.purgeOlderThan(days);
        return new RefreshResultResponse("local", (int) removed,
                "Removed " + removed + " snapshot(s)", LocalDateTime.now());
    }
}
