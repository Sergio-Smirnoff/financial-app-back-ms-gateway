package com.financialapp.gateway.domain.common.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessTokenTest {
    @Test void accepts_value() {
        assertEquals("a.b.c", new AccessToken("a.b.c").value());
    }
    @Test void rejects_blank_or_null() {
        assertThrows(IllegalArgumentException.class, () -> new AccessToken("  "));
        assertThrows(IllegalArgumentException.class, () -> new AccessToken(null));
    }
}
