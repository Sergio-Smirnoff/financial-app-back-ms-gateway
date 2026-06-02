package com.financialapp.gateway.domain.model.admission;

/** Immutable rate-limit configuration: how many requests are allowed per rolling window. */
public record RateLimitPolicy(int capacity, long windowMillis) {
    public RateLimitPolicy {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis must be positive");
        }
    }
}
