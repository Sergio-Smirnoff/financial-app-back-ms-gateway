package com.financialapp.gateway.domain.common.model;

/** A validated identity, produced only from a successfully verified AccessToken. */
public record Principal(UserId userId) {
    public Principal {
        if (userId == null) {
            throw new IllegalArgumentException("principal requires a userId");
        }
    }
}
