package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;

import java.math.BigDecimal;

/** Produced when a strategy decides an alert's condition is met. */
public record AlertTrigger(Alert alert, BigDecimal observedValue, String message) {
}
