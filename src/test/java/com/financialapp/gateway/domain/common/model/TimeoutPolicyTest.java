package com.financialapp.gateway.domain.common.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutPolicyTest {

    @Test
    void exposes_the_per_call_duration() {
        TimeoutPolicy policy = new TimeoutPolicy(Duration.ofSeconds(3));
        assertThat(policy.perCall()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void rejects_null_zero_or_negative() {
        assertThatThrownBy(() -> new TimeoutPolicy(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeoutPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeoutPolicy(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
