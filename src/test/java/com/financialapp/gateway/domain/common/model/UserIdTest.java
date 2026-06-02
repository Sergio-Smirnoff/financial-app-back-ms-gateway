package com.financialapp.gateway.domain.common.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserIdTest {
    @Test void accepts_positive_value() {
        assertEquals(42L, new UserId(42L).value());
    }
    @Test void rejects_null_or_non_positive() {
        assertThrows(IllegalArgumentException.class, () -> new UserId(null));
        assertThrows(IllegalArgumentException.class, () -> new UserId(0L));
        assertThrows(IllegalArgumentException.class, () -> new UserId(-1L));
    }
}
