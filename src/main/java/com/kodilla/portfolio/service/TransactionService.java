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
import com.kodilla.portfolio.repository.AssetRepository;
import com.kodilla.portfolio.repository.PortfolioRepository;
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
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final HoldingCalculator holdingCalculator;
    private final AuditService auditService;

    public TransactionService(TransactionRepository transactionRepository,
                              PortfolioRepository portfolioRepository,
                              AssetRepository assetRepository,
                              HoldingCalculator holdingCalculator,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.holdingCalculator = holdingCalculator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByPortfolio(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio", portfolioId);
        }
        return transactionRepository.findByPortfolioIdOrderByExecutedAtDesc(portfolioId).stream()
                .map(DtoMapper::toTransactionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(Long id) {
        return DtoMapper.toTransactionResponse(requireTransaction(id));
    }

    /** Database write #17: record a buy or sell. */
    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Portfolio portfolio = portfolioRepository.findById(request.portfolioId())
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", request.portfolioId()));
        Asset asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", request.assetId()));

        LocalDateTime executedAt = request.executedAt() == null ? LocalDateTime.now() : request.executedAt();
        if (executedAt.isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("executedAt cannot be in the future");
        }

        if (request.type() == TransactionType.SELL) {
            requireSufficientHolding(portfolio, asset, request.quantity(), null);
        }

        Transaction transaction = new Transaction(portfolio, asset, request.type(),
                request.quantity(), request.pricePerUnitUsd(), executedAt);
        transaction.setFeeUsd(request.feeUsd() == null ? BigDecimal.ZERO : request.feeUsd());
        transaction.setNote(request.note());

        Transaction saved = transactionRepository.save(transaction);
        auditService.record("TRANSACTION_CREATED", ENTITY, saved.getId(),
                "%s %s %s @ %s USD in portfolio %d".formatted(
                        saved.getType(), saved.getQuantity().toPlainString(),
                        asset.getSymbol(), saved.getPricePerUnitUsd().toPlainString(),
                        portfolio.getId()));
        return DtoMapper.toTransactionResponse(saved);
    }

    /** Database write #18: correct a previously recorded transaction. */
    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction transaction = requireTransaction(id);
        Asset asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", request.assetId()));

        LocalDateTime executedAt = request.executedAt() == null
                ? transaction.getExecutedAt() : request.executedAt();
        if (executedAt.isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("executedAt cannot be in the future");
        }

        if (request.type() == TransactionType.SELL) {
            // Exclude this transaction from the check so editing a sell in place
            // is not rejected by its own effect on the balance.
            requireSufficientHolding(transaction.getPortfolio(), asset, request.quantity(), id);
        }

        transaction.setAsset(asset);
        transaction.setType(request.type());
        transaction.setQuantity(request.quantity());
        transaction.setPricePerUnitUsd(request.pricePerUnitUsd());
        transaction.setFeeUsd(request.feeUsd() == null ? BigDecimal.ZERO : request.feeUsd());
        transaction.setExecutedAt(executedAt);
        transaction.setNote(request.note());

        Transaction saved = transactionRepository.save(transaction);
        auditService.record("TRANSACTION_UPDATED", ENTITY, id,
                "%s %s %s @ %s USD".formatted(saved.getType(), saved.getQuantity().toPlainString(),
                        asset.getSymbol(), saved.getPricePerUnitUsd().toPlainString()));
        return DtoMapper.toTransactionResponse(saved);
    }

    /** Database write #19: delete a transaction. */
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
