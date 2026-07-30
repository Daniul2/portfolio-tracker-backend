package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.domain.PriceSnapshot;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.external.CryptoPriceProvider;
import com.kodilla.portfolio.external.CryptoQuote;
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
import java.util.Optional;

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

    /**
     * Database write #2: pull current prices for every active asset and store one snapshot each.
     */
    public List<PriceSnapshot> refreshPrices() {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        if (activeAssets.isEmpty()) {
            log.debug("No active assets to refresh");
            return List.of();
        }

        Map<String, Asset> byExternalId = new HashMap<>();
        activeAssets.forEach(asset -> byExternalId.put(asset.getExternalId(), asset));

        // Outside the transaction on purpose.
        Map<String, CryptoQuote> quotes = priceProvider.fetchPrices(byExternalId.keySet());
        if (quotes.isEmpty()) {
            log.warn("{} returned no usable quotes", priceProvider.providerName());
            return List.of();
        }

        List<PriceSnapshot> saved = transactionTemplate.execute(status -> {
            List<PriceSnapshot> batch = new ArrayList<>();
            quotes.forEach((externalId, quote) -> {
                Asset asset = byExternalId.get(externalId);
                if (asset != null) {
                    batch.add(new PriceSnapshot(asset, quote.priceUsd(), quote.change24hPercent()));
                }
            });
            List<PriceSnapshot> persisted = snapshotRepository.saveAll(batch);
            auditService.record("PRICES_REFRESHED", "PriceSnapshot", null,
                    "Saved " + persisted.size() + " snapshot(s) from " + priceProvider.providerName());
            return persisted;
        });

        List<PriceSnapshot> result = saved == null ? List.of() : saved;
        log.info("Refreshed {} price snapshot(s) from {}", result.size(), priceProvider.providerName());
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<PriceSnapshot> findLatestForAsset(Long assetId) {
        return snapshotRepository.findFirstByAssetIdOrderByCapturedAtDesc(assetId);
    }

    /** Latest snapshot per asset, keyed by asset id. Assets never priced are absent. */
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
    public List<PriceSnapshot> findHistory(Long assetId, int days) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", assetId);
        }
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return snapshotRepository.findByAssetIdAndCapturedAtAfterOrderByCapturedAtAsc(assetId, since);
    }

    /** Database write #3: drop snapshots older than the retention window. */
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
