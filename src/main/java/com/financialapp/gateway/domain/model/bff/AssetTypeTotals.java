package com.financialapp.gateway.domain.model.bff;

public record AssetTypeTotals(CurrencyAmounts marketValue, CurrencyAmounts cost, int count) {

    public AssetTypeTotals {
        if (marketValue == null || cost == null) {
            throw new IllegalArgumentException("marketValue and cost required");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }

    public AssetTypeTotals plus(AssetTypeTotals other) {
        return new AssetTypeTotals(marketValue.plus(other.marketValue), cost.plus(other.cost), count + other.count);
    }
}
