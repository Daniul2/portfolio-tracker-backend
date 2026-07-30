package com.kodilla.portfolio.external;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Provider-neutral currency rate. */
public record RateQuote(String currencyCode, BigDecimal ratePln, LocalDate effectiveDate) {
}
