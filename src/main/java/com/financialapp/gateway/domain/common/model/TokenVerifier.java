package com.financialapp.gateway.domain.common.model;

/** SPI: verifies a raw JWT and yields a Principal. Implemented in infrastructure (jjwt). */
@FunctionalInterface
public interface TokenVerifier {
    Principal verify(String rawToken);
}
