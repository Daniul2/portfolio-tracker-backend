package com.kodilla.portfolio.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A price reading captured from CoinGecko by the scheduler. Keeping the history
 * lets the app chart an asset over time instead of only showing "right now".
 */
@Entity
@Table(name = "price_snapshots", indexes = {
        @Index(name = "idx_snapshot_asset_time", columnList = "asset_id, captured_at")
})
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "price_usd", nullable = false, precision = 20, scale = 8)
    private BigDecimal priceUsd;

    @Column(name = "change_24h_percent", precision = 12, scale = 4)
    private BigDecimal change24hPercent;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    protected PriceSnapshot() {
    }

    public PriceSnapshot(Asset asset, BigDecimal priceUsd, BigDecimal change24hPercent) {
        this.asset = asset;
        this.priceUsd = priceUsd;
        this.change24hPercent = change24hPercent;
    }

    @PrePersist
    void onCreate() {
        if (capturedAt == null) {
            capturedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public BigDecimal getPriceUsd() {
        return priceUsd;
    }

    public BigDecimal getChange24hPercent() {
        return change24hPercent;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }
}
