package com.financialapp.gateway.infrastructure.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TtlCache<K, V> {

    private record Entry<V>(CompletableFuture<V> future, Instant expiresAt) {}

    private final Duration ttl;
    private final Clock clock;
    private final ConcurrentHashMap<K, Entry<V>> cache = new ConcurrentHashMap<>();

    public TtlCache(Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    public TtlCache(Duration ttl, Clock clock) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("positive ttl required");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock required");
        }
        this.ttl = ttl;
        this.clock = clock;
    }

    public CompletableFuture<V> get(K key, Supplier<CompletableFuture<V>> loader) {
        Instant now = clock.instant();

        Entry<V> entry = cache.compute(key, (k, existing) -> {
            if (existing != null && now.isBefore(existing.expiresAt())) {
                return existing;
            }
            CompletableFuture<V> future = loader.get();
            return new Entry<>(future, now.plus(ttl));
        });

        entry.future().whenComplete((val, ex) -> {
            if (ex != null) {
                cache.remove(key, entry);
            }
        });

        return entry.future();
    }

    public void clear() {
        cache.clear();
    }
}
