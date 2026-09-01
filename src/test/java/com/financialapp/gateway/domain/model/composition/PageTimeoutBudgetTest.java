package com.financialapp.gateway.domain.model.composition;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageTimeoutBudgetTest {

    @Test
    void acceptsValidDuration() {
        PageTimeoutBudget budget = PageTimeoutBudget.fromMillis(5000);
        assertThat(budget.total()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void rejectsNullOrZeroOrNegative() {
        assertThatThrownBy(() -> new PageTimeoutBudget(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageTimeoutBudget.fromMillis(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageTimeoutBudget.fromMillis(-100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDurationExceeding30Seconds() {
        assertThatThrownBy(() -> PageTimeoutBudget.fromMillis(30_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
