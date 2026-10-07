package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.Alert;
import com.kodilla.portfolio.domain.AlertEvent;
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
import com.kodilla.portfolio.service.alert.AlertStrategy;
import com.kodilla.portfolio.service.alert.AlertStrategyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertService {

    private static final String ENTITY = "Alert";

    private final AlertRepository alertRepository;
    private final AlertEventRepository alertEventRepository;
    private final PortfolioService portfolioService;
    private final AssetService assetService;
    private final AlertStrategyFactory strategyFactory;
    private final AuditService auditService;

    public AlertService(AlertRepository alertRepository,
                        AlertEventRepository alertEventRepository,
                        PortfolioService portfolioService,
                        AssetService assetService,
                        AlertStrategyFactory strategyFactory,
                        AuditService auditService) {
        this.alertRepository = alertRepository;
        this.alertEventRepository = alertEventRepository;
        this.portfolioService = portfolioService;
        this.assetService = assetService;
        this.strategyFactory = strategyFactory;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findAll() {
        return alertRepository.findAll().stream().map(DtoMapper::toAlertResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findByPortfolio(Long portfolioId) {
        portfolioService.requirePortfolio(portfolioId);
        return alertRepository.findByPortfolioId(portfolioId).stream()
                .map(DtoMapper::toAlertResponse).toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse findById(Long id) {
        return DtoMapper.toAlertResponse(requireAlert(id));
    }

    @Transactional
    public AlertResponse create(AlertRequest request) {
        Portfolio portfolio = portfolioService.requirePortfolio(request.portfolioId());
        Asset asset = resolveAsset(request);

        Alert alert = new Alert(portfolio, asset, request.type(), request.threshold());
        if (request.active() != null) {
            alert.setActive(request.active());
        }
        Alert saved = alertRepository.save(alert);

        auditService.record("ALERT_CREATED", ENTITY, saved.getId(), describe(saved));
        return DtoMapper.toAlertResponse(saved);
    }

    @Transactional
    public AlertResponse update(Long id, AlertRequest request) {
        Alert alert = requireAlert(id);

        alert.setAsset(resolveAsset(request));
        alert.setType(request.type());
        alert.setThreshold(request.threshold());
        if (request.active() != null) {
            alert.setActive(request.active());
        }

        Alert saved = alertRepository.save(alert);
        auditService.record("ALERT_UPDATED", ENTITY, id, describe(saved));
        return DtoMapper.toAlertResponse(saved);
    }

    /** Removes the alert; its fired events go with it by cascade. */
    @Transactional
    public void delete(Long id) {
        Alert alert = requireAlert(id);
        alertRepository.delete(alert);
        auditService.record("ALERT_DELETED", ENTITY, id, "type=" + alert.getType());
    }

    @Transactional(readOnly = true)
    public List<AlertEventResponse> findEventsByPortfolio(Long portfolioId) {
        portfolioService.requirePortfolio(portfolioId);
        return alertEventRepository.findByAlertPortfolioIdOrderByCreatedAtDesc(portfolioId).stream()
                .map(DtoMapper::toAlertEventResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertEventResponse> findUnacknowledgedEvents() {
        return alertEventRepository.findByAcknowledgedFalseOrderByCreatedAtDesc().stream()
                .map(DtoMapper::toAlertEventResponse).toList();
    }

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
     * Whether an alert needs an asset is the strategy's call, not this service's, so a new alert
     * type never requires a change here.
     */
    private Asset resolveAsset(AlertRequest request) {
        AlertStrategy strategy = strategyFactory.require(request.type());
        if (!strategy.requiresAsset()) {
            return null;
        }
        if (request.assetId() == null) {
            throw new BusinessRuleException("assetId is required for alert type " + request.type());
        }
        return assetService.requireAsset(request.assetId());
    }

    private Alert requireAlert(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private static String describe(Alert alert) {
        return "type=" + alert.getType() + ", threshold=" + alert.getThreshold().toPlainString();
    }
}
