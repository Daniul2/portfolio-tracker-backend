package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioRequest;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioSummaryResponse;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionResponse;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.service.PortfolioService;
import com.kodilla.portfolio.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final PortfolioFacade portfolioFacade;

    public PortfolioController(PortfolioService portfolioService,
                               TransactionService transactionService,
                               PortfolioFacade portfolioFacade) {
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.portfolioFacade = portfolioFacade;
    }

    /** Endpoint 7. Optionally filtered to one user. */
    @GetMapping
    public List<PortfolioResponse> findAll(@RequestParam(required = false) Long userId) {
        return userId == null ? portfolioService.findAll() : portfolioService.findByUser(userId);
    }

    /** Endpoint 8. */
    @GetMapping("/{id}")
    public PortfolioResponse findById(@PathVariable Long id) {
        return portfolioService.findById(id);
    }

    /** Endpoint 9. Full valuation: holdings, profit/loss, value in base currency. */
    @GetMapping("/{id}/summary")
    public PortfolioSummaryResponse summary(@PathVariable Long id) {
        return portfolioFacade.summarise(id);
    }

    /** Endpoint 10. */
    @GetMapping("/{id}/transactions")
    public List<TransactionResponse> transactions(@PathVariable Long id) {
        return transactionService.findByPortfolio(id);
    }

    /** Endpoint 11. */
    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@Valid @RequestBody PortfolioRequest request) {
        PortfolioResponse created = portfolioService.create(request);
        return ResponseEntity.created(URI.create("/v1/portfolios/" + created.id())).body(created);
    }

    /** Endpoint 12. */
    @PutMapping("/{id}")
    public PortfolioResponse update(@PathVariable Long id,
                                    @Valid @RequestBody PortfolioRequest request) {
        return portfolioService.update(id, request);
    }

    /** Endpoint 13. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        portfolioService.delete(id);
    }
}
