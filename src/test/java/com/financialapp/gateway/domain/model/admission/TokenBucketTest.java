package com.financialapp.gateway.domain.model.admission;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {
    @Test void allows_up_to_capacity_then_denies_within_window() {
        TokenBucket bucket = new TokenBucket(new RateLimitPolicy(2, 60_000L));
        assertTrue(bucket.tryAcquire(1_000L));
        assertTrue(bucket.tryAcquire(1_010L));
        assertFalse(bucket.tryAcquire(1_020L));
    }
    @Test void refills_after_the_window_passes() {
        TokenBucket bucket = new TokenBucket(new RateLimitPolicy(1, 60_000L));
        assertTrue(bucket.tryAcquire(0L));
        assertFalse(bucket.tryAcquire(30_000L));
        assertTrue(bucket.tryAcquire(60_001L));
    }
    @Test void policy_rejects_invalid_config() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(0, 1000L));
        assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(1, 0L));
    }
}
