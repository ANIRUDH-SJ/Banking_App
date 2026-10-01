package com.netbanking.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class AuthRateLimiterTest {

    @Test
    void rejectsRequestsBeyondTheLimitUntilTheWindowResets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-29T00:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(2, Duration.ofSeconds(10), clock);

        assertThat(limiter.acquire("client-a").allowed()).isTrue();
        assertThat(limiter.acquire("client-a").allowed()).isTrue();

        AuthRateLimiter.Decision rejected = limiter.acquire("client-a");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(10);

        clock.advance(Duration.ofMillis(9_001));
        assertThat(limiter.acquire("client-a").retryAfterSeconds()).isEqualTo(1);

        clock.advance(Duration.ofMillis(999));
        assertThat(limiter.acquire("client-a").allowed()).isTrue();
    }

    @Test
    void keepsIndependentWindowsForDifferentClients() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        AuthRateLimiter limiter = new AuthRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.acquire("client-a").allowed()).isTrue();
        assertThat(limiter.acquire("client-a").allowed()).isFalse();
        assertThat(limiter.acquire("client-b").allowed()).isTrue();
    }

    @Test
    void periodicallyRemovesExpiredClientWindows() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        AuthRateLimiter limiter = new AuthRateLimiter(1, Duration.ofSeconds(1), clock);
        limiter.acquire("expired-client");
        clock.advance(Duration.ofSeconds(2));

        for (int i = 0; i < 255; i++) {
            limiter.acquire("active-" + i);
        }

        assertThat(limiter.trackedClientCount()).isEqualTo(255);
    }

    @Test
    void rejectsInvalidConfiguration() {
        MutableClock clock = new MutableClock(Instant.EPOCH);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AuthRateLimiter(0, Duration.ofSeconds(1), clock));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AuthRateLimiter(1, Duration.ZERO, clock));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AuthRateLimiter(1, Duration.ofNanos(1), clock));
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
