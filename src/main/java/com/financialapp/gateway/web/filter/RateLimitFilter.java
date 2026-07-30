package com.financialapp.gateway.web.filter;

import com.financialapp.gateway.domain.exception.DomainErrorCode;
import com.financialapp.gateway.domain.model.admission.RateLimitPolicy;
import com.financialapp.gateway.domain.model.admission.TokenBucket;
import com.financialapp.gateway.web.error.ErrorResponseRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@Component
@Order(-1)
public class RateLimitFilter implements WebFilter {

    private static final long WINDOW_MS = 60_000L;

    private final RateLimitPolicy policy;
    private final ErrorResponseRenderer errorRenderer;
    private final Supplier<Long> timeSupplier;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis = new AtomicLong(0);

    public RateLimitFilter(@Value("${rate-limit.requests-per-minute:600}") int requestsPerMinute,
                            ErrorResponseRenderer errorRenderer) {
        this(requestsPerMinute, errorRenderer, System::currentTimeMillis);
    }

    public RateLimitFilter(int requestsPerMinute,
                            ErrorResponseRenderer errorRenderer,
                            Supplier<Long> timeSupplier) {
        this.policy = new RateLimitPolicy(requestsPerMinute, WINDOW_MS);
        this.errorRenderer = errorRenderer;
        this.timeSupplier = timeSupplier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        long now = timeSupplier.get();
        maybeEvictIdleBuckets(now);

        String ip = resolveClientIp(exchange);
        TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket(policy, now));
        boolean allowed;
        synchronized (bucket) {
            allowed = bucket.tryAcquire(now);
        }
        if (!allowed) {
            log.warn("Rate limit exceeded for IP: {}", ip);
            return errorRenderer.render(exchange, HttpStatus.TOO_MANY_REQUESTS, DomainErrorCode.RATE_LIMITED, "Too many requests");
        }
        return chain.filter(exchange);
    }

    private void maybeEvictIdleBuckets(long now) {
        long lastSweep = lastSweepMillis.get();
        if (now - lastSweep >= WINDOW_MS) {
            if (lastSweepMillis.compareAndSet(lastSweep, now)) {
                for (String key : List.copyOf(buckets.keySet())) {
                    buckets.computeIfPresent(key, (ignoredKey, bucket) -> {
                        synchronized (bucket) {
                            return now - bucket.lastHitMillis() > 2 * WINDOW_MS ? null : bucket;
                        }
                    });
                }
            }
        }
    }

    int activeBucketCount() {
        return buckets.size();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }
}
