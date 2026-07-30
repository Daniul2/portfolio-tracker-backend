package com.kodilla.portfolio.service;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionRequest;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.PortfolioRepository;
import com.kodilla.portfolio.repository.TransactionRepository;
import com.kodilla.portfolio.service.valuation.HoldingCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AuditService auditService;

    private TransactionService service;

    private final User user = TestFixtures.user(1L, "demo");
    private final Portfolio portfolio = TestFixtures.portfolio(10L, user, "Main", "PLN");
    private final Asset bitcoin = TestFixtures.asset(100L, "bitcoin", "BTC");
    private final LocalDateTime past = LocalDateTime.now().minusDays(5);

    @BeforeEach
    void setUp() {
        // A real calculator: the sell-validation logic is what these tests check.
        service = new TransactionService(transactionRepository, portfolioRepository,
                assetRepository, new HoldingCalculator(), auditService);
    }

    private TransactionRequest request(TransactionType type, String quantity) {
        return new TransactionRequest(10L, 100L, type, new BigDecimal(quantity),
                new BigDecimal("50000"), BigDecimal.ZERO, past, "note");
    }

    @Test
    @DisplayName("records a buy")
    void recordsBuy() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            TestFixtures.setId(transaction, 1L);
            return transaction;
        });

        TransactionResponse response = service.create(request(TransactionType.BUY, "0.5"));

        assertThat(response.type()).isEqualTo(TransactionType.BUY);
        assertThat(response.grossValueUsd()).isEqualByComparingTo("25000");
        verify(auditService).record(eq("TRANSACTION_CREATED"), eq("Transaction"), eq(1L), anyString());
    }

    @Test
    @DisplayName("defaults a missing executedAt to now instead of rejecting")
    void defaultsExecutedAt() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            TestFixtures.setId(transaction, 1L);
            return transaction;
        });

        TransactionResponse response = service.create(new TransactionRequest(
                10L, 100L, TransactionType.BUY, BigDecimal.ONE,
                new BigDecimal("50000"), null, null, null));

        assertThat(response.executedAt()).isNotNull();
        assertThat(response.feeUsd()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("rejects a trade dated in the future")
    void rejectsFutureTrade() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));

        TransactionRequest future = new TransactionRequest(10L, 100L, TransactionType.BUY,
                BigDecimal.ONE, new BigDecimal("50000"), null,
                LocalDateTime.now().plusDays(1), null);

        assertThatThrownBy(() -> service.create(future))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("allows a sell covered by the current holding")
    void allowsCoveredSell() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(
                List.of(TestFixtures.buy(1L, portfolio, bitcoin, "2", "40000", past.minusDays(1))));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            TestFixtures.setId(transaction, 2L);
            return transaction;
        });

        assertThat(service.create(request(TransactionType.SELL, "1")).type())
                .isEqualTo(TransactionType.SELL);
    }

    @Test
    @DisplayName("rejects a sell larger than the holding")
    void rejectsOversizedSell() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(
                List.of(TestFixtures.buy(1L, portfolio, bitcoin, "0.5", "40000", past.minusDays(1))));

        assertThatThrownBy(() -> service.create(request(TransactionType.SELL, "2")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("only holds");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a sell when nothing is held at all")
    void rejectsSellWithNoHolding() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request(TransactionType.SELL, "1")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("editing an existing sell excludes it from its own coverage check")
    void updateExcludesItselfFromHoldingCheck() {
        Transaction existingBuy = TestFixtures.buy(1L, portfolio, bitcoin, "2", "40000", past.minusDays(2));
        Transaction existingSell = TestFixtures.sell(2L, portfolio, bitcoin, "1", "50000", past);

        when(transactionRepository.findById(2L)).thenReturn(Optional.of(existingSell));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L))
                .thenReturn(List.of(existingBuy, existingSell));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Raising the sell to 2 is valid against the 2 held once its own effect
        // is excluded; counting itself would leave only 1 and wrongly reject it.
        TransactionResponse response = service.update(2L, request(TransactionType.SELL, "2"));

        assertThat(response.quantity()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("still rejects an edit that exceeds the holding")
    void updateRejectsOversizedSell() {
        Transaction existingBuy = TestFixtures.buy(1L, portfolio, bitcoin, "2", "40000", past.minusDays(2));
        Transaction existingSell = TestFixtures.sell(2L, portfolio, bitcoin, "1", "50000", past);

        when(transactionRepository.findById(2L)).thenReturn(Optional.of(existingSell));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L))
                .thenReturn(List.of(existingBuy, existingSell));

        assertThatThrownBy(() -> service.update(2L, request(TransactionType.SELL, "5")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("updating a buy keeps the original date when none is supplied")
    void updateKeepsOriginalDate() {
        Transaction existing = TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", past);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(bitcoin));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = service.update(1L, new TransactionRequest(
                10L, 100L, TransactionType.BUY, new BigDecimal("3"),
                new BigDecimal("45000"), null, null, "corrected"));

        assertThat(response.executedAt()).isEqualTo(past);
        assertThat(response.quantity()).isEqualByComparingTo("3");
        assertThat(response.note()).isEqualTo("corrected");
    }

    @Test
    @DisplayName("deletes a transaction and audits it")
    void deletesTransaction() {
        Transaction existing = TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", past);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(transactionRepository).delete(existing);
        verify(auditService).record(eq("TRANSACTION_DELETED"), eq("Transaction"), eq(1L), anyString());
    }

    @Test
    @DisplayName("unknown ids are 404s")
    void unknownIdsAreNotFound() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new TransactionRequest(99L, 100L,
                TransactionType.BUY, BigDecimal.ONE, BigDecimal.TEN, null, past, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("creating against an unknown asset is a 404")
    void unknownAssetIsNotFound() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new TransactionRequest(10L, 99L,
                TransactionType.BUY, BigDecimal.ONE, BigDecimal.TEN, null, past, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listing for an unknown portfolio is a 404")
    void listForUnknownPortfolio() {
        when(portfolioRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByPortfolio(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lists a portfolio's transactions")
    void listsTransactions() {
        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(10L))
                .thenReturn(List.of(TestFixtures.buy(1L, portfolio, bitcoin, "1", "40000", past)));

        assertThat(service.findByPortfolio(10L)).hasSize(1);
    }
}
