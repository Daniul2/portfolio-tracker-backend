package com.kodilla.portfolio.service.alert;

/** Observer pattern: notified whenever an alert's condition is met. */
public interface AlertObserver {

    void onAlertTriggered(AlertTrigger trigger);

    /** Lower runs first; lets the persisting observer go before the audit one. */
    default int order() {
        return 0;
    }
}
