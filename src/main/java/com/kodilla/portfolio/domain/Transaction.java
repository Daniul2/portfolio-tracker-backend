package com.kodilla.portfolio.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single buy or sell. Holdings are never stored directly — they are derived
 * from the transaction history, so the ledger stays the single source of truth.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "price_per_unit_usd", nullable = false, precision = 20, scale = 8)
    private BigDecimal pricePerUnitUsd;

    @Column(name = "fee_usd", precision = 20, scale = 8)
    private BigDecimal feeUsd = BigDecimal.ZERO;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(length = 255)
    private String note;

    protected Transaction() {
    }

    public Transaction(Portfolio portfolio, Asset asset, TransactionType type,
                       BigDecimal quantity, BigDecimal pricePerUnitUsd, LocalDateTime executedAt) {
        this.portfolio = portfolio;
        this.asset = asset;
        this.type = type;
        this.quantity = quantity;
        this.pricePerUnitUsd = pricePerUnitUsd;
        this.executedAt = executedAt;
    }

    @PrePersist
    void onCreate() {
        if (executedAt == null) {
            executedAt = LocalDateTime.now();
        }
        if (feeUsd == null) {
            feeUsd = BigDecimal.ZERO;
        }
    }

    /** Gross value of the trade in USD, excluding fees. */
    public BigDecimal grossValueUsd() {
        return quantity.multiply(pricePerUnitUsd);
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPricePerUnitUsd() {
        return pricePerUnitUsd;
    }

    public void setPricePerUnitUsd(BigDecimal pricePerUnitUsd) {
        this.pricePerUnitUsd = pricePerUnitUsd;
    }

    public BigDecimal getFeeUsd() {
        return feeUsd;
    }

    public void setFeeUsd(BigDecimal feeUsd) {
        this.feeUsd = feeUsd;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
