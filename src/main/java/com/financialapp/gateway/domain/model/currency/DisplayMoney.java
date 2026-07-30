package com.financialapp.gateway.domain.model.currency;

import java.math.BigDecimal;

public record DisplayMoney(BigDecimal amount, Currency currency) {
    public DisplayMoney {
        if (amount == null) {
            throw new IllegalArgumentException("amount required");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency required");
        }
    }
}
