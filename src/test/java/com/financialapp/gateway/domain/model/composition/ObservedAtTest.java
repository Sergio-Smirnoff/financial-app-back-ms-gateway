package com.financialapp.gateway.domain.model.composition;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservedAtTest {

    private final Instant now = Instant.parse("2026-07-30T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneId.of("UTC"));

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new ObservedAt(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObservedAt.of(null, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFutureInstantBeyond30Seconds() {
        Instant future31s = now.plusSeconds(31);
        assertThatThrownBy(() -> ObservedAt.of(future31s, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsInstantWithin29SecondsInFuture() {
        Instant future29s = now.plusSeconds(29);
        ObservedAt observedAt = ObservedAt.of(future29s, clock);
        assertThat(observedAt.value()).isEqualTo(future29s);
    }

    @Test
    void ageMathAgainstFixedClock() {
        Instant past10m = now.minus(Duration.ofMinutes(10));
        ObservedAt observedAt = ObservedAt.of(past10m, clock);
        assertThat(observedAt.age(clock)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void stalenessBoundaryCheck() {
        Instant past5m = now.minus(Duration.ofMinutes(5));
        ObservedAt observedAt = ObservedAt.of(past5m, clock);

        assertThat(observedAt.isStaleBeyond(Duration.ofMinutes(4), clock)).isTrue();
        assertThat(observedAt.isStaleBeyond(Duration.ofMinutes(5), clock)).isFalse();
        assertThat(observedAt.isStaleBeyond(Duration.ofMinutes(6), clock)).isFalse();
    }
}
