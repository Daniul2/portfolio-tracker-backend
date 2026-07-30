package com.kodilla.portfolio.service;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.dto.AssetDtos.AssetRequest;
import com.kodilla.portfolio.dto.AssetDtos.AssetResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private AssetService service;

    @Test
    @DisplayName("creates an asset and upper-cases the symbol")
    void createsAssetNormalisingSymbol() {
        when(assetRepository.existsByExternalId("bitcoin")).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            TestFixtures.setId(asset, 1L);
            return asset;
        });

        AssetResponse response = service.create(new AssetRequest("bitcoin", "btc", "Bitcoin"));

        assertThat(response.symbol()).isEqualTo("BTC");
        verify(auditService).record(eq("ASSET_CREATED"), eq("Asset"), eq(1L), anyString());
    }

    @Test
    @DisplayName("rejects tracking the same external id twice")
    void rejectsDuplicateExternalId() {
        when(assetRepository.existsByExternalId("bitcoin")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AssetRequest("bitcoin", "BTC", "Bitcoin")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("updates an asset")
    void updatesAsset() {
        Asset existing = TestFixtures.asset(1L, "bitcoin", "BTC");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetResponse response = service.update(1L,
                new AssetRequest("bitcoin", "btc", "Bitcoin (BTC)"));

        assertThat(response.name()).isEqualTo("Bitcoin (BTC)");
        assertThat(response.symbol()).isEqualTo("BTC");
    }

    @Test
    @DisplayName("keeping the same external id on update is not a duplicate")
    void allowsKeepingOwnExternalId() {
        Asset existing = TestFixtures.asset(1L, "bitcoin", "BTC");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, new AssetRequest("bitcoin", "BTC", "Bitcoin"));

        verify(assetRepository, never()).existsByExternalId(anyString());
    }

    @Test
    @DisplayName("rejects moving to an external id another asset already uses")
    void rejectsTakingAnotherExternalId() {
        Asset existing = TestFixtures.asset(1L, "bitcoin", "BTC");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(assetRepository.existsByExternalId("ethereum")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L,
                new AssetRequest("ethereum", "ETH", "Ethereum")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("deactivating stops the scheduler pricing an asset")
    void deactivatesAsset() {
        Asset existing = TestFixtures.asset(1L, "bitcoin", "BTC");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetResponse response = service.setActive(1L, false);

        assertThat(response.active()).isFalse();
        verify(auditService).record(eq("ASSET_DEACTIVATED"), eq("Asset"), eq(1L), anyString());
    }

    @Test
    @DisplayName("reactivating audits with the matching action name")
    void reactivatesAsset() {
        Asset existing = TestFixtures.asset(1L, "bitcoin", "BTC");
        existing.setActive(false);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.setActive(1L, true).active()).isTrue();
        verify(auditService).record(eq("ASSET_ACTIVATED"), eq("Asset"), eq(1L), anyString());
    }

    @Test
    @DisplayName("deletes an unreferenced asset")
    void deletesUnreferencedAsset() {
        Asset existing = TestFixtures.asset(1L, "bitcoin", "BTC");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.countByAssetId(1L)).thenReturn(0L);

        service.delete(1L);

        verify(assetRepository).delete(existing);
    }

    @Test
    @DisplayName("refuses to delete an asset that transactions reference")
    void refusesToDeleteReferencedAsset() {
        Asset existing = TestFixtures.asset(1L, "bitcoin", "BTC");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.countByAssetId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("3 transaction(s)")
                .hasMessageContaining("Deactivate");

        verify(assetRepository, never()).delete(any());
    }

    @Test
    @DisplayName("lists all assets and just the active ones")
    void listsAssets() {
        when(assetRepository.findAll()).thenReturn(List.of(
                TestFixtures.asset(1L, "bitcoin", "BTC"),
                TestFixtures.asset(2L, "ethereum", "ETH")));
        when(assetRepository.findByActiveTrue())
                .thenReturn(List.of(TestFixtures.asset(1L, "bitcoin", "BTC")));

        assertThat(service.findAll()).hasSize(2);
        assertThat(service.findActive()).hasSize(1);
    }

    @Test
    @DisplayName("finds an asset by symbol, case-insensitively")
    void findsBySymbol() {
        when(assetRepository.findBySymbolIgnoreCase("btc"))
                .thenReturn(Optional.of(TestFixtures.asset(1L, "bitcoin", "BTC")));

        assertThat(service.findBySymbol("btc").symbol()).isEqualTo("BTC");
    }

    @Test
    @DisplayName("an unknown symbol is a 404")
    void unknownSymbolIsNotFound() {
        when(assetRepository.findBySymbolIgnoreCase("zzz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBySymbol("zzz"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("an unknown id is a 404")
    void unknownIdIsNotFound() {
        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
