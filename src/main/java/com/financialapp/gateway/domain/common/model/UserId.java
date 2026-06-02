package com.financialapp.gateway.domain.common.model;

/** Identity of the authenticated user. Reifies the bare {@code Long} carried as X-User-Id. */
public record UserId(Long value) {
    public UserId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("userId must be a positive identifier");
        }
    }
}
