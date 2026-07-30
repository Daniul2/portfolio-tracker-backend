package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.dto.AssetDtos.AssetRequest;
import com.kodilla.portfolio.dto.AssetDtos.AssetResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetService {

    private static final String ENTITY = "Asset";

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public AssetService(AssetRepository assetRepository,
                        TransactionRepository transactionRepository,
                        AuditService auditService) {
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
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

    /** Database write #10: add an asset to the tracked catalogue. */
    @Transactional
    public AssetResponse create(AssetRequest request) {
        if (assetRepository.existsByExternalId(request.externalId())) {
            throw new DuplicateResourceException(
                    "Asset '" + request.externalId() + "' is already tracked");
        }
        Asset saved = assetRepository.save(
                new Asset(request.externalId(), request.symbol().toUpperCase(), request.name()));
        auditService.record("ASSET_CREATED", ENTITY, saved.getId(),
                "externalId=" + saved.getExternalId());
        return DtoMapper.toAssetResponse(saved);
    }

    /** Database write #11: update an asset's details. */
    @Transactional
    public AssetResponse update(Long id, AssetRequest request) {
        Asset asset = requireAsset(id);
        if (!asset.getExternalId().equals(request.externalId())
                && assetRepository.existsByExternalId(request.externalId())) {
            throw new DuplicateResourceException(
                    "Asset '" + request.externalId() + "' is already tracked");
        }
        asset.setExternalId(request.externalId());
        asset.setSymbol(request.symbol().toUpperCase());
        asset.setName(request.name());

        Asset saved = assetRepository.save(asset);
        auditService.record("ASSET_UPDATED", ENTITY, id, "externalId=" + saved.getExternalId());
        return DtoMapper.toAssetResponse(saved);
    }

    /**
     * Database write #12: turn price tracking for an asset on or off. Preferred
     * over deleting once transactions reference it.
     */
    @Transactional
    public AssetResponse setActive(Long id, boolean active) {
        Asset asset = requireAsset(id);
        asset.setActive(active);
        Asset saved = assetRepository.save(asset);
        auditService.record(active ? "ASSET_ACTIVATED" : "ASSET_DEACTIVATED", ENTITY, id,
                "symbol=" + saved.getSymbol());
        return DtoMapper.toAssetResponse(saved);
    }

    /** Database write #13: remove an asset, provided nothing references it. */
    @Transactional
    public void delete(Long id) {
        Asset asset = requireAsset(id);
        long referencing = transactionRepository.countByAssetId(id);
        if (referencing > 0) {
            throw new BusinessRuleException("Cannot delete asset " + asset.getSymbol()
                    + ": it is referenced by " + referencing + " transaction(s). Deactivate it instead.");
        }
        String symbol = asset.getSymbol();
        assetRepository.delete(asset);
        auditService.record("ASSET_DELETED", ENTITY, id, "symbol=" + symbol);
    }

    /** Entity lookup used by other services that need the managed instance. */
    @Transactional(readOnly = true)
    public Asset requireAsset(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }
}
