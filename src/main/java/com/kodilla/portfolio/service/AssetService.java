package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.dto.AssetDtos.AssetRequest;
import com.kodilla.portfolio.dto.AssetDtos.AssetResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.repository.AlertRepository;
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.PriceSnapshotRepository;
import com.kodilla.portfolio.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetService {

    private static final String ENTITY = "Asset";

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final AuditService auditService;

    public AssetService(AssetRepository assetRepository,
                        TransactionRepository transactionRepository,
                        AlertRepository alertRepository,
                        PriceSnapshotRepository snapshotRepository,
                        AuditService auditService) {
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.snapshotRepository = snapshotRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> findAll() {
        return assetRepository.findAll().stream().map(DtoMapper::toAssetResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> findActive() {
        return assetRepository.findByActiveTrue().stream().map(DtoMapper::toAssetResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse findById(Long id) {
        return DtoMapper.toAssetResponse(requireAsset(id));
    }

    @Transactional(readOnly = true)
    public AssetResponse findBySymbol(String symbol) {
        Asset asset = assetRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("No asset with symbol " + symbol));
        return DtoMapper.toAssetResponse(asset);
    }

    @Transactional
    public AssetResponse create(AssetRequest request) {
        requireUntracked(request.externalId());
        Asset saved = assetRepository.save(
                new Asset(request.externalId(), request.symbol().toUpperCase(), request.name()));
        auditService.record("ASSET_CREATED", ENTITY, saved.getId(),
                "externalId=" + saved.getExternalId());
        return DtoMapper.toAssetResponse(saved);
    }

    @Transactional
    public AssetResponse update(Long id, AssetRequest request) {
        Asset asset = requireAsset(id);
        if (!asset.getExternalId().equals(request.externalId())) {
            requireUntracked(request.externalId());
        }
        asset.setExternalId(request.externalId());
        asset.setSymbol(request.symbol().toUpperCase());
        asset.setName(request.name());

        Asset saved = assetRepository.save(asset);
        auditService.record("ASSET_UPDATED", ENTITY, id, "externalId=" + saved.getExternalId());
        return DtoMapper.toAssetResponse(saved);
    }

    /** Turns price tracking on or off. Preferred over deleting a referenced asset. */
    @Transactional
    public AssetResponse setActive(Long id, boolean active) {
        Asset asset = requireAsset(id);
        asset.setActive(active);
        Asset saved = assetRepository.save(asset);
        auditService.record(active ? "ASSET_ACTIVATED" : "ASSET_DEACTIVATED", ENTITY, id,
                "symbol=" + saved.getSymbol());
        return DtoMapper.toAssetResponse(saved);
    }

    /** Removes an asset together with its price history. */
    @Transactional
    public void delete(Long id) {
        Asset asset = requireAsset(id);

        long transactions = transactionRepository.countByAssetId(id);
        if (transactions > 0) {
            throw new BusinessRuleException("Cannot delete asset " + asset.getSymbol()
                    + ": it is referenced by " + transactions + " transaction(s). Deactivate it instead.");
        }
        long alerts = alertRepository.countByAssetId(id);
        if (alerts > 0) {
            throw new BusinessRuleException("Cannot delete asset " + asset.getSymbol()
                    + ": it is watched by " + alerts + " alert(s). Delete those alerts first.");
        }

        String symbol = asset.getSymbol();
        long snapshots = snapshotRepository.deleteByAssetId(id);
        assetRepository.delete(asset);
        auditService.record("ASSET_DELETED", ENTITY, id,
                "symbol=" + symbol + ", price snapshots removed=" + snapshots);
    }

    /** Looks up an asset or fails with a 404; shared with the other services. */
    @Transactional(readOnly = true)
    public Asset requireAsset(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private void requireUntracked(String externalId) {
        if (assetRepository.existsByExternalId(externalId)) {
            throw new DuplicateResourceException("Asset '" + externalId + "' is already tracked");
        }
    }
}
