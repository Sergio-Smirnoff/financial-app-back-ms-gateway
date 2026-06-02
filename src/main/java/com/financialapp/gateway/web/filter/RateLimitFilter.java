package com.financialapp.gateway.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialapp.gateway.domain.model.admission.RateLimitPolicy;
import com.financialapp.gateway.domain.model.admission.TokenBucket;
import com.financialapp.gateway.web.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/** Per-IP admission: delegates the allow/deny decision to a domain TokenBucket. */
@Slf4j
@Component
@Order(-1)
public class RateLimitFilter implements WebFilter {

    private static final long WINDOW_MS = 60_000L;

    private final RateLimitPolicy policy;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${rate-limit.requests-per-minute:600}") int requestsPerMinute,
                           ObjectMapper objectMapper) {
        this.policy = new RateLimitPolicy(requestsPerMinute, WINDOW_MS);
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String ip = resolveClientIp(exchange);
        TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket(policy));
        boolean allowed;
        synchronized (bucket) {
            allowed = bucket.tryAcquire(System.currentTimeMillis());
        }
        if (!allowed) {
            log.warn("Rate limit exceeded for IP: {}", ip);
            return writeError(exchange);
        }
        return chain.filter(exchange);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    private Mono<Void> writeError(ServerWebExchange exchange) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ApiResponse.error("Too many requests"));
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }
}
