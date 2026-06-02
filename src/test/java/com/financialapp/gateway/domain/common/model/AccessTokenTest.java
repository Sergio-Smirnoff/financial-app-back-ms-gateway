package com.financialapp.gateway.domain.common.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessTokenTest {
    @Test void resolves_principal_from_a_valid_token() {
        TokenVerifier verifier = raw -> new Principal(new UserId(7L));
        Principal p = new AccessToken("good.jwt.value").resolve(verifier);
        assertEquals(7L, p.userId().value());
    }
    @Test void rejects_blank_token() {
        assertThrows(IllegalArgumentException.class, () -> new AccessToken("  "));
    }
    @Test void propagates_verifier_rejection() {
        TokenVerifier verifier = raw -> { throw new InvalidAccessTokenException("bad"); };
        assertThrows(InvalidAccessTokenException.class,
                () -> new AccessToken("x.y.z").resolve(verifier));
    }
}
