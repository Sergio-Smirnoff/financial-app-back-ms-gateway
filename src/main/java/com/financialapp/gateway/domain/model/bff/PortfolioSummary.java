package com.financialapp.gateway.domain.model.bff;

import java.util.Map;

public record PortfolioSummary(
        CurrencyAmounts marketValue,
        CurrencyAmounts cost,
        Map<String, AssetTypeTotals> byAssetType
) {

    public PortfolioSummary {
        if (marketValue == null || cost == null || byAssetType == null) {
            throw new IllegalArgumentException("marketValue, cost and byAssetType required");
        }
        byAssetType = Map.copyOf(byAssetType);
    }

    public boolean needsUsdRate() {
        return marketValue.needsUsdRate() || cost.needsUsdRate();
    }
}
