package com.financialapp.gateway.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Percentages {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Percentages() {
    }

    public static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_EVEN);
    }
}
