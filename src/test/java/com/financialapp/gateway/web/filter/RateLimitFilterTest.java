package com.financialapp.gateway.web.filter;

import com.financialapp.gateway.web.error.ErrorResponseRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final ErrorResponseRenderer errorRenderer = mock(ErrorResponseRenderer.class);
    private final WebFilterChain chain = mock(WebFilterChain.class);

    @Test
    void allows_requests_under_capacity_and_blocks_when_exceeded() {
        AtomicLong now = new AtomicLong(1000L);
        RateLimitFilter filter = new RateLimitFilter(2, errorRenderer, now::get);

        when(chain.filter(any())).thenReturn(Mono.empty());
        when(errorRenderer.render(any(), eq(HttpStatus.TOO_MANY_REQUESTS), any(), any()))
                .thenReturn(Mono.empty());

        MockServerWebExchange req1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 80)).build());
        MockServerWebExchange req2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 80)).build());
        MockServerWebExchange req3 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 80)).build());

        filter.filter(req1, chain).block();
        filter.filter(req2, chain).block();
        filter.filter(req3, chain).block();

        verify(errorRenderer).render(eq(req3), eq(HttpStatus.TOO_MANY_REQUESTS), any(), any());
    }

    @Test
    void options_request_bypasses_rate_limit() {
        RateLimitFilter filter = new RateLimitFilter(1, errorRenderer, System::currentTimeMillis);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange optionsExchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/test").build());

        filter.filter(optionsExchange, chain).block();
        verify(chain).filter(optionsExchange);
    }

    @Test
    void evicts_idle_buckets_older_than_twice_window() {
        AtomicLong now = new AtomicLong(1000L);
        RateLimitFilter filter = new RateLimitFilter(600, errorRenderer, now::get);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange reqIpA = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 80)).build());
        filter.filter(reqIpA, chain).block();
        assertThat(filter.activeBucketCount()).isEqualTo(1);

        // Advance clock past 2 * 60_000ms (e.g. +130_000ms)
        now.addAndGet(130_000L);

        MockServerWebExchange reqIpB = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").remoteAddress(new java.net.InetSocketAddress("10.0.0.2", 80)).build());
        filter.filter(reqIpB, chain).block();

        // ip-A bucket should be evicted, only ip-B remains
        assertThat(filter.activeBucketCount()).isEqualTo(1);
    }
}
