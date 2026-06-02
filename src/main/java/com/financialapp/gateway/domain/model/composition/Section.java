package com.financialapp.gateway.domain.model.composition;

import java.util.concurrent.CompletableFuture;

public record Section<T>(T data, SectionStatus status) {

    public static <T> Section<T> ok(T data) {
        return new Section<>(data, SectionStatus.OK);
    }

    public static <T> Section<T> unavailable(T fallback) {
        return new Section<>(fallback, SectionStatus.UNAVAILABLE);
    }

    public static <T> CompletableFuture<Section<T>> guard(CompletableFuture<T> call, T fallback) {
        return call.handle((value, ex) -> ex == null ? ok(value) : unavailable(fallback));
    }
}
