package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertEvent;
import com.kodilla.portfolio.domain.AlertType;
import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.dto.AlertDtos.AlertEventResponse;
import com.kodilla.portfolio.dto.AlertDtos.AlertRequest;
import com.kodilla.portfolio.dto.AlertDtos.AlertResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.repository.AlertEventRepository;
import com.kodilla.portfolio.repository.AlertRepository;
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.PortfolioRepository;
import com.kodilla.portfolio.service.alert.AlertStrategyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertService {

    private static final String ENTITY = "Alert";

    private final AlertRepository alertRepository;
    private final AlertEventRepository alertEventRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final AlertStrategyFactory strategyFactory;
    private final AuditService auditService;

    public AlertService(AlertRepository alertRepository,
                        AlertEventRepository alertEventRepository,
                        PortfolioRepository portfolioRepository,
                        AssetRepository assetRepository,
                        AlertStrategyFactory strategyFactory,
                        AuditService auditService) {
        this.alertRepository = alertRepository;
        this.alertEventRepository = alertEventRepository;
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.strategyFactory = strategyFactory;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findAll() {
        return alertRepository.findAll().stream().map(DtoMapper::toAlertResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findByPortfolio(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio", portfolioId);
        }
        return alertRepository.findByPortfolioId(portfolioId).stream()
                .map(DtoMapper::toAlertResponse).toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse findById(Long id) {
        return DtoMapper.toAlertResponse(requireAlert(id));
    }

    /** Database write #20: create a price or portfolio-value alert. */
    @Transactional
    public AlertResponse create(AlertRequest request) {
        Portfolio portfolio = portfolioRepository.findById(request.portfolioId())
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", request.portfolioId()));

        if (strategyFactory.strategyFor(request.type()).isEmpty()) {
            throw new BusinessRuleException("Alert type " + request.type() + " is not supported");
        }

        Asset asset = resolveAsset(request);

        Alert saved = alertRepository.save(
                new Alert(portfolio, asset, request.type(), request.threshold()));
        if (request.active() != null) {
            saved.setActive(request.active());
            saved = alertRepository.save(saved);
        }

        auditService.record("ALERT_CREATED", ENTITY, saved.getId(),
                "type=" + saved.getType() + ", threshold=" + saved.getThreshold().toPlainString());
        return DtoMapper.toAlertResponse(saved);
    }

    /** Database write #21: change an alert's threshold, type or active flag. */
    @Transactional
    public AlertResponse update(Long id, AlertRequest request) {
        Alert alert = requireAlert(id);

        if (strategyFactory.strategyFor(request.type()).isEmpty()) {
            throw new BusinessRuleException("Alert type " + request.type() + " is not supported");
        }

        alert.setType(request.type());
        alert.setAsset(resolveAsset(request));
        alert.setThreshold(request.threshold());
        if (request.active() != null) {
            alert.setActive(request.active());
        }

        Alert saved = alertRepository.save(alert);
        auditService.record("ALERT_UPDATED", ENTITY, id,
                "type=" + saved.getType() + ", threshold=" + saved.getThreshold().toPlainString());
        return DtoMapper.toAlertResponse(saved);
    }

    /** Database write #22: delete an alert. */
    @Transactional
    public void delete(Long id) {
        Alert alert = requireAlert(id);
        alertRepository.delete(alert);
        auditService.record("ALERT_DELETED", ENTITY, id, "type=" + alert.getType());
    }

    @Transactional(readOnly = true)
    public List<AlertEventResponse> findEventsByPortfolio(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio", portfolioId);
        }
        return alertEventRepository.findByAlertPortfolioIdOrderByCreatedAtDesc(portfolioId).stream()
                .map(DtoMapper::toAlertEventResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertEventResponse> findUnacknowledgedEvents() {
        return alertEventRepository.findByAcknowledgedFalseOrderByCreatedAtDesc().stream()
                .map(DtoMapper::toAlertEventResponse).toList();
    }

    /** Database write #23: mark a fired alert as seen. */
    @Transactional
    public AlertEventResponse acknowledgeEvent(Long eventId) {
        AlertEvent event = alertEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("AlertEvent", eventId));
        event.setAcknowledged(true);
        AlertEvent saved = alertEventRepository.save(event);
        auditService.record("ALERT_EVENT_ACKNOWLEDGED", "AlertEvent", eventId, saved.getMessage());
        return DtoMapper.toAlertEventResponse(saved);
    }

    /**
     * Price alerts need an asset; portfolio-level alerts must not have one, or
     * the response would imply a scope the strategy does not actually use.
     */
    private Asset resolveAsset(AlertRequest request) {
        boolean assetRequired = request.type() == AlertType.PRICE_ABOVE
                || request.type() == AlertType.PRICE_BELOW;

        if (!assetRequired) {
            return null;
        }
        if (request.assetId() == null) {
            throw new BusinessRuleException("assetId is required for alert type " + request.type());
        }
        return assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", request.assetId()));
    }

    private Alert requireAlert(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }
}
