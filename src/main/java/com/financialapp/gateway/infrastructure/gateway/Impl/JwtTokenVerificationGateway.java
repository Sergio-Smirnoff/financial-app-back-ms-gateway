package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.Principal;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.exception.InvalidAccessTokenException;
import com.financialapp.gateway.domain.gateway.TokenVerificationGateway;
import com.financialapp.gateway.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtTokenVerificationGateway implements TokenVerificationGateway {

    private final SecretKey signingKey;

    public JwtTokenVerificationGateway(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Principal verify(AccessToken token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(token.value()).getPayload();

            String type = claims.get("type", String.class);
            if (type != null && !"access".equals(type)) {
                throw new InvalidAccessTokenException("Token invalid or expired");
            }

            Long userId = claims.get("userId", Long.class);
            if (userId == null && claims.getSubject() != null) {
                userId = Long.parseLong(claims.getSubject());
            }
            if (userId == null) {
                throw new InvalidAccessTokenException("Token invalid or expired");
            }
            return new Principal(new UserId(userId));
        } catch (InvalidAccessTokenException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidAccessTokenException("Token invalid or expired");
        }
    }
}
