package com.financialapp.gateway.domain.model.admission;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sliding-window admission counter for a single client. Owns the allow/deny decision.
 * Not thread-safe by itself; the infrastructure filter guards per-client access.
 */
public final class TokenBucket {
    private final RateLimitPolicy policy;
    private final Deque<Long> hits = new ArrayDeque<>();

    public TokenBucket(RateLimitPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy required");
        }
        this.policy = policy;
    }

    public boolean tryAcquire(long nowMillis) {
        while (!hits.isEmpty() && nowMillis - hits.peekFirst() > policy.windowMillis()) {
            hits.pollFirst();
        }
        if (hits.size() >= policy.capacity()) {
            return false;
        }
        hits.addLast(nowMillis);
        return true;
    }
}
