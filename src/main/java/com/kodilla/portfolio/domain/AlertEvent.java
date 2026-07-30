package com.kodilla.portfolio.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Record of an alert actually firing, written by an observer. */
@Entity
@Table(name = "alert_events")
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Column(nullable = false, length = 400)
    private String message;

    @Column(name = "value_at_trigger", nullable = false, precision = 24, scale = 8)
    private BigDecimal valueAtTrigger;

    @Column(nullable = false)
    private boolean acknowledged = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AlertEvent() {
    }

    public AlertEvent(Alert alert, String message, BigDecimal valueAtTrigger) {
        this.alert = alert;
        this.message = message;
        this.valueAtTrigger = valueAtTrigger;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Alert getAlert() {
        return alert;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getValueAtTrigger() {
        return valueAtTrigger;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
