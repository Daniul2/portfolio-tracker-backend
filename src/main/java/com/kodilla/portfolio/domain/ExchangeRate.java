package com.kodilla.portfolio.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A currency rate from the NBP (Polish National Bank) API, stored as
 * "how many PLN one unit of {@code currencyCode} is worth".
 */
@Entity
@Table(name = "exchange_rates", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rate_code_date", columnNames = {"currency_code", "effective_date"})
})
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "rate_pln", nullable = false, precision = 20, scale = 8)
    private BigDecimal ratePln;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    protected ExchangeRate() {
    }

    public ExchangeRate(String currencyCode, BigDecimal ratePln, LocalDate effectiveDate) {
        this.currencyCode = currencyCode;
        this.ratePln = ratePln;
        this.effectiveDate = effectiveDate;
    }

    @PrePersist
    void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getRatePln() {
        return ratePln;
    }

    public void setRatePln(BigDecimal ratePln) {
        this.ratePln = ratePln;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }
}
