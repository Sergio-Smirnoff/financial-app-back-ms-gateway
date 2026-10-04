package com.financialapp.gateway.domain.model.bff;

public record AssetTypeTotals(CurrencyAmounts marketValue, CurrencyAmounts cost, CurrencyAmounts pnl, int count) {

    public AssetTypeTotals {
        if (marketValue == null || cost == null || pnl == null) {
            throw new IllegalArgumentException("marketValue, cost and pnl required");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }

    public AssetTypeTotals plus(AssetTypeTotals other) {
        return new AssetTypeTotals(marketValue.plus(other.marketValue), cost.plus(other.cost), pnl.plus(other.pnl),
                count + other.count);
    }
}
