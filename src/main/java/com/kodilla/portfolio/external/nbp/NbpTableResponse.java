package com.kodilla.portfolio.external.nbp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Shape of one entry in NBP's {@code /exchangerates/tables/A} response.
 * Unknown fields are ignored so the client survives the provider adding some.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NbpTableResponse(String table, LocalDate effectiveDate, List<NbpRate> rates) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NbpRate(String currency, String code, BigDecimal mid) {
    }
}
