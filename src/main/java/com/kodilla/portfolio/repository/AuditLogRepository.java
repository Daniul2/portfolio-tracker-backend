package com.kodilla.portfolio.repository;

import com.kodilla.portfolio.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    List<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType);
}
