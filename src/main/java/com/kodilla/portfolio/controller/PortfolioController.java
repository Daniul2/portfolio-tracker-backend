package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioRequest;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioSummaryResponse;
import com.kodilla.portfolio.facade.PortfolioFacade;
import com.kodilla.portfolio.service.PortfolioService;
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
    private final PortfolioFacade portfolioFacade;

    public PortfolioController(PortfolioService portfolioService,
                               PortfolioFacade portfolioFacade) {
        this.portfolioService = portfolioService;
        this.portfolioFacade = portfolioFacade;
    }

    /** Optionally filtered to one user. */
    @GetMapping
    public List<PortfolioResponse> findAll(@RequestParam(required = false) Long userId) {
        return userId == null ? portfolioService.findAll() : portfolioService.findByUser(userId);
    }

    @GetMapping("/{id}")
    public PortfolioResponse findById(@PathVariable Long id) {
        return portfolioService.findById(id);
    }

    /** Full valuation: holdings, profit/loss, value in base currency. */
    @GetMapping("/{id}/summary")
    public PortfolioSummaryResponse summary(@PathVariable Long id) {
        return portfolioFacade.summarise(id);
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@Valid @RequestBody PortfolioRequest request) {
        PortfolioResponse created = portfolioService.create(request);
        return ResponseEntity.created(URI.create("/v1/portfolios/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public PortfolioResponse update(@PathVariable Long id,
                                    @Valid @RequestBody PortfolioRequest request) {
        return portfolioService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        portfolioService.delete(id);
    }
}
