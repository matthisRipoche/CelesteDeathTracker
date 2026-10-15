package com.celestedeathtracker.api.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limiteur à fenêtre glissante, en mémoire. Suffisant pour une seule instance de l'API ;
 * avec plusieurs instances il faudrait un stockage partagé (Redis, base...).
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** @return true si la requête est autorisée, false si la limite est dépassée */
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long threshold = now - window.toMillis();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < threshold) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** Évite que la table grossisse indéfiniment avec des clés (IP) qui ne reviennent pas. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000)
    void evictIdle() {
        long threshold = System.currentTimeMillis() - Duration.ofMinutes(15).toMillis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() < threshold;
            }
        });
    }
}
