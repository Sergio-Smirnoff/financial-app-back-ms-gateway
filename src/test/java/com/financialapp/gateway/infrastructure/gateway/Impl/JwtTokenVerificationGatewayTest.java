package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.Principal;
import com.financialapp.gateway.domain.exception.InvalidAccessTokenException;
import com.financialapp.gateway.infrastructure.config.JwtProperties;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenVerificationGatewayTest {

    private static final String SECRET =
            "dGhpcyBpcyBhIGRldmVsb3BtZW50IHBsYWNlaG9sZGVyIHNlY3JldA==";

    private final JwtTokenVerificationGateway gateway = createGateway();

    private JwtTokenVerificationGateway createGateway() {
        JwtProperties props = new JwtProperties();
        props.setEnabled(true);
        props.setSecret(SECRET);
        return new JwtTokenVerificationGateway(props);
    }

    private String tokenWith(String type) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        JwtBuilder builder = Jwts.builder()
                .subject("42")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000));
        if (type != null) {
            builder.claim("type", type);
        }
        return builder.signWith(key).compact();
    }

    @Test
    void acceptsAnAccessTypedToken() {
        Principal principal = gateway.verify(new AccessToken(tokenWith("access")));
        assertThat(principal.userId().value()).isEqualTo(42L);
    }

    @Test
    void acceptsATokenWithNoTypeClaimDuringRollover() {
        Principal principal = gateway.verify(new AccessToken(tokenWith(null)));
        assertThat(principal.userId().value()).isEqualTo(42L);
    }

    @Test
    void rejectsARefreshTokenPresentedAsAnAccessToken() {
        assertThatThrownBy(() -> gateway.verify(new AccessToken(tokenWith("refresh"))))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void rejectsAnUnknownTokenType() {
        assertThatThrownBy(() -> gateway.verify(new AccessToken(tokenWith("something-else"))))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> gateway.verify(new AccessToken("not.a.jwt")))
                .isInstanceOf(InvalidAccessTokenException.class);
    }
}
