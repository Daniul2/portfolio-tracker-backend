package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.domain.PriceSnapshot;
import com.kodilla.portfolio.dto.AssetDtos.PriceSnapshotResponse;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.external.CryptoPriceProvider;
import com.kodilla.portfolio.external.CryptoQuote;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.PriceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fetches prices from the crypto provider and keeps a local history of them. */
@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private final CryptoPriceProvider priceProvider;
    private final AssetRepository assetRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public PriceService(CryptoPriceProvider priceProvider,
                        AssetRepository assetRepository,
                        PriceSnapshotRepository snapshotRepository,
                        AuditService auditService,
                        PlatformTransactionManager transactionManager) {
        this.priceProvider = priceProvider;
        this.assetRepository = assetRepository;
        this.snapshotRepository = snapshotRepository;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Pulls current prices for every active asset and stores one snapshot each. */
    public int refreshPrices() {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        if (activeAssets.isEmpty()) {
            log.debug("No active assets to refresh");
            return 0;
        }

        Map<String, Asset> byExternalId = new HashMap<>();
        activeAssets.forEach(asset -> byExternalId.put(asset.getExternalId(), asset));

        // Outside the transaction on purpose.
        Map<String, CryptoQuote> quotes = priceProvider.fetchPrices(byExternalId.keySet());
        if (quotes.isEmpty()) {
            log.warn("{} returned no usable quotes", priceProvider.providerName());
            return 0;
        }

        Integer saved = transactionTemplate.execute(status -> {
            List<PriceSnapshot> batch = new ArrayList<>();
            quotes.forEach((externalId, quote) -> {
                Asset asset = byExternalId.get(externalId);
                if (asset != null) {
                    batch.add(new PriceSnapshot(asset, quote.priceUsd(), quote.change24hPercent()));
                }
            });
            int persisted = snapshotRepository.saveAll(batch).size();
            auditService.record("PRICES_REFRESHED", "PriceSnapshot", null,
                    "Saved " + persisted + " snapshot(s) from " + priceProvider.providerName());
            return persisted;
        });

        int result = saved == null ? 0 : saved;
        log.info("Refreshed {} price snapshot(s) from {}", result, priceProvider.providerName());
        return result;
    }

    /** Newest stored price for every asset that has one. */
    @Transactional(readOnly = true)
    public List<PriceSnapshotResponse> findLatestSnapshots() {
        return findLatestPrices().values().stream()
                .map(DtoMapper::toPriceSnapshotResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PriceSnapshotResponse findLatestForAsset(Long assetId) {
        return snapshotRepository.findFirstByAssetIdOrderByCapturedAtDesc(assetId)
                .map(DtoMapper::toPriceSnapshotResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No price has been recorded yet for asset " + assetId));
    }

    /**
     * Latest snapshot per asset, keyed by asset id; assets never priced are absent.
     * Returns entities because it feeds the valuation and alert services, not the API.
     */
    @Transactional(readOnly = true)
    public Map<Long, PriceSnapshot> findLatestPrices() {
        Map<Long, PriceSnapshot> latest = new HashMap<>();
        for (Asset asset : assetRepository.findAll()) {
            snapshotRepository.findFirstByAssetIdOrderByCapturedAtDesc(asset.getId())
                    .ifPresent(snapshot -> latest.put(asset.getId(), snapshot));
        }
        return latest;
    }

    @Transactional(readOnly = true)
    public List<PriceSnapshotResponse> findHistory(Long assetId, int days) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", assetId);
        }
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return snapshotRepository.findByAssetIdAndCapturedAtAfterOrderByCapturedAtAsc(assetId, since)
                .stream()
                .map(DtoMapper::toPriceSnapshotResponse)
                .toList();
    }

    /** Drops snapshots older than the given number of days. */
    @Transactional
    public long purgeOlderThan(int days) {
        long removed = snapshotRepository.deleteByCapturedAtBefore(LocalDateTime.now().minusDays(days));
        if (removed > 0) {
            auditService.record("PRICES_PURGED", "PriceSnapshot", null,
                    "Removed " + removed + " snapshot(s) older than " + days + " day(s)");
        }
        return removed;
    }
}
