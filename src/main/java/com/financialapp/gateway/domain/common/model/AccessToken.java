package com.financialapp.gateway.domain.common.model;

public record AccessToken(String value) {
    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("access token must not be blank");
        }
    }
}
