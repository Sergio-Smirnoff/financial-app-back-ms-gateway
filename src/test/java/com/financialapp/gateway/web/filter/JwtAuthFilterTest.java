package com.financialapp.gateway.web.filter;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.Principal;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.exception.InvalidAccessTokenException;
import com.financialapp.gateway.domain.gateway.TokenVerificationGateway;
import com.financialapp.gateway.web.error.ErrorResponseRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private final TokenVerificationGateway tokenVerification = mock(TokenVerificationGateway.class);
    private final ErrorResponseRenderer errorRenderer = mock(ErrorResponseRenderer.class);
    private final WebFilterChain chain = mock(WebFilterChain.class);

    private final JwtAuthFilter filter = new JwtAuthFilter(true, tokenVerification, errorRenderer);

    @Test
    void allows_public_paths_without_cookie() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void rejects_protected_path_when_cookie_is_missing() {
        when(errorRenderer.render(any(), eq(HttpStatus.UNAUTHORIZED), any(), any()))
                .thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bff/overview").build());

        filter.filter(exchange, chain).block();

        verify(errorRenderer).render(eq(exchange), eq(HttpStatus.UNAUTHORIZED), any(), any());
    }

    @Test
    void injects_x_user_id_header_when_valid_token_provided() {
        when(tokenVerification.verify(new AccessToken("valid-token")))
                .thenReturn(new Principal(new UserId(42L)));
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
            return Mono.empty();
        });

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bff/overview")
                        .cookie(new HttpCookie("access_token", "valid-token"))
                        .build());

        filter.filter(exchange, chain).block();
    }

    @Test
    void rejects_invalid_token_with_401_envelope() {
        when(tokenVerification.verify(new AccessToken("invalid-token")))
                .thenThrow(new InvalidAccessTokenException("Token invalid"));
        when(errorRenderer.render(any(), eq(HttpStatus.UNAUTHORIZED), any(), any()))
                .thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bff/overview")
                        .cookie(new HttpCookie("access_token", "invalid-token"))
                        .build());

        filter.filter(exchange, chain).block();

        verify(errorRenderer).render(eq(exchange), eq(HttpStatus.UNAUTHORIZED), any(), any());
    }
}
