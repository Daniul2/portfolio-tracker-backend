package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.Asset;
import com.kodilla.portfolio.domain.Portfolio;
import com.kodilla.portfolio.domain.Transaction;
import com.kodilla.portfolio.domain.TransactionType;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionRequest;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.repository.TransactionRepository;
import com.kodilla.portfolio.service.valuation.Holding;
import com.kodilla.portfolio.service.valuation.HoldingCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private static final String ENTITY = "Transaction";

    private final TransactionRepository transactionRepository;
    private final PortfolioService portfolioService;
    private final AssetService assetService;
    private final HoldingCalculator holdingCalculator;
    private final AuditService auditService;

    public TransactionService(TransactionRepository transactionRepository,
                              PortfolioService portfolioService,
                              AssetService assetService,
                              HoldingCalculator holdingCalculator,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.portfolioService = portfolioService;
        this.assetService = assetService;
        this.holdingCalculator = holdingCalculator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByPortfolio(Long portfolioId) {
        portfolioService.requirePortfolio(portfolioId);
        return transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(portfolioId).stream()
                .map(DtoMapper::toTransactionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(Long id) {
        return DtoMapper.toTransactionResponse(requireTransaction(id));
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Portfolio portfolio = portfolioService.requirePortfolio(request.portfolioId());
        Asset asset = assetService.requireAsset(request.assetId());
        LocalDateTime executedAt = requireNotInFuture(
                request.executedAt() == null ? LocalDateTime.now() : request.executedAt());

        if (request.type() == TransactionType.SELL) {
            requireSufficientHolding(portfolio, asset, request.quantity(), null);
        }

        Transaction transaction = new Transaction(portfolio, asset, request.type(),
                request.quantity(), request.pricePerUnitUsd(), executedAt);
        transaction.setFeeUsd(feeOrZero(request));
        transaction.setNote(request.note());

        Transaction saved = transactionRepository.save(transaction);
        auditService.record("TRANSACTION_CREATED", ENTITY, saved.getId(),
                describe(saved) + " in portfolio " + portfolio.getId());
        return DtoMapper.toTransactionResponse(saved);
    }

    /** Corrects a recorded transaction; an omitted date keeps the original one. */
    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction transaction = requireTransaction(id);
        Asset asset = assetService.requireAsset(request.assetId());
        LocalDateTime executedAt = requireNotInFuture(
                request.executedAt() == null ? transaction.getExecutedAt() : request.executedAt());

        if (request.type() == TransactionType.SELL) {
            // Exclude this transaction from the check so editing a sell in place
            // is not rejected by its own effect on the balance.
            requireSufficientHolding(transaction.getPortfolio(), asset, request.quantity(), id);
        }

        transaction.setAsset(asset);
        transaction.setType(request.type());
        transaction.setQuantity(request.quantity());
        transaction.setPricePerUnitUsd(request.pricePerUnitUsd());
        transaction.setFeeUsd(feeOrZero(request));
        transaction.setExecutedAt(executedAt);
        transaction.setNote(request.note());

        Transaction saved = transactionRepository.save(transaction);
        auditService.record("TRANSACTION_UPDATED", ENTITY, id, describe(saved));
        return DtoMapper.toTransactionResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Transaction transaction = requireTransaction(id);
        Long portfolioId = transaction.getPortfolio().getId();
        transactionRepository.delete(transaction);
        auditService.record("TRANSACTION_DELETED", ENTITY, id, "portfolioId=" + portfolioId);
    }

    private Transaction requireTransaction(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private static LocalDateTime requireNotInFuture(LocalDateTime executedAt) {
        if (executedAt.isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("executedAt cannot be in the future");
        }
        return executedAt;
    }

    private static BigDecimal feeOrZero(TransactionRequest request) {
        return request.feeUsd() == null ? BigDecimal.ZERO : request.feeUsd();
    }

    private static String describe(Transaction transaction) {
        return "%s %s %s @ %s USD".formatted(
                transaction.getType(), transaction.getQuantity().toPlainString(),
                transaction.getAsset().getSymbol(), transaction.getPricePerUnitUsd().toPlainString());
    }

    /**
     * Rejects a sell larger than the position actually held, which would otherwise create a phantom
     * negative holding.
     */
    private void requireSufficientHolding(Portfolio portfolio, Asset asset,
                                          BigDecimal quantityToSell, Long excludeTransactionId) {
        List<Transaction> existing = transactionRepository
                .findByPortfolioIdOrderByExecutedAtDesc(portfolio.getId()).stream()
                .filter(t -> excludeTransactionId == null || !excludeTransactionId.equals(t.getId()))
                .toList();

        BigDecimal held = holdingCalculator.calculate(existing).stream()
                .filter(holding -> holding.asset().getExternalId().equals(asset.getExternalId()))
                .map(Holding::quantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        if (held.compareTo(quantityToSell) < 0) {
            throw new BusinessRuleException("Cannot sell %s %s: portfolio only holds %s"
                    .formatted(quantityToSell.toPlainString(), asset.getSymbol(), held.toPlainString()));
        }
    }
}
