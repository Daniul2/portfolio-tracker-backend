package com.kodilla.portfolio.repository;

import com.kodilla.portfolio.domain.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByPortfolioId(Long portfolioId);

    List<Alert> findByActiveTrue();
}
