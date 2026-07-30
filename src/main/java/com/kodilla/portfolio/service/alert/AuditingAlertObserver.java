package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.service.AuditService;
import org.springframework.stereotype.Component;

/** Adds a row to the audit trail whenever an alert fires. */
@Component
public class AuditingAlertObserver implements AlertObserver {

    private final AuditService auditService;

    public AuditingAlertObserver(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onAlertTriggered(AlertTrigger trigger) {
        auditService.record("ALERT_TRIGGERED", "Alert", trigger.alert().getId(), trigger.message());
    }

    @Override
    public int order() {
        return 1;
    }
}
