package com.financialapp.gateway.domain.model.composition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public record ObservedAt(Instant value) {
    private static final Duration MAX_FUTURE_SKEW = Duration.ofSeconds(30);

    public ObservedAt {
        if (value == null) {
            throw new IllegalArgumentException("value required");
        }
        if (value.isAfter(Instant.now().plus(MAX_FUTURE_SKEW))) {
            throw new IllegalArgumentException("ObservedAt cannot be in the future beyond tolerance");
        }
    }

    public static ObservedAt of(Instant value, Clock clock) {
        if (value == null) {
            throw new IllegalArgumentException("value required");
        }
        Instant maxAllowed = clock.instant().plus(MAX_FUTURE_SKEW);
        if (value.isAfter(maxAllowed)) {
            throw new IllegalArgumentException("ObservedAt cannot be in the future beyond tolerance");
        }
        return new ObservedAt(value);
    }

    public static ObservedAt now(Clock clock) {
        return new ObservedAt(clock.instant());
    }

    public Duration age(Clock clock) {
        return Duration.between(value, clock.instant());
    }

    public boolean isStaleBeyond(Duration maxAge, Clock clock) {
        return age(clock).compareTo(maxAge) > 0;
    }
}
