package com.financialapp.gateway.domain.model.currency;

import java.math.BigDecimal;

public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount required");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency required");
        }
    }
}
