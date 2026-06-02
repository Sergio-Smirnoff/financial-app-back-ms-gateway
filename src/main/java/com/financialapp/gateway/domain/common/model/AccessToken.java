package com.financialapp.gateway.domain.common.model;

/** The raw bearer token presented at the edge. Verified via the TokenVerificationGateway. */
public record AccessToken(String value) {
    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("access token must not be blank");
        }
    }
}
