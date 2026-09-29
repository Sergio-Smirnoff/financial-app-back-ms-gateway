package com.financialapp.gateway.domain.model.bff;

import java.util.Map;

public record PortfolioSummary(
        CurrencyAmounts marketValue,
        CurrencyAmounts cost,
        Map<String, CurrencyAmounts> marketValueByAssetType
) {

    public PortfolioSummary {
        if (marketValue == null || cost == null || marketValueByAssetType == null) {
            throw new IllegalArgumentException("marketValue, cost and marketValueByAssetType required");
        }
        marketValueByAssetType = Map.copyOf(marketValueByAssetType);
    }

    public boolean needsUsdRate() {
        return marketValue.needsUsdRate() || cost.needsUsdRate();
    }
}
