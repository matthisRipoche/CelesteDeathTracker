package com.celestedeathtracker.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.celestedeathtracker.api.security.RateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTests {

    @Test
    void blocksAfterTheLimitAndIsPerKey() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("a", 3, Duration.ofMinutes(1)));
        }
        assertFalse(limiter.tryAcquire("a", 3, Duration.ofMinutes(1)));
        // Une autre clé n'est pas affectée
        assertTrue(limiter.tryAcquire("b", 3, Duration.ofMinutes(1)));
    }

    @Test
    void windowSlides() throws Exception {
        RateLimiter limiter = new RateLimiter();
        assertTrue(limiter.tryAcquire("k", 1, Duration.ofMillis(50)));
        assertFalse(limiter.tryAcquire("k", 1, Duration.ofMillis(50)));
        Thread.sleep(80);
        assertTrue(limiter.tryAcquire("k", 1, Duration.ofMillis(50)));
    }
}
