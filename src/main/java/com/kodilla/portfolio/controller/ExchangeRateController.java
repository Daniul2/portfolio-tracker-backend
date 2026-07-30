package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.CommonDtos.ExchangeRateResponse;
import com.kodilla.portfolio.dto.CommonDtos.RefreshResultResponse;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.service.ExchangeRateService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/rates")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    /** Endpoint 41. */
    @GetMapping
    public List<ExchangeRateResponse> findAll() {
        return exchangeRateService.findAll().stream()
                .map(DtoMapper::toExchangeRateResponse)
                .toList();
    }

    /** Endpoint 42. */
    @GetMapping("/{currencyCode}")
    public ExchangeRateResponse findLatest(@PathVariable String currencyCode) {
        return DtoMapper.toExchangeRateResponse(exchangeRateService.requireLatest(currencyCode));
    }

    /** Endpoint 43. The USD-to-target multiplier used when valuing portfolios. */
    @GetMapping("/convert")
    public Map<String, Object> usdRate(@RequestParam(defaultValue = "PLN") String to) {
        BigDecimal rate = exchangeRateService.usdToCurrencyRate(to)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No rate available to convert USD to " + to.toUpperCase()));
        return Map.of("from", "USD", "to", to.toUpperCase(), "rate", rate);
    }

    /** Endpoint 44. Pulls the current NBP table on demand. */
    @PostMapping("/refresh")
    public RefreshResultResponse refresh() {
        int saved = exchangeRateService.refreshRates().size();
        return new RefreshResultResponse("NBP", saved,
                "Stored " + saved + " exchange rate(s)", LocalDateTime.now());
    }
}
