package com.financialapp.gateway.domain.common.model;

import java.time.Duration;

public record TimeoutPolicy(Duration perCall) {
    public TimeoutPolicy {
        if (perCall == null || perCall.isZero() || perCall.isNegative()) {
            throw new IllegalArgumentException("perCall timeout must be a positive duration");
        }
    }
}
