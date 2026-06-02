package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.Principal;
import com.financialapp.gateway.domain.exception.InvalidAccessTokenException;
import com.financialapp.gateway.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenVerificationGatewayTest {

    private static final String SECRET =
            "dGhpcyBpcyBhIGRldmVsb3BtZW50IHBsYWNlaG9sZGVyIHNlY3JldA==";

    private JwtTokenVerificationGateway gateway() {
        JwtProperties props = new JwtProperties();
        props.setEnabled(true);
        props.setSecret(SECRET);
        return new JwtTokenVerificationGateway(props);
    }

    private String tokenWithUserId(long id) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().claims(Map.of("userId", id))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test void verifies_and_extracts_userId() {
        Principal p = gateway().verify(new AccessToken(tokenWithUserId(99L)));
        assertEquals(99L, p.userId().value());
    }

    @Test void rejects_garbage_token() {
        assertThrows(InvalidAccessTokenException.class,
                () -> gateway().verify(new AccessToken("not.a.jwt")));
    }
}
