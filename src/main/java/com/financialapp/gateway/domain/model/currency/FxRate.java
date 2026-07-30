package com.financialapp.gateway.domain.model.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRate(LocalDate date, FxRateMode mode, BigDecimal buy, BigDecimal sell) {
    public FxRate {
        if (date == null) {
            throw new IllegalArgumentException("date required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode required");
        }
        if (buy == null || sell == null) {
            throw new IllegalArgumentException("buy and sell rates required");
        }
    }
}
