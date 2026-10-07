package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.AlertDtos.AlertEventResponse;
import com.kodilla.portfolio.dto.AlertDtos.AlertRequest;
import com.kodilla.portfolio.dto.AlertDtos.AlertResponse;
import com.kodilla.portfolio.dto.CommonDtos.CountResponse;
import com.kodilla.portfolio.service.AlertService;
import com.kodilla.portfolio.service.alert.AlertEvaluationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/alerts")
public class AlertController {

    private final AlertService alertService;
    private final AlertEvaluationService alertEvaluationService;

    public AlertController(AlertService alertService,
                           AlertEvaluationService alertEvaluationService) {
        this.alertService = alertService;
        this.alertEvaluationService = alertEvaluationService;
    }

    @GetMapping
    public List<AlertResponse> findAll(@RequestParam(required = false) Long portfolioId) {
        return portfolioId == null ? alertService.findAll() : alertService.findByPortfolio(portfolioId);
    }

    @GetMapping("/{id}")
    public AlertResponse findById(@PathVariable Long id) {
        return alertService.findById(id);
    }

    @GetMapping("/events")
    public List<AlertEventResponse> events(@RequestParam Long portfolioId) {
        return alertService.findEventsByPortfolio(portfolioId);
    }

    @GetMapping("/events/unacknowledged")
    public List<AlertEventResponse> unacknowledgedEvents() {
        return alertService.findUnacknowledgedEvents();
    }

    @PostMapping
    public ResponseEntity<AlertResponse> create(@Valid @RequestBody AlertRequest request) {
        AlertResponse created = alertService.create(request);
        return ResponseEntity.created(URI.create("/v1/alerts/" + created.id())).body(created);
    }

    /** Runs the alert rules immediately instead of waiting for the scheduler. */
    @PostMapping("/evaluate")
    public CountResponse evaluateNow() {
        return new CountResponse("alertsTriggered", alertEvaluationService.evaluateAll());
    }

    @PutMapping("/{id}")
    public AlertResponse update(@PathVariable Long id, @Valid @RequestBody AlertRequest request) {
        return alertService.update(id, request);
    }

    @PutMapping("/events/{eventId}/acknowledge")
    public AlertEventResponse acknowledge(@PathVariable Long eventId) {
        return alertService.acknowledgeEvent(eventId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        alertService.delete(id);
    }
}
