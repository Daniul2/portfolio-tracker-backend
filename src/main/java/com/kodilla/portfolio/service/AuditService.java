package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.AuditLog;
import com.kodilla.portfolio.dto.CommonDtos.AuditLogResponse;
import com.kodilla.portfolio.mapper.DtoMapper;
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

    private static final String ELLIPSIS = "...";

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(String action, String entityType, Long entityId, String details) {
        auditLogRepository.save(new AuditLog(action, entityType, entityId, truncate(details)));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> findRecent() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(DtoMapper::toAuditLogResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> findByEntityType(String entityType) {
        return auditLogRepository.findByEntityTypeOrderByCreatedAtDesc(entityType).stream()
                .map(DtoMapper::toAuditLogResponse)
                .toList();
    }

    /** Never let a long payload overflow the details column and break the write. */
    private static String truncate(String details) {
        if (details == null || details.length() <= AuditLog.DETAILS_MAX_LENGTH) {
            return details;
        }
        return details.substring(0, AuditLog.DETAILS_MAX_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}
