package com.financialapp.gateway.domain.model.admission;

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
