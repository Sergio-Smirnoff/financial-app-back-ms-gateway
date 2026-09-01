package com.financialapp.gateway.domain.model.composition;

import java.time.Duration;

public record PageTimeoutBudget(Duration total) {
    private static final Duration MAX_BUDGET = Duration.ofSeconds(30);

    public PageTimeoutBudget {
        if (total == null || total.isNegative() || total.isZero()) {
            throw new IllegalArgumentException("positive total duration required");
        }
        if (total.compareTo(MAX_BUDGET) > 0) {
            throw new IllegalArgumentException("PageTimeoutBudget cannot exceed 30s");
        }
    }

    public static PageTimeoutBudget fromMillis(long millis) {
        return new PageTimeoutBudget(Duration.ofMillis(millis));
    }
}
