package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioSummaryResponse;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionRequest;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionResponse;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final PortfolioFacade portfolioFacade;

    public TransactionController(TransactionService transactionService,
                                 PortfolioFacade portfolioFacade) {
        this.transactionService = transactionService;
        this.portfolioFacade = portfolioFacade;
    }

    @GetMapping
    public List<TransactionResponse> findByPortfolio(@RequestParam Long portfolioId) {
        return transactionService.findByPortfolio(portfolioId);
    }

    @GetMapping("/{id}")
    public TransactionResponse findById(@PathVariable Long id) {
        return transactionService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse created = transactionService.create(request);
        return ResponseEntity.created(URI.create("/v1/transactions/" + created.id())).body(created);
    }

    /** Records the trade and returns the repriced portfolio in one call. */
    @PostMapping("/with-summary")
    public PortfolioSummaryResponse createAndSummarise(@Valid @RequestBody TransactionRequest request) {
        return portfolioFacade.recordTransactionAndRevalue(request);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id,
                                      @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        transactionService.delete(id);
    }
}
