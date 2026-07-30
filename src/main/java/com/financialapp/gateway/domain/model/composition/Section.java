package com.financialapp.gateway.domain.model.composition;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;

public record Section<T>(T data, SectionStatus status, ObservedAt observedAt) {

    public Section {
        if (status == null) {
            throw new IllegalArgumentException("status required");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt required");
        }
    }

    public static <T> Section<T> ok(T data, ObservedAt observedAt) {
        return new Section<>(data, SectionStatus.OK, observedAt);
    }

    public static <T> Section<T> unavailable(T fallback, ObservedAt observedAt) {
        return new Section<>(fallback, SectionStatus.UNAVAILABLE, observedAt);
    }

    public static <T> CompletableFuture<Section<T>> guard(CompletableFuture<T> call, T fallback, Clock clock) {
        return call.handle((value, ex) -> {
            ObservedAt stamp = ObservedAt.now(clock);
            return ex == null ? ok(value, stamp) : unavailable(fallback, stamp);
        });
    }
}
