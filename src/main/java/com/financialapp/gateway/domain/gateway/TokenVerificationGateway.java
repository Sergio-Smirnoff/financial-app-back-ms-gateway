package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.Principal;

/**
 * Verifies an access token and yields the authenticated {@link Principal}. A local capability
 * (no outbound call); the JWT implementation lives in {@code infrastructure.gateway.Impl}.
 * Throws {@code InvalidAccessTokenException} when the token is invalid or expired.
 */
public interface TokenVerificationGateway {
    Principal verify(AccessToken token);
}
