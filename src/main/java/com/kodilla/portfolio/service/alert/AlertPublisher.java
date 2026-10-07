package com.kodilla.portfolio.service.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;

/** The subject half of the Observer pattern: fans a trigger out to every observer. */
@Component
public class AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

    private final List<AlertObserver> observers;
    private final TransactionTemplate isolatedTransaction;

    public AlertPublisher(List<AlertObserver> observers, PlatformTransactionManager transactionManager) {
        this.observers = observers.stream()
                .sorted(Comparator.comparingInt(AlertObserver::order))
                .toList();
        this.isolatedTransaction = new TransactionTemplate(transactionManager);
        this.isolatedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void publish(AlertTrigger trigger) {
        for (AlertObserver observer : observers) {
            try {
                isolatedTransaction.executeWithoutResult(status -> observer.onAlertTriggered(trigger));
            } catch (RuntimeException e) {
                log.error("Observer {} failed handling alert {}",
                        observer.getClass().getSimpleName(), trigger.alert().getId(), e);
            }
        }
    }
}
