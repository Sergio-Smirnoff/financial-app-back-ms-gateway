package com.financialapp.gateway.domain.model.currency;

public record UserDisplayPreferences(
        Currency primaryCurrency,
        FxRateMode secondaryMode,
        String numberFormat,
        int decimals,
        boolean colorForAmounts) {

    public UserDisplayPreferences {
        if (primaryCurrency == null) {
            primaryCurrency = Currency.ARS;
        }
        if (numberFormat == null || numberFormat.isBlank()) {
            numberFormat = "1.234,56";
        }
        if (decimals < 0) {
            decimals = 2;
        }
    }
}
