package com.kodilla.portfolio.service.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** The subject half of the Observer pattern: fans a trigger out to every observer. */
@Component
public class AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

    private final List<AlertObserver> observers;

    public AlertPublisher(List<AlertObserver> observers) {
        this.observers = observers.stream()
                .sorted(Comparator.comparingInt(AlertObserver::order))
                .toList();
    }

    public void publish(AlertTrigger trigger) {
        for (AlertObserver observer : observers) {
            try {
                observer.onAlertTriggered(trigger);
            } catch (RuntimeException e) {
                // One failing observer must not stop the others or abort the
                // whole scheduled evaluation run.
                log.error("Observer {} failed handling alert {}",
                        observer.getClass().getSimpleName(), trigger.alert().getId(), e);
            }
        }
    }

    public int observerCount() {
        return observers.size();
    }
}
