package com.kodilla.portfolio.service;

import com.kodilla.portfolio.TestFixtures;
import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.domain.User;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioRequest;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.repository.PortfolioRepository;
import com.kodilla.portfolio.repository.TransactionRepository;
import com.kodilla.portfolio.repository.UserRepository;
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
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private PortfolioService service;

    private final User user = TestFixtures.user(1L, "demo");

    @Test
    @DisplayName("creates a portfolio and upper-cases the currency")
    void createsPortfolio() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(portfolioRepository.existsByUserIdAndName(1L, "Main")).thenReturn(false);
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> {
            Portfolio portfolio = invocation.getArgument(0);
            TestFixtures.setId(portfolio, 7L);
            return portfolio;
        });

        PortfolioResponse response = service.create(new PortfolioRequest(1L, "Main", "pln"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.baseCurrency()).isEqualTo("PLN");
        assertThat(response.transactionCount()).isZero();
        verify(auditService).record(eq("PORTFOLIO_CREATED"), eq("Portfolio"), eq(7L), anyString());
    }

    @Test
    @DisplayName("creating for an unknown user is a 404")
    void createForUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new PortfolioRequest(99L, "Main", "PLN")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("one user cannot have two portfolios with the same name")
    void rejectsDuplicateNamePerUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(portfolioRepository.existsByUserIdAndName(1L, "Main")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new PortfolioRequest(1L, "Main", "PLN")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Main");
    }

    @Test
    @DisplayName("updates name and currency")
    void updatesPortfolio() {
        Portfolio existing = TestFixtures.portfolio(7L, user, "Main", "PLN");
        when(portfolioRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(portfolioRepository.existsByUserIdAndName(1L, "Renamed")).thenReturn(false);
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.countByPortfolioId(7L)).thenReturn(4L);

        PortfolioResponse response = service.update(7L, new PortfolioRequest(1L, "Renamed", "usd"));

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.baseCurrency()).isEqualTo("USD");
        assertThat(response.transactionCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("keeping the same name is not treated as a duplicate")
    void allowsKeepingOwnName() {
        Portfolio existing = TestFixtures.portfolio(7L, user, "Main", "PLN");
        when(portfolioRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.countByPortfolioId(7L)).thenReturn(0L);

        service.update(7L, new PortfolioRequest(1L, "Main", "USD"));

        verify(portfolioRepository, never()).existsByUserIdAndName(anyLong(), anyString());
    }

    @Test
    @DisplayName("rejects renaming onto another portfolio of the same user")
    void rejectsRenamingOntoExisting() {
        Portfolio existing = TestFixtures.portfolio(7L, user, "Main", "PLN");
        when(portfolioRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(portfolioRepository.existsByUserIdAndName(1L, "Other")).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, new PortfolioRequest(1L, "Other", "PLN")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("deletes a portfolio and audits it")
    void deletesPortfolio() {
        Portfolio existing = TestFixtures.portfolio(7L, user, "Main", "PLN");
        when(portfolioRepository.findById(7L)).thenReturn(Optional.of(existing));

        service.delete(7L);

        verify(portfolioRepository).delete(existing);
        verify(auditService).record(eq("PORTFOLIO_DELETED"), eq("Portfolio"), eq(7L), anyString());
    }

    @Test
    @DisplayName("listing by an unknown user is a 404, not an empty list")
    void listByUnknownUser() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lists a user's portfolios with transaction counts")
    void listsByUser() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(portfolioRepository.findByUserId(1L))
                .thenReturn(List.of(TestFixtures.portfolio(7L, user, "Main", "PLN")));
        when(transactionRepository.countByPortfolioId(7L)).thenReturn(6L);

        List<PortfolioResponse> portfolios = service.findByUser(1L);

        assertThat(portfolios).hasSize(1);
        assertThat(portfolios.get(0).transactionCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("lists every portfolio")
    void listsAll() {
        when(portfolioRepository.findAll())
                .thenReturn(List.of(TestFixtures.portfolio(7L, user, "Main", "PLN")));
        when(transactionRepository.countByPortfolioId(7L)).thenReturn(0L);

        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("an unknown portfolio id is a 404")
    void unknownIdIsNotFound() {
        when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.requirePortfolio(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
