package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.currency.Currency;

import java.math.BigDecimal;

public record MoneyFigure(BigDecimal amount, Currency currency, MoneyFigure secondary) {
    public MoneyFigure {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (currency == null) {
            currency = Currency.ARS;
        }
    }

    public static MoneyFigure of(BigDecimal amount, Currency currency) {
        return new MoneyFigure(amount, currency, null);
    }

    public static MoneyFigure of(BigDecimal amount, Currency currency, MoneyFigure secondary) {
        return new MoneyFigure(amount, currency, secondary);
    }

    public static MoneyFigure zero(Currency currency) {
        return new MoneyFigure(BigDecimal.ZERO, currency, null);
    }
}
