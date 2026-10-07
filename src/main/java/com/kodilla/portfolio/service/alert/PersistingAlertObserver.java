package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.AlertEvent;
import com.kodilla.portfolio.repository.AlertEventRepository;
import com.kodilla.portfolio.repository.AlertRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Stores the fired alert so the user can see it later, and stamps the alert so the cooldown can
 * suppress repeats.
 */
@Component
public class PersistingAlertObserver implements AlertObserver {

    private final AlertEventRepository alertEventRepository;
    private final AlertRepository alertRepository;

    public PersistingAlertObserver(AlertEventRepository alertEventRepository,
                                   AlertRepository alertRepository) {
        this.alertEventRepository = alertEventRepository;
        this.alertRepository = alertRepository;
    }

    @Override
    public void onAlertTriggered(AlertTrigger trigger) {
        // An alert deleted between evaluation and now has nothing left to record.
        alertRepository.findById(trigger.alert().getId()).ifPresent(alert -> {
            alertEventRepository.save(new AlertEvent(alert, trigger.message(), trigger.observedValue()));
            alert.setLastTriggeredAt(LocalDateTime.now());
        });
    }

    @Override
    public int order() {
        return 0;
    }
}
