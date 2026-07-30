package com.kodilla.portfolio.repository;

import com.kodilla.portfolio.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByExternalId(String externalId);

    Optional<Asset> findBySymbolIgnoreCase(String symbol);

    List<Asset> findByActiveTrue();

    boolean existsByExternalId(String externalId);
}
