package com.kodilla.portfolio.service;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.domain.PriceSnapshot;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.external.CryptoPriceProvider;
import com.kodilla.portfolio.external.CryptoQuote;
import com.kodilla.portfolio.external.ExternalApiException;
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.PriceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    private CryptoPriceProvider priceProvider;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private PriceSnapshotRepository snapshotRepository;
    @Mock
    private AuditService auditService;

    private PriceService service;

    private final Asset bitcoin = TestFixtures.asset(1L, "bitcoin", "BTC");
    private final Asset ethereum = TestFixtures.asset(2L, "ethereum", "ETH");

    @BeforeEach
    void setUp() {
        service = new PriceService(priceProvider, assetRepository, snapshotRepository, auditService,
                TestFixtures.noOpTransactionManager());
    }

    @Test
    @DisplayName("saves one snapshot per quote returned")
    void savesSnapshots() {
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(bitcoin, ethereum));
        when(priceProvider.fetchPrices(anyCollection())).thenReturn(Map.of(
                "bitcoin", new CryptoQuote("bitcoin", new BigDecimal("64000"), new BigDecimal("1.5")),
                "ethereum", new CryptoQuote("ethereum", new BigDecimal("1900"), new BigDecimal("-0.5"))));
        when(snapshotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(priceProvider.providerName()).thenReturn("CoinGecko");

        List<PriceSnapshot> saved = service.refreshPrices();

        assertThat(saved).hasSize(2);
        verify(auditService).record(eq("PRICES_REFRESHED"), eq("PriceSnapshot"), isNull(), anyString());
    }

    @Test
    @DisplayName("asks the provider only for the active assets")
    void requestsOnlyActiveAssets() {
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(bitcoin));
        when(priceProvider.fetchPrices(anyCollection())).thenReturn(Map.of(
                "bitcoin", new CryptoQuote("bitcoin", new BigDecimal("64000"), null)));
        when(snapshotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(priceProvider.providerName()).thenReturn("CoinGecko");

        service.refreshPrices();

        ArgumentCaptor<java.util.Collection<String>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(priceProvider).fetchPrices(captor.capture());
        assertThat(captor.getValue()).containsExactly("bitcoin");
    }

    @Test
    @DisplayName("does not call the provider when nothing is active")
    void skipsProviderWithoutActiveAssets() {
        when(assetRepository.findByActiveTrue()).thenReturn(List.of());

        assertThat(service.refreshPrices()).isEmpty();

        verifyNoInteractions(priceProvider);
        verify(snapshotRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("saves nothing when the provider returns no usable quotes")
    void handlesEmptyQuotes() {
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(bitcoin));
        when(priceProvider.fetchPrices(anyCollection())).thenReturn(Map.of());
        when(priceProvider.providerName()).thenReturn("CoinGecko");

        assertThat(service.refreshPrices()).isEmpty();

        verify(snapshotRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("ignores a quote for an id that is not in the local catalogue")
    void ignoresUnknownQuoteIds() {
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(bitcoin));
        when(priceProvider.fetchPrices(anyCollection())).thenReturn(Map.of(
                "bitcoin", new CryptoQuote("bitcoin", new BigDecimal("64000"), null),
                "surprise-coin", new CryptoQuote("surprise-coin", new BigDecimal("1"), null)));
        when(snapshotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(priceProvider.providerName()).thenReturn("CoinGecko");

        List<PriceSnapshot> saved = service.refreshPrices();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getAsset()).isEqualTo(bitcoin);
    }

    @Test
    @DisplayName("a provider failure propagates so callers can report it")
    void propagatesProviderFailure() {
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(bitcoin));
        when(priceProvider.fetchPrices(anyCollection()))
                .thenThrow(new ExternalApiException("CoinGecko", "down"));

        assertThatThrownBy(() -> service.refreshPrices())
                .isInstanceOf(ExternalApiException.class);

        verify(snapshotRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("latest prices are keyed by asset id and omit unpriced assets")
    void latestPricesOmitsUnpriced() {
        when(assetRepository.findAll()).thenReturn(List.of(bitcoin, ethereum));
        when(snapshotRepository.findFirstByAssetIdOrderByCapturedAtDesc(1L))
                .thenReturn(Optional.of(TestFixtures.snapshot(10L, bitcoin, "64000")));
        when(snapshotRepository.findFirstByAssetIdOrderByCapturedAtDesc(2L))
                .thenReturn(Optional.empty());

        Map<Long, PriceSnapshot> latest = service.findLatestPrices();

        assertThat(latest).containsOnlyKeys(1L);
    }

    @Test
    @DisplayName("history for an unknown asset is a 404, not an empty list")
    void historyRejectsUnknownAsset() {
        when(assetRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findHistory(99L, 7))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("history queries from the requested number of days back")
    void historyUsesRequestedWindow() {
        when(assetRepository.existsById(1L)).thenReturn(true);
        when(snapshotRepository.findByAssetIdAndCapturedAtAfterOrderByCapturedAtAsc(eq(1L), any()))
                .thenReturn(List.of());

        service.findHistory(1L, 30);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(snapshotRepository)
                .findByAssetIdAndCapturedAtAfterOrderByCapturedAtAsc(eq(1L), captor.capture());
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(29));
    }

    @Test
    @DisplayName("purging records an audit entry only when rows were actually removed")
    void purgeAuditsOnlyWhenSomethingRemoved() {
        when(snapshotRepository.deleteByCapturedAtBefore(any())).thenReturn(5L);

        assertThat(service.purgeOlderThan(90)).isEqualTo(5L);
        verify(auditService).record(eq("PRICES_PURGED"), eq("PriceSnapshot"), isNull(), anyString());

        reset(auditService);
        when(snapshotRepository.deleteByCapturedAtBefore(any())).thenReturn(0L);

        assertThat(service.purgeOlderThan(90)).isZero();
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("findLatestForAsset delegates to the repository")
    void findLatestForAssetDelegates() {
        when(snapshotRepository.findFirstByAssetIdOrderByCapturedAtDesc(1L))
                .thenReturn(Optional.of(TestFixtures.snapshot(10L, bitcoin, "64000")));

        assertThat(service.findLatestForAsset(1L)).isPresent();
    }
}
