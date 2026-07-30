package com.kodilla.portfolio;

import com.kodilla.portfolio.domain.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Builders for domain objects used across the unit tests. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static void setId(Object entity, Long id) {
        try {
            Field field = findField(entity.getClass(), "id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set id on " + entity.getClass(), e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found on " + type);
    }

    public static User user(Long id, String username) {
        User user = new User(username, username + "@example.com", "Test " + username);
        setId(user, id);
        return user;
    }

    public static Asset asset(Long id, String externalId, String symbol) {
        Asset asset = new Asset(externalId, symbol, symbol + " Coin");
        setId(asset, id);
        return asset;
    }

    public static Portfolio portfolio(Long id, User user, String name, String baseCurrency) {
        Portfolio portfolio = new Portfolio(user, name, baseCurrency);
        setId(portfolio, id);
        return portfolio;
    }

    public static Transaction transaction(Long id, Portfolio portfolio, Asset asset,
                                         TransactionType type, String quantity, String price,
                                         LocalDateTime executedAt) {
        Transaction transaction = new Transaction(portfolio, asset, type,
                new BigDecimal(quantity), new BigDecimal(price), executedAt);
        transaction.setFeeUsd(BigDecimal.ZERO);
        setId(transaction, id);
        return transaction;
    }

    public static Transaction buy(Long id, Portfolio portfolio, Asset asset,
                                  String quantity, String price, LocalDateTime executedAt) {
        return transaction(id, portfolio, asset, TransactionType.BUY, quantity, price, executedAt);
    }

    public static Transaction sell(Long id, Portfolio portfolio, Asset asset,
                                   String quantity, String price, LocalDateTime executedAt) {
        return transaction(id, portfolio, asset, TransactionType.SELL, quantity, price, executedAt);
    }

    public static Alert alert(Long id, Portfolio portfolio, Asset asset,
                              AlertType type, String threshold) {
        Alert alert = new Alert(portfolio, asset, type, new BigDecimal(threshold));
        setId(alert, id);
        return alert;
    }

    public static PriceSnapshot snapshot(Long id, Asset asset, String priceUsd) {
        PriceSnapshot snapshot = new PriceSnapshot(asset, new BigDecimal(priceUsd), BigDecimal.ZERO);
        snapshot.setCapturedAt(LocalDateTime.now());
        setId(snapshot, id);
        return snapshot;
    }

    public static ExchangeRate rate(Long id, String code, String ratePln) {
        ExchangeRate rate = new ExchangeRate(code, new BigDecimal(ratePln), LocalDate.now());
        setId(rate, id);
        return rate;
    }

    /**
     * A transaction manager that satisfies {@code TransactionTemplate} without a database, for
     * unit-testing the services that manage transactions programmatically.
     */
    public static org.springframework.transaction.PlatformTransactionManager noOpTransactionManager() {
        return new org.springframework.transaction.PlatformTransactionManager() {
            @Override
            public org.springframework.transaction.TransactionStatus getTransaction(
                    org.springframework.transaction.TransactionDefinition definition) {
                return new org.springframework.transaction.support.SimpleTransactionStatus();
            }

            @Override
            public void commit(org.springframework.transaction.TransactionStatus status) {
                // nothing to commit without a real resource
            }

            @Override
            public void rollback(org.springframework.transaction.TransactionStatus status) {
                // nothing to roll back without a real resource
            }
        };
    }
}
