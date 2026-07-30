package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.CommonDtos.AuditLogResponse;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Endpoint 47. The 100 most recent state changes. */
    @GetMapping
    public List<AuditLogResponse> recent() {
        return auditService.findRecent().stream().map(DtoMapper::toAuditLogResponse).toList();
    }

    /** Endpoint 48. */
    @GetMapping("/by-type/{entityType}")
    public List<AuditLogResponse> byEntityType(@PathVariable String entityType) {
        return auditService.findByEntityType(entityType).stream()
                .map(DtoMapper::toAuditLogResponse)
                .toList();
    }
}
