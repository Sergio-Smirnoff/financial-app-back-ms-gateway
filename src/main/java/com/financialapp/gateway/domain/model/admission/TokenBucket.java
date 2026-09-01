package com.financialapp.gateway.domain.model.admission;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TokenBucket {
    private final RateLimitPolicy policy;
    private final Deque<Long> hits = new ArrayDeque<>();
    private final long createdAtMillis;

    public TokenBucket(RateLimitPolicy policy, long createdAtMillis) {
        if (policy == null) {
            throw new IllegalArgumentException("policy required");
        }
        this.policy = policy;
        this.createdAtMillis = createdAtMillis;
    }

    public TokenBucket(RateLimitPolicy policy) {
        this(policy, System.currentTimeMillis());
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

    public long lastHitMillis() {
        Long last = hits.peekLast();
        return last != null ? last : createdAtMillis;
    }
}
