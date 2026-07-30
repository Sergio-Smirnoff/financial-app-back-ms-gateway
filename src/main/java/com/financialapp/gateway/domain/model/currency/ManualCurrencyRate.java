package com.financialapp.gateway.domain.model.currency;

import java.math.BigDecimal;

public record ManualCurrencyRate(Currency currency, BigDecimal ratePerArs) {
    public ManualCurrencyRate {
        if (currency == null) {
            throw new IllegalArgumentException("currency required");
        }
        if (ratePerArs == null || ratePerArs.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("positive ratePerArs required");
        }
    }
}
