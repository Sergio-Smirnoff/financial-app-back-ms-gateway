package com.financialapp.gateway.web.dto.response;

public record MoneyView(String amount, String currency, MoneyView secondary) {
    public static MoneyView of(String amount, String currency) {
        return new MoneyView(amount, currency, null);
    }

    public static MoneyView of(String amount, String currency, MoneyView secondary) {
        return new MoneyView(amount, currency, secondary);
    }
}
