package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.DeathRequest;
import com.celestedeathtracker.api.dto.DeathResponse;
import com.celestedeathtracker.api.dto.SessionEventRequest;
import com.celestedeathtracker.api.model.Death;
import com.celestedeathtracker.api.repository.DeathRepository;
import com.celestedeathtracker.api.security.RateLimiter;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Points d'entrée du mod C#. Protégés par ApiTokenAuthFilter : le propriétaire de la donnée
 * est toujours déduit du token, jamais d'un champ envoyé par le client.
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    // Un joueur ne meurt pas 300 fois par minute : au-delà, c'est un script ou un bug.
    private static final int MAX_PER_MINUTE = 300;

    private final DeathRepository deaths;
    private final RateLimiter rateLimiter;

    public IngestController(DeathRepository deaths, RateLimiter rateLimiter) {
        this.deaths = deaths;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/deaths")
    @ResponseStatus(HttpStatus.CREATED)
    public DeathResponse death(@AuthenticationPrincipal Long userId, @Valid @RequestBody DeathRequest r) {
        limit(userId);
        Death saved = deaths.save(new Death(userId, r.timestamp(), r.chapter(), r.room(),
                r.deathX(), r.deathY(), r.totalDeaths(), r.sessionDeaths(), r.timeInRoomMs()));
        return DeathResponse.from(saved);
    }

    /** Événements de session : acceptés mais pas encore stockés (voir CLAUDE.md, parcours étape 5). */
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void event(@AuthenticationPrincipal Long userId, @Valid @RequestBody SessionEventRequest r) {
        limit(userId);
    }

    private void limit(Long userId) {
        if (!rateLimiter.tryAcquire("ingest:" + userId, MAX_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop d'envois");
        }
    }
}
