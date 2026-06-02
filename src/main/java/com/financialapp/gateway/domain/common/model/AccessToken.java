package com.financialapp.gateway.domain.common.model;

/** The raw bearer token presented at the edge. Knows how to resolve itself to a Principal. */
public record AccessToken(String value) {
    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("access token must not be blank");
        }
    }

    public Principal resolve(TokenVerifier verifier) {
        return verifier.verify(value);
    }
}
