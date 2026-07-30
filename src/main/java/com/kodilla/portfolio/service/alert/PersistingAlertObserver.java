package com.kodilla.portfolio.service.alert;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertEvent;
import com.kodilla.portfolio.repository.AlertEventRepository;
import com.kodilla.portfolio.repository.AlertRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Stores the fired alert so the user can see it later. */
@Component
public class PersistingAlertObserver implements AlertObserver {

    private final AlertEventRepository alertEventRepository;
    private final AlertRepository alertRepository;

    public PersistingAlertObserver(AlertEventRepository alertEventRepository,
                                   AlertRepository alertRepository) {
        this.alertEventRepository = alertEventRepository;
        this.alertRepository = alertRepository;
    }

    /** Database writes #5 and #6: save the event and stamp the alert. */
    @Override
    @Transactional
    public void onAlertTriggered(AlertTrigger trigger) {
        Alert alert = trigger.alert();
        alertEventRepository.save(new AlertEvent(alert, trigger.message(), trigger.observedValue()));
        alert.setLastTriggeredAt(LocalDateTime.now());
        alertRepository.save(alert);
    }

    @Override
    public int order() {
        return 0;
    }
}
