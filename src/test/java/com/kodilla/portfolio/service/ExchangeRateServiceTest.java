package com.kodilla.portfolio.service;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.ExchangeRate;
import com.kodilla.portfolio.dto.CommonDtos.ExchangeRateResponse;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.external.ExchangeRateProvider;
import com.kodilla.portfolio.external.RateQuote;
import com.kodilla.portfolio.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateProvider rateProvider;
    @Mock
    private ExchangeRateRepository rateRepository;
    @Mock
    private AuditService auditService;

    private ExchangeRateService service;

    private final LocalDate today = LocalDate.of(2026, 7, 29);

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(rateProvider, rateRepository, auditService,
                TestFixtures.noOpTransactionManager());
    }

    @Test
    @DisplayName("stores each rate returned by the provider")
    void storesNewRates() {
        when(rateProvider.fetchLatestRates()).thenReturn(List.of(
                new RateQuote("USD", new BigDecimal("3.7962"), today),
                new RateQuote("EUR", new BigDecimal("4.3262"), today)));
        when(rateRepository.findByEffectiveDate(today)).thenReturn(List.of());
        when(rateRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.refreshRates()).isEqualTo(2);
        verify(auditService).record(eq("RATES_REFRESHED"), eq("ExchangeRate"), isNull(), anyString());
    }

    @Test
    @DisplayName("updates the existing row instead of inserting a duplicate for the same day")
    void updatesExistingRateForSameDay() {
        ExchangeRate existing = TestFixtures.rate(1L, "USD", "3.5000");
        when(rateProvider.fetchLatestRates())
                .thenReturn(List.of(new RateQuote("USD", new BigDecimal("3.7962"), today)));
        when(rateRepository.findByEffectiveDate(today)).thenReturn(List.of(existing));
        when(rateRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.refreshRates();

        ArgumentCaptor<List<ExchangeRate>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        // Same managed instance, new value: an update, not an insert.
        assertThat(captor.getValue().get(0)).isSameAs(existing);
        assertThat(captor.getValue().get(0).getRatePln()).isEqualByComparingTo("3.7962");
    }

    @Test
    @DisplayName("collapses a duplicated currency in the payload to one row")
    void deduplicatesProviderPayload() {
        when(rateProvider.fetchLatestRates()).thenReturn(List.of(
                new RateQuote("USD", new BigDecimal("3.7962"), today),
                new RateQuote("USD", new BigDecimal("3.8000"), today)));
        when(rateRepository.findByEffectiveDate(today)).thenReturn(List.of());
        when(rateRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.refreshRates();

        ArgumentCaptor<List<ExchangeRate>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        // First wins, so the result is deterministic.
        assertThat(captor.getValue().get(0).getRatePln()).isEqualByComparingTo("3.7962");
    }

    @Test
    @DisplayName("reads existing rates with a single query rather than one per currency")
    void queriesExistingRatesOnce() {
        when(rateProvider.fetchLatestRates()).thenReturn(List.of(
                new RateQuote("USD", new BigDecimal("3.79"), today),
                new RateQuote("EUR", new BigDecimal("4.32"), today),
                new RateQuote("CHF", new BigDecimal("4.65"), today)));
        when(rateRepository.findByEffectiveDate(today)).thenReturn(List.of());
        when(rateRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.refreshRates();

        verify(rateRepository, times(1)).findByEffectiveDate(today);
    }

    @Test
    @DisplayName("an empty provider response saves nothing")
    void handlesEmptyProviderResponse() {
        when(rateProvider.fetchLatestRates()).thenReturn(List.of());

        assertThat(service.refreshRates()).isZero();

        verify(rateRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("USD to USD is exactly one without consulting the database")
    void usdToUsdIsOne() {
        assertThat(service.usdToCurrencyRate("USD")).contains(BigDecimal.ONE);

        verifyNoInteractions(rateRepository);
    }

    @Test
    @DisplayName("a null target currency is treated as USD")
    void nullTargetIsUsd() {
        assertThat(service.usdToCurrencyRate(null)).contains(BigDecimal.ONE);
    }

    @Test
    @DisplayName("USD to PLN is the stored NBP rate directly")
    void usdToPlnUsesStoredRate() {
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("USD"))
                .thenReturn(Optional.of(TestFixtures.rate(1L, "USD", "3.7962")));

        assertThat(service.usdToCurrencyRate("PLN")).contains(new BigDecimal("3.7962"));
    }

    @Test
    @DisplayName("USD to a third currency is derived as a cross rate through PLN")
    void usdToOtherCurrencyIsCrossRate() {
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("USD"))
                .thenReturn(Optional.of(TestFixtures.rate(1L, "USD", "3.7962")));
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("EUR"))
                .thenReturn(Optional.of(TestFixtures.rate(2L, "EUR", "4.3262")));

        // 3.7962 PLN per USD / 4.3262 PLN per EUR = 0.87749064 EUR per USD
        assertThat(service.usdToCurrencyRate("EUR")).contains(new BigDecimal("0.87749064"));
    }

    @Test
    @DisplayName("the target currency is matched case-insensitively")
    void targetCurrencyIsCaseInsensitive() {
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("USD"))
                .thenReturn(Optional.of(TestFixtures.rate(1L, "USD", "3.7962")));

        assertThat(service.usdToCurrencyRate("pln")).contains(new BigDecimal("3.7962"));
    }

    @Test
    @DisplayName("returns empty when the USD leg has not been fetched yet")
    void emptyWithoutUsdRate() {
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("USD"))
                .thenReturn(Optional.empty());

        assertThat(service.usdToCurrencyRate("PLN")).isEmpty();
    }

    @Test
    @DisplayName("returns empty when the target leg is missing")
    void emptyWithoutTargetRate() {
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("USD"))
                .thenReturn(Optional.of(TestFixtures.rate(1L, "USD", "3.7962")));
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("GBP"))
                .thenReturn(Optional.empty());

        assertThat(service.usdToCurrencyRate("GBP")).isEmpty();
    }

    @Test
    @DisplayName("a zero target rate returns empty rather than dividing by zero")
    void guardsAgainstZeroRate() {
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("USD"))
                .thenReturn(Optional.of(TestFixtures.rate(1L, "USD", "3.7962")));
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("BAD"))
                .thenReturn(Optional.of(TestFixtures.rate(2L, "BAD", "0")));

        assertThat(service.usdToCurrencyRate("BAD")).isEmpty();
    }

    @Test
    @DisplayName("requireLatest throws a 404-mapped exception for an unknown currency")
    void requireLatestThrowsWhenMissing() {
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("XYZ"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireLatest("XYZ"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("XYZ");
    }

    @Test
    @DisplayName("requireLatest maps the newest stored rate to a response")
    void requireLatestMapsToResponse() {
        when(rateRepository.findFirstByCurrencyCodeOrderByEffectiveDateDesc("USD"))
                .thenReturn(Optional.of(TestFixtures.rate(1L, "USD", "3.7962")));

        ExchangeRateResponse response = service.requireLatest("usd");

        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.ratePln()).isEqualByComparingTo("3.7962");
    }

    @Test
    @DisplayName("findAll maps every stored rate to a response")
    void findAllMapsToResponses() {
        when(rateRepository.findAll()).thenReturn(List.of(TestFixtures.rate(1L, "USD", "3.79")));

        assertThat(service.findAll())
                .singleElement()
                .extracting(ExchangeRateResponse::currencyCode)
                .isEqualTo("USD");
    }
}
