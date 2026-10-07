package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.ExchangeRate;
import com.kodilla.portfolio.dto.CommonDtos.ExchangeRateResponse;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.external.ExchangeRateProvider;
import com.kodilla.portfolio.external.RateQuote;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/** Keeps a local copy of NBP's currency table and converts between currencies. */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final String PIVOT_CURRENCY = "PLN";
    private static final String USD = "USD";
    private static final int RATE_SCALE = 8;

    private final ExchangeRateProvider rateProvider;
    private final ExchangeRateRepository rateRepository;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    /** Serializes refreshes so two of them never insert the same rate twice. */
    private final ReentrantLock refreshLock = new ReentrantLock();

    public ExchangeRateService(ExchangeRateProvider rateProvider,
                               ExchangeRateRepository rateRepository,
                               AuditService auditService,
                               PlatformTransactionManager transactionManager) {
        this.rateProvider = rateProvider;
        this.rateRepository = rateRepository;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Pulls the latest NBP table and stores one row per currency per effective date. */
    public int refreshRates() {
        refreshLock.lock();
        try {
            Integer saved = transactionTemplate.execute(status -> persistLatestRates());
            return saved == null ? 0 : saved;
        } finally {
            refreshLock.unlock();
        }
    }

    /** Returns how many rates were stored. */
    private int persistLatestRates() {
        List<RateQuote> quotes = rateProvider.fetchLatestRates();
        if (quotes.isEmpty()) {
            return 0;
        }

        // The provider should not repeat a currency, but a duplicate in the
        // payload would collide on the unique key, so collapse them first.
        Map<String, RateQuote> uniqueQuotes = new LinkedHashMap<>();
        for (RateQuote quote : quotes) {
            uniqueQuotes.putIfAbsent(quote.currencyCode(), quote);
        }

        // One query for the whole day rather than one per currency.
        LocalDate effectiveDate = uniqueQuotes.values().iterator().next().effectiveDate();
        Map<String, ExchangeRate> existingByCode = new HashMap<>();
        for (ExchangeRate rate : rateRepository.findByEffectiveDate(effectiveDate)) {
            existingByCode.put(rate.getCurrencyCode(), rate);
        }

        List<ExchangeRate> toSave = new ArrayList<>();
        for (RateQuote quote : uniqueQuotes.values()) {
            ExchangeRate existing = existingByCode.get(quote.currencyCode());
            if (existing != null) {
                existing.setRatePln(quote.ratePln());
                toSave.add(existing);
            } else {
                toSave.add(new ExchangeRate(
                        quote.currencyCode(), quote.ratePln(), quote.effectiveDate()));
            }
        }

        int saved = rateRepository.saveAll(toSave).size();

        auditService.record("RATES_REFRESHED", "ExchangeRate", null,
                "Stored " + saved + " rate(s) from " + rateProvider.providerName());
        log.info("Refreshed {} exchange rate(s) from {}", saved, rateProvider.providerName());
        return saved;
    }

    @Transactional(readOnly = true)
    public ExchangeRateResponse requireLatest(String currencyCode) {
        return findLatest(currencyCode)
                .map(DtoMapper::toExchangeRateResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No exchange rate stored for currency " + currencyCode));
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> findAll() {
        return rateRepository.findAll().stream().map(DtoMapper::toExchangeRateResponse).toList();
    }

    /**
     * How many units of {@code targetCurrency} one USD buys — the multiplier that turns a USD
     * portfolio value into the user's reporting currency.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> usdToCurrencyRate(String targetCurrency) {
        String target = targetCurrency == null ? USD : targetCurrency.toUpperCase();
        if (USD.equals(target)) {
            return Optional.of(BigDecimal.ONE);
        }

        Optional<ExchangeRate> usdRate = findLatest(USD);
        if (usdRate.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal plnPerUsd = usdRate.get().getRatePln();

        if (PIVOT_CURRENCY.equals(target)) {
            return Optional.of(plnPerUsd);
        }

        return findLatest(target)
                .map(ExchangeRate::getRatePln)
                .filter(plnPerTarget -> plnPerTarget.signum() != 0)
                .map(plnPerTarget -> plnPerUsd.divide(plnPerTarget, RATE_SCALE, RoundingMode.HALF_UP));
    }

    private Optional<ExchangeRate> findLatest(String currencyCode) {
        return rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc(
                currencyCode.toUpperCase());
    }
}
