package com.kodilla.portfolio.mapper;

import com.kodilla.portfolio.domain.*;
import com.kodilla.portfolio.dto.AlertDtos.AlertEventResponse;
import com.kodilla.portfolio.dto.AlertDtos.AlertResponse;
import com.kodilla.portfolio.dto.AssetDtos.AssetResponse;
import com.kodilla.portfolio.dto.AssetDtos.PriceSnapshotResponse;
import com.kodilla.portfolio.dto.CommonDtos.AuditLogResponse;
import com.kodilla.portfolio.dto.CommonDtos.ExchangeRateResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.HoldingResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.kodilla.portfolio.dto.PortfolioDtos.PortfolioSummaryResponse;
import com.kodilla.portfolio.dto.TransactionDtos.TransactionResponse;
import com.kodilla.portfolio.dto.UserDtos.UserResponse;
import com.kodilla.portfolio.service.valuation.HoldingValuation;
import com.kodilla.portfolio.service.valuation.PortfolioValuation;

import java.util.List;

/**
 * Entity to response-DTO conversion, kept in one place so the API shape is defined by exactly one
 * file.
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static UserResponse toUserResponse(User user, long portfolioCount) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                (int) portfolioCount);
    }

    public static PortfolioResponse toPortfolioResponse(Portfolio portfolio, long transactionCount) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getUser().getId(),
                portfolio.getName(),
                portfolio.getBaseCurrency(),
                portfolio.getCreatedAt(),
                (int) transactionCount);
    }

    public static AssetResponse toAssetResponse(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getExternalId(),
                asset.getSymbol(),
                asset.getName(),
                asset.isActive(),
                asset.getCreatedAt());
    }

    public static TransactionResponse toTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getPortfolio().getId(),
                transaction.getAsset().getId(),
                transaction.getAsset().getSymbol(),
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getPricePerUnitUsd(),
                transaction.getFeeUsd(),
                transaction.grossValueUsd(),
                transaction.getExecutedAt(),
                transaction.getNote());
    }

    public static AlertResponse toAlertResponse(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getPortfolio().getId(),
                alert.getAsset() == null ? null : alert.getAsset().getId(),
                alert.getAsset() == null ? null : alert.getAsset().getSymbol(),
                alert.getType(),
                alert.getThreshold(),
                alert.isActive(),
                alert.getCreatedAt(),
                alert.getLastTriggeredAt());
    }

    public static AlertEventResponse toAlertEventResponse(AlertEvent event) {
        return new AlertEventResponse(
                event.getId(),
                event.getAlert().getId(),
                event.getAlert().getPortfolio().getId(),
                event.getMessage(),
                event.getValueAtTrigger(),
                event.isAcknowledged(),
                event.getCreatedAt());
    }

    public static PriceSnapshotResponse toPriceSnapshotResponse(PriceSnapshot snapshot) {
        return new PriceSnapshotResponse(
                snapshot.getId(),
                snapshot.getAsset().getId(),
                snapshot.getAsset().getSymbol(),
                snapshot.getPriceUsd(),
                snapshot.getChange24hPercent(),
                snapshot.getCapturedAt());
    }

    public static ExchangeRateResponse toExchangeRateResponse(ExchangeRate rate) {
        return new ExchangeRateResponse(
                rate.getId(),
                rate.getCurrencyCode(),
                rate.getRatePln(),
                rate.getEffectiveDate(),
                rate.getFetchedAt());
    }

    public static AuditLogResponse toAuditLogResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getDetails(),
                auditLog.getCreatedAt());
    }

    public static HoldingResponse toHoldingResponse(HoldingValuation valuation) {
        Asset asset = valuation.holding().asset();
        return new HoldingResponse(
                asset.getId(),
                asset.getSymbol(),
                asset.getName(),
                valuation.holding().quantity(),
                valuation.holding().averageCostUsd(),
                valuation.holding().costBasisUsd(),
                valuation.currentPriceUsd(),
                valuation.marketValueUsd(),
                valuation.unrealizedPnlUsd(),
                valuation.unrealizedPnlPercent(),
                valuation.marketValueBase(),
                valuation.isPriced());
    }

    public static PortfolioSummaryResponse toPortfolioSummaryResponse(PortfolioValuation valuation) {
        List<HoldingResponse> holdings = valuation.holdings().stream()
                .map(DtoMapper::toHoldingResponse)
                .toList();
        return new PortfolioSummaryResponse(
                valuation.portfolioId(),
                valuation.portfolioName(),
                valuation.baseCurrency(),
                valuation.totalCostUsd(),
                valuation.totalValueUsd(),
                valuation.totalPnlUsd(),
                valuation.totalPnlPercent(),
                valuation.fxRate(),
                valuation.totalValueBase(),
                holdings,
                valuation.valuedAt());
    }
}
