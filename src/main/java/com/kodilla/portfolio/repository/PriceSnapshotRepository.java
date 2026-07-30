package com.kodilla.portfolio.repository;

import com.kodilla.portfolio.domain.PriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findFirstByAssetIdOrderByCapturedAtDesc(Long assetId);

    List<PriceSnapshot> findByAssetIdAndCapturedAtAfterOrderByCapturedAtAsc(Long assetId, LocalDateTime after);

    long deleteByCapturedAtBefore(LocalDateTime cutoff);
}
