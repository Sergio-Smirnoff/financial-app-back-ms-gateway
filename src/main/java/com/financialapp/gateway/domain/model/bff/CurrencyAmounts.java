package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.currency.Currency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public record CurrencyAmounts(Map<Currency, BigDecimal> amounts) {

    public CurrencyAmounts {
        if (amounts == null) {
            throw new IllegalArgumentException("amounts required");
        }
        amounts = Map.copyOf(amounts);
    }

    public static CurrencyAmounts none() {
        return new CurrencyAmounts(Map.of());
    }

    public CurrencyAmounts plus(Currency currency, BigDecimal amount) {
        Map<Currency, BigDecimal> merged = new HashMap<>(amounts);
        merged.merge(currency, amount, BigDecimal::add);
        return new CurrencyAmounts(merged);
    }

    public CurrencyAmounts plus(CurrencyAmounts other) {
        CurrencyAmounts result = this;
        for (Map.Entry<Currency, BigDecimal> entry : other.amounts.entrySet()) {
            result = result.plus(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public boolean needsUsdRate() {
        return amounts.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(Currency.ARS) && entry.getValue().signum() != 0);
    }
}
