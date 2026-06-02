package com.financialapp.gateway.web.filter;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.InvalidAccessTokenException;
import com.financialapp.gateway.domain.common.model.Principal;
import com.financialapp.gateway.domain.gateway.TokenVerificationGateway;
import com.financialapp.gateway.web.error.ErrorResponseRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@Order(-2)
public class JwtAuthFilter implements WebFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
            "/actuator", "/actuator/**", "/webjars/**");

    private final boolean enabled;
    private final TokenVerificationGateway tokenVerification;
    private final ErrorResponseRenderer errorRenderer;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(@Value("${jwt.enabled:true}") boolean enabled,
                         TokenVerificationGateway tokenVerification,
                         ErrorResponseRenderer errorRenderer) {
        this.enabled = enabled;
        this.tokenVerification = tokenVerification;
        this.errorRenderer = errorRenderer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        var cookie = exchange.getRequest().getCookies().getFirst("access_token");
        if (cookie == null || cookie.getValue().isBlank()) {
            return errorRenderer.render(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        try {
            Principal principal = tokenVerification.verify(new AccessToken(cookie.getValue()));
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", principal.userId().value().toString()).build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (InvalidAccessTokenException | IllegalArgumentException e) {
            log.warn("JWT rejected for {}: {}", path, e.getMessage());
            return errorRenderer.render(exchange, HttpStatus.UNAUTHORIZED, "Token invalid or expired");
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(p -> pathMatcher.match(p, path));
    }
}
