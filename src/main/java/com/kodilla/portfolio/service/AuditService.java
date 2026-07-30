package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.AuditLog;
import com.kodilla.portfolio.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Central place for writing the audit trail. Every service that changes state
 * calls {@link #record} so there is a single, consistent format for the log.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Database write #1: append an audit entry. */
    @Transactional
    public AuditLog record(String action, String entityType, Long entityId, String details) {
        return auditLogRepository.save(new AuditLog(action, entityType, entityId, truncate(details)));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findRecent() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByEntityType(String entityType) {
        return auditLogRepository.findByEntityTypeOrderByCreatedAtDesc(entityType);
    }

    /** The details column is capped at 800 chars; never let a long payload break the write. */
    private String truncate(String details) {
        if (details == null) {
            return null;
        }
        return details.length() <= 800 ? details : details.substring(0, 797) + "...";
    }
}
