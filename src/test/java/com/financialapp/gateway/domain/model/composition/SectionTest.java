package com.financialapp.gateway.domain.model.composition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class SectionTest {

    @Test
    void ok_wraps_data_with_ok_status() {
        Section<String> s = Section.ok("data");
        assertThat(s.data()).isEqualTo("data");
        assertThat(s.status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void unavailable_wraps_fallback_with_unavailable_status() {
        Section<String> s = Section.unavailable("fallback");
        assertThat(s.data()).isEqualTo("fallback");
        assertThat(s.status()).isEqualTo(SectionStatus.UNAVAILABLE);
    }

    @Test
    void guard_returns_ok_section_when_future_succeeds() {
        var future = CompletableFuture.completedFuture(List.of("a", "b"));
        Section<List<String>> s = Section.guard(future, List.of()).join();
        assertThat(s.status()).isEqualTo(SectionStatus.OK);
        assertThat(s.data()).containsExactly("a", "b");
    }

    @Test
    void guard_returns_unavailable_section_with_fallback_when_future_fails() {
        CompletableFuture<List<String>> future =
                CompletableFuture.failedFuture(new RuntimeException("boom"));
        Section<List<String>> s = Section.guard(future, List.of()).join();
        assertThat(s.status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(s.data()).isEmpty();
    }
}
