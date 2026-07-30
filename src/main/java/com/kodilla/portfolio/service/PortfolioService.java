package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.domain.User;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioRequest;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.repository.PortfolioRepository;
import com.kodilla.portfolio.repository.TransactionRepository;
import com.kodilla.portfolio.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioService {

    private static final String ENTITY = "Portfolio";

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public PortfolioService(PortfolioRepository portfolioRepository,
                           UserRepository userRepository,
                           TransactionRepository transactionRepository,
                           AuditService auditService) {
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> findAll() {
        return portfolioRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> findByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return portfolioRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PortfolioResponse findById(Long id) {
        return toResponse(requirePortfolio(id));
    }

    /** Database write #14: create a portfolio. */
    @Transactional
    public PortfolioResponse create(PortfolioRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));

        if (portfolioRepository.existsByUserIdAndName(request.userId(), request.name())) {
            throw new DuplicateResourceException(
                    "That user already has a portfolio named '" + request.name() + "'");
        }

        Portfolio saved = portfolioRepository.save(
                new Portfolio(user, request.name(), request.baseCurrency().toUpperCase()));
        auditService.record("PORTFOLIO_CREATED", ENTITY, saved.getId(),
                "name=" + saved.getName() + ", userId=" + user.getId());
        return DtoMapper.toPortfolioResponse(saved, 0);
    }

    /** Database write #15: rename a portfolio or change its reporting currency. */
    @Transactional
    public PortfolioResponse update(Long id, PortfolioRequest request) {
        Portfolio portfolio = requirePortfolio(id);

        boolean nameChanged = !portfolio.getName().equals(request.name());
        if (nameChanged
                && portfolioRepository.existsByUserIdAndName(portfolio.getUser().getId(), request.name())) {
            throw new DuplicateResourceException(
                    "That user already has a portfolio named '" + request.name() + "'");
        }

        portfolio.setName(request.name());
        portfolio.setBaseCurrency(request.baseCurrency().toUpperCase());

        Portfolio saved = portfolioRepository.save(portfolio);
        auditService.record("PORTFOLIO_UPDATED", ENTITY, id,
                "name=" + saved.getName() + ", baseCurrency=" + saved.getBaseCurrency());
        return toResponse(saved);
    }

    /** Database write #16: delete a portfolio with its transactions and alerts. */
    @Transactional
    public void delete(Long id) {
        Portfolio portfolio = requirePortfolio(id);
        String name = portfolio.getName();
        portfolioRepository.delete(portfolio);
        auditService.record("PORTFOLIO_DELETED", ENTITY, id, "name=" + name);
    }

    /** Entity lookup used by services that need the managed instance. */
    @Transactional(readOnly = true)
    public Portfolio requirePortfolio(Long id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private PortfolioResponse toResponse(Portfolio portfolio) {
        return DtoMapper.toPortfolioResponse(portfolio,
                transactionRepository.countByPortfolioId(portfolio.getId()));
    }
}
