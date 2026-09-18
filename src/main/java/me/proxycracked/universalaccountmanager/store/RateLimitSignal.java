package me.proxycracked.universalaccountmanager.store;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tells the history scan that a shop is throttling, even when the request went on to succeed.
 *
 * <p>The API clients retry a 429 internally with their own backoff, so a throttled read usually
 * still returns a result - just seconds later. That means a scan pacing itself on thrown errors
 * never finds out it is being limited; it only feels slow. The clients report every 429 here
 * instead, so the pacer can widen the gap on the first one and honour {@code Retry-After} rather
 * than repeatedly walking into the limiter and paying the client's retry sleep each time.</p>
 */
public final class RateLimitSignal {
    private static final long NONE = -1L;
    private static final AtomicLong PENDING = new AtomicLong(NONE);

    private RateLimitSignal() {
    }

    /** @param retryAfterMillis what the shop asked for, or 0 when it did not say */
    public static void report(long retryAfterMillis) {
        long value = Math.max(0L, retryAfterMillis);
        long current;
        do {
            current = PENDING.get();
            if (value <= current) {
                return;
            }
        } while (!PENDING.compareAndSet(current, value));
    }

    /**
     * The largest {@code Retry-After} seen since the last call, or {@code -1} when nothing was
     * throttled. Reading it clears it.
     */
    public static long consume() {
        return PENDING.getAndSet(NONE);
    }
}
