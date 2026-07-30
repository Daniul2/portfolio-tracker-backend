package com.kodilla.portfolio.service.valuation;

import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.domain.Transaction;
import com.kodilla.portfolio.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns a transaction ledger into current positions using the average-cost method. */
@Component
public class HoldingCalculator {

    private static final int SCALE = 12;
    private static final int MONEY_SCALE = 8;

    /** Returns one entry per asset still held, in stable asset-name order. */
    public List<Holding> calculate(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return List.of();
        }

        // Keyed by externalId rather than the database id: externalId is unique
        // and always populated, so this also works for not-yet-persisted assets.
        Map<String, Position> positions = new LinkedHashMap<>();

        transactions.stream()
                .sorted(Comparator.comparing(Transaction::getExecutedAt)
                        .thenComparing(t -> t.getId() == null ? 0L : t.getId()))
                .forEach(transaction -> {
                    Asset asset = transaction.getAsset();
                    Position position = positions.computeIfAbsent(
                            asset.getExternalId(), key -> new Position(asset));
                    if (transaction.getType() == TransactionType.BUY) {
                        position.applyBuy(transaction);
                    } else {
                        position.applySell(transaction);
                    }
                });

        List<Holding> holdings = new ArrayList<>();
        for (Position position : positions.values()) {
            // Positions fully sold off are not holdings any more.
            if (position.quantity.signum() > 0) {
                holdings.add(new Holding(position.asset, position.quantity, position.costBasis));
            }
        }
        holdings.sort(Comparator.comparing(h -> h.asset().getName(), String.CASE_INSENSITIVE_ORDER));
        return holdings;
    }

    /** Mutable accumulator used only while folding over the ledger. */
    private static final class Position {
        private final Asset asset;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal costBasis = BigDecimal.ZERO;

        private Position(Asset asset) {
            this.asset = asset;
        }

        void applyBuy(Transaction transaction) {
            BigDecimal fee = transaction.getFeeUsd() == null ? BigDecimal.ZERO : transaction.getFeeUsd();
            quantity = quantity.add(transaction.getQuantity());
            costBasis = costBasis.add(transaction.grossValueUsd()).add(fee);
        }

        void applySell(Transaction transaction) {
            BigDecimal sold = transaction.getQuantity();
            if (quantity.signum() <= 0) {
                // Selling with nothing on the books: no basis to release.
                return;
            }
            // Cap at what is actually held so a bad row cannot drive the
            // position negative and corrupt every later calculation.
            BigDecimal effective = sold.min(quantity);
            BigDecimal releasedFraction = effective.divide(quantity, SCALE, RoundingMode.HALF_UP);
            costBasis = costBasis.subtract(costBasis.multiply(releasedFraction));
            quantity = quantity.subtract(effective);

            if (quantity.signum() == 0) {
                costBasis = BigDecimal.ZERO;
            } else {
                // The proportional release above divides at a fixed scale, which leaves sub-cent
                // noise (e.g. 5250.0000000021).
                costBasis = costBasis.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
        }
    }
}
