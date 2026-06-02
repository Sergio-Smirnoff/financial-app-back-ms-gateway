package com.financialapp.gateway.domain.common.model;

public record UserId(Long value) {
    public UserId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("userId must be a positive identifier");
        }
    }
}
