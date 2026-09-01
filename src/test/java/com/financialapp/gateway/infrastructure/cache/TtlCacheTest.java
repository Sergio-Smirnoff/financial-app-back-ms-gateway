package com.financialapp.gateway.infrastructure.cache;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TtlCacheTest {

    @Test
    void caches_value_within_ttl_period() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-30T12:00:00Z"));
        TtlCache<String, String> cache = new TtlCache<>(Duration.ofSeconds(30), clock);

        AtomicInteger callCount = new AtomicInteger();
        SupplierLoader loader = () -> CompletableFuture.completedFuture("val-" + callCount.incrementAndGet());

        String first = cache.get("key", loader::load).join();
        String second = cache.get("key", loader::load).join();

        assertThat(first).isEqualTo("val-1");
        assertThat(second).isEqualTo("val-1");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void reloads_after_ttl_expires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-30T12:00:00Z"));
        TtlCache<String, String> cache = new TtlCache<>(Duration.ofSeconds(30), clock);

        AtomicInteger callCount = new AtomicInteger();
        SupplierLoader loader = () -> CompletableFuture.completedFuture("val-" + callCount.incrementAndGet());

        String first = cache.get("key", loader::load).join();

        // Advance 31 seconds
        clock.advance(Duration.ofSeconds(31));

        String second = cache.get("key", loader::load).join();

        assertThat(first).isEqualTo("val-1");
        assertThat(second).isEqualTo("val-2");
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void failed_load_is_not_cached() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-30T12:00:00Z"));
        TtlCache<String, String> cache = new TtlCache<>(Duration.ofSeconds(30), clock);

        AtomicInteger callCount = new AtomicInteger();

        // First attempt fails
        CompletableFuture<String> failedCall = cache.get("key", () -> {
            callCount.incrementAndGet();
            return CompletableFuture.failedFuture(new RuntimeException("boom"));
        });

        assertThat(failedCall).isCompletedExceptionally();

        // Second attempt succeeds
        CompletableFuture<String> successCall = cache.get("key", () -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture("success");
        });

        assertThat(successCall.join()).isEqualTo("success");
        assertThat(callCount.get()).isEqualTo(2);
    }

    private interface SupplierLoader {
        CompletableFuture<String> load();
    }

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
