package com.netbanking.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
final class AuthRateLimiter {
    private static final long CLEANUP_INTERVAL = 256;

    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong acquisitions = new AtomicLong();

    @Autowired
    AuthRateLimiter(
            @Value("${app.gateway.auth-rate-limit.max-requests:10}") int maxRequests,
            @Value("${app.gateway.auth-rate-limit.window-seconds:60}") long windowSeconds) {
        this(maxRequests, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
    }

    AuthRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("window must be at least one millisecond");
        }
        this.clock = clock;
    }

    Decision acquire(String clientKey) {
        long now = clock.millis();
        AtomicReference<Decision> result = new AtomicReference<>();
        windows.compute(
                clientKey,
                (key, current) -> {
                    if (current == null || now >= current.resetAtMillis()) {
                        long resetAt = Math.addExact(now, windowMillis);
                        result.set(new Decision(true, 0));
                        return new Window(1, resetAt);
                    }
                    if (current.requestCount() >= maxRequests) {
                        result.set(
                                new Decision(
                                        false,
                                        retryAfterSeconds(now, current.resetAtMillis())));
                        return current;
                    }
                    result.set(new Decision(true, 0));
                    return new Window(current.requestCount() + 1, current.resetAtMillis());
                });
        if (acquisitions.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            windows.entrySet().removeIf(entry -> entry.getValue().resetAtMillis() <= now);
        }
        return result.get();
    }

    int trackedClientCount() {
        return windows.size();
    }

    private static long retryAfterSeconds(long now, long resetAtMillis) {
        long remainingMillis = Math.max(1, resetAtMillis - now);
        return 1 + (remainingMillis - 1) / 1000;
    }

    record Decision(boolean allowed, long retryAfterSeconds) {}

    private record Window(int requestCount, long resetAtMillis) {}
}
