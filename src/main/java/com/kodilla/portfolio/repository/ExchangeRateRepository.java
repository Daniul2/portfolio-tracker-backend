package com.kodilla.portfolio.repository;

import com.kodilla.portfolio.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findFirstByCurrencyCodeOrderByEffectiveDateDesc(String currencyCode);

    Optional<ExchangeRate> findByCurrencyCodeAndEffectiveDate(String currencyCode, LocalDate effectiveDate);

    List<ExchangeRate> findByEffectiveDate(LocalDate effectiveDate);
}
