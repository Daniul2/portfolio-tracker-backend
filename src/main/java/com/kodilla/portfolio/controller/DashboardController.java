package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.DashboardDtos.DashboardResponse;
import com.kodilla.portfolio.dto.DashboardDtos.MarketRefreshResponse;
import com.kodilla.portfolio.facade.PortfolioFacade;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/dashboard")
public class DashboardController {

    private final PortfolioFacade portfolioFacade;

    public DashboardController(PortfolioFacade portfolioFacade) {
        this.portfolioFacade = portfolioFacade;
    }

    /** Everything the frontend's main screen needs, in one request. */
    @GetMapping("/{userId}")
    public DashboardResponse dashboard(@PathVariable Long userId) {
        return portfolioFacade.dashboardFor(userId);
    }

    /** Refreshes both external sources and re-runs the alert rules. */
    @PostMapping("/refresh")
    public MarketRefreshResponse refresh() {
        return portfolioFacade.refreshMarketData();
    }
}
