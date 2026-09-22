package com.financialapp.gateway.application.bff.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

final class CardFigures {

    private CardFigures() {
    }

    static BigDecimal usedAmount(Map<String, Object> card) {
        return toDecimal(card.get("usedAmount"));
    }

    static BigDecimal usedPercent(Map<String, Object> card, BigDecimal creditLimit) {
        BigDecimal reported = toDecimal(card.get("usedPercent"));
        if (reported.signum() != 0) {
            return reported;
        }
        BigDecimal used = usedAmount(card);
        return creditLimit.signum() > 0
                ? used.divide(creditLimit, 4, RoundingMode.HALF_EVEN).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
