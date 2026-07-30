package com.kodilla.portfolio.config;

import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Puts a small, realistic dataset in place on first start so the app is not an empty shell when
 * someone runs it for the first time.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;

    public DataSeeder(UserRepository userRepository,
                      PortfolioRepository portfolioRepository,
                      AssetRepository assetRepository,
                      TransactionRepository transactionRepository,
                      AlertRepository alertRepository) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.debug("Demo data already present, skipping seed");
            return;
        }

        Asset bitcoin = assetRepository.save(new Asset("bitcoin", "BTC", "Bitcoin"));
        Asset ethereum = assetRepository.save(new Asset("ethereum", "ETH", "Ethereum"));
        Asset cardano = assetRepository.save(new Asset("cardano", "ADA", "Cardano"));
        Asset solana = assetRepository.save(new Asset("solana", "SOL", "Solana"));

        User demo = userRepository.save(new User("demo", "demo@example.com", "Demo User"));
        Portfolio longTerm = portfolioRepository.save(new Portfolio(demo, "Long term", "PLN"));
        Portfolio experiments = portfolioRepository.save(new Portfolio(demo, "Experiments", "USD"));

        LocalDateTime now = LocalDateTime.now();

        transactionRepository.save(buy(longTerm, bitcoin, "0.25", "42000", now.minusDays(120)));
        transactionRepository.save(buy(longTerm, bitcoin, "0.10", "58000", now.minusDays(45)));
        transactionRepository.save(buy(longTerm, ethereum, "3.5", "2100", now.minusDays(90)));
        transactionRepository.save(sell(longTerm, ethereum, "1.0", "2600", now.minusDays(20)));
        transactionRepository.save(buy(experiments, cardano, "1500", "0.45", now.minusDays(60)));
        transactionRepository.save(buy(experiments, solana, "12", "95", now.minusDays(30)));

        alertRepository.save(new Alert(longTerm, bitcoin, AlertType.PRICE_ABOVE, new BigDecimal("75000")));
        alertRepository.save(new Alert(longTerm, ethereum, AlertType.PRICE_BELOW, new BigDecimal("1500")));
        alertRepository.save(new Alert(experiments, null,
                AlertType.PORTFOLIO_VALUE_ABOVE, new BigDecimal("2000")));

        log.info("Seeded demo data: user 'demo' (id={}) with 2 portfolios, 4 assets, 6 transactions",
                demo.getId());
    }

    private Transaction buy(Portfolio portfolio, Asset asset, String quantity,
                            String price, LocalDateTime executedAt) {
        return new Transaction(portfolio, asset, TransactionType.BUY,
                new BigDecimal(quantity), new BigDecimal(price), executedAt);
    }

    private Transaction sell(Portfolio portfolio, Asset asset, String quantity,
                             String price, LocalDateTime executedAt) {
        return new Transaction(portfolio, asset, TransactionType.SELL,
                new BigDecimal(quantity), new BigDecimal(price), executedAt);
    }
}
