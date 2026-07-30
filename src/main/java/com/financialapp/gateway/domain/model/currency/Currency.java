package com.financialapp.gateway.domain.model.currency;

public record Currency(String code) {
    public static final Currency ARS = new Currency("ARS");
    public static final Currency USD = new Currency("USD");
    public static final Currency EUR = new Currency("EUR");

    public Currency {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("currency code required");
        }
        code = code.toUpperCase().trim();
    }

    public static Currency of(String code) {
        return new Currency(code);
    }
}
