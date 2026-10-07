package com.kodilla.portfolio.service.valuation;

import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.domain.PriceSnapshot;
import com.kodilla.portfolio.repository.TransactionRepository;
import com.kodilla.portfolio.service.ExchangeRateService;
import com.kodilla.portfolio.service.PriceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prices a portfolio by combining three inputs: the local transaction ledger,
 * CoinGecko prices, and the NBP exchange rate.
 */
@Service
public class PortfolioValuationService {

    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactionRepository;
    private final HoldingCalculator holdingCalculator;
    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;

    public PortfolioValuationService(TransactionRepository transactionRepository,
                                     HoldingCalculator holdingCalculator,
                                     PriceService priceService,
                                     ExchangeRateService exchangeRateService) {
        this.transactionRepository = transactionRepository;
        this.holdingCalculator = holdingCalculator;
        this.priceService = priceService;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional(readOnly = true)
    public List<Holding> holdingsOf(Portfolio portfolio) {
        return holdingCalculator.calculate(
                transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(portfolio.getId()));
    }

    @Transactional(readOnly = true)
    public PortfolioValuation value(Portfolio portfolio) {
        List<Holding> holdings = holdingsOf(portfolio);
        Map<Long, PriceSnapshot> latestPrices = priceService.findLatestPrices();
        Optional<BigDecimal> fxRate = exchangeRateService.usdToCurrencyRate(portfolio.getBaseCurrency());

        List<HoldingValuation> valuations = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal pricedCost = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        int pricedCount = 0;

        for (Holding holding : holdings) {
            PriceSnapshot snapshot = latestPrices.get(holding.asset().getId());
            BigDecimal price = snapshot == null ? null : snapshot.getPriceUsd();
            BigDecimal cost = holding.costBasisUsd();
            totalCost = totalCost.add(cost);

            if (price == null) {
                // Unpriced asset: report it, but do not invent a market value.
                valuations.add(new HoldingValuation(holding, null, null, null, null, null));
                continue;
            }

            pricedCount++;
            pricedCost = pricedCost.add(cost);
            BigDecimal marketValue = holding.quantity().multiply(price);
            BigDecimal pnl = marketValue.subtract(cost);
            BigDecimal pnlPercent = percentOf(pnl, cost);
            BigDecimal valueBase = fxRate.map(rate -> scaleMoney(marketValue.multiply(rate))).orElse(null);

            totalValue = totalValue.add(marketValue);
            valuations.add(new HoldingValuation(
                    holding,
                    price,
                    scaleMoney(marketValue),
                    scaleMoney(pnl),
                    pnlPercent,
                    valueBase));
        }

        // Profit is measured only against what could be priced.
        boolean fullyPriced = pricedCount == holdings.size();
        boolean anyValue = pricedCount > 0 || holdings.isEmpty();
        BigDecimal value = anyValue ? totalValue : null;
        BigDecimal pnl = anyValue ? totalValue.subtract(pricedCost) : null;
        BigDecimal valueBase = value == null ? null
                : fxRate.map(rate -> scaleMoney(value.multiply(rate))).orElse(null);

        return new PortfolioValuation(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getBaseCurrency(),
                scaleMoney(totalCost),
                scaleMoney(value),
                scaleMoney(pnl),
                pnl == null ? null : percentOf(pnl, pricedCost),
                fxRate.orElse(null),
                valueBase,
                fullyPriced,
                valuations,
                LocalDateTime.now());
    }

    /**
     * Total market value in USD for portfolio-level alerts, or null while any holding is unpriced.
     */
    @Transactional(readOnly = true)
    public BigDecimal totalValueUsd(Portfolio portfolio) {
        PortfolioValuation valuation = value(portfolio);
        return valuation.fullyPriced() ? valuation.totalValueUsd() : null;
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            // No cost basis means percentage change is undefined, not zero.
            return null;
        }
        return amount.multiply(ONE_HUNDRED)
                .divide(base, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
