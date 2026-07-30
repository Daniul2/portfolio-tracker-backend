package com.kodilla.portfolio.repository;

import com.kodilla.portfolio.domain.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    List<AlertEvent> findByAlertPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    List<AlertEvent> findByAcknowledgedFalseOrderByCreatedAtDesc();
}
