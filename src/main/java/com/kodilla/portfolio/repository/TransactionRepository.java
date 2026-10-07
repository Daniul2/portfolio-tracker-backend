package com.kodilla.portfolio.repository;

import com.kodilla.portfolio.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByPortfolioIdOrderByExecutedAtDesc(Long portfolioId);

    long countByAssetId(Long assetId);

    long countByPortfolioId(Long portfolioId);
}
