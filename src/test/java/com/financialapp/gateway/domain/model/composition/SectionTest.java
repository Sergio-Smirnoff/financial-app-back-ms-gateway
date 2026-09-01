package com.financialapp.gateway.domain.model.composition;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class SectionTest {

    private final Instant now = Instant.parse("2026-07-30T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneId.of("UTC"));

    @Test
    void ok_wraps_data_with_ok_status_and_observed_at() {
        ObservedAt stamp = ObservedAt.now(clock);
        Section<String> s = Section.ok("data", stamp);
        assertThat(s.data()).isEqualTo("data");
        assertThat(s.status()).isEqualTo(SectionStatus.OK);
        assertThat(s.observedAt()).isEqualTo(stamp);
    }

    @Test
    void unavailable_wraps_fallback_with_unavailable_status_and_observed_at() {
        ObservedAt stamp = ObservedAt.now(clock);
        Section<String> s = Section.unavailable("fallback", stamp);
        assertThat(s.data()).isEqualTo("fallback");
        assertThat(s.status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(s.observedAt()).isEqualTo(stamp);
    }

    @Test
    void guard_returns_ok_section_stamped_with_clock_when_future_succeeds() {
        var future = CompletableFuture.completedFuture(List.of("a", "b"));
        Section<List<String>> s = Section.guard(future, List.of(), clock).join();
        assertThat(s.status()).isEqualTo(SectionStatus.OK);
        assertThat(s.data()).containsExactly("a", "b");
        assertThat(s.observedAt().value()).isEqualTo(now);
    }

    @Test
    void guard_returns_unavailable_section_stamped_with_clock_when_future_fails() {
        CompletableFuture<List<String>> future =
                CompletableFuture.failedFuture(new RuntimeException("boom"));
        Section<List<String>> s = Section.guard(future, List.of(), clock).join();
        assertThat(s.status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(s.data()).isEmpty();
        assertThat(s.observedAt().value()).isEqualTo(now);
    }
}
