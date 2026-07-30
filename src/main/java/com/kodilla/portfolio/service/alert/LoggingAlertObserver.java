package com.kodilla.portfolio.service.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Writes fired alerts to the application log. */
@Component
public class LoggingAlertObserver implements AlertObserver {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertObserver.class);

    @Override
    public void onAlertTriggered(AlertTrigger trigger) {
        log.info("Alert {} triggered: {}", trigger.alert().getId(), trigger.message());
    }

    @Override
    public int order() {
        return 2;
    }
}
