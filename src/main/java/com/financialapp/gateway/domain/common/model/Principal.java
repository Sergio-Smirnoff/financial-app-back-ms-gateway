package com.financialapp.gateway.domain.common.model;

public record Principal(UserId userId) {
    public Principal {
        if (userId == null) {
            throw new IllegalArgumentException("principal requires a userId");
        }
    }
}
