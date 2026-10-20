package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.AttemptEventRequest;
import com.celestedeathtracker.api.dto.DeathRequest;
import com.celestedeathtracker.api.dto.DeathResponse;
import com.celestedeathtracker.api.model.Death;
import com.celestedeathtracker.api.repository.DeathRepository;
import com.celestedeathtracker.api.security.RateLimiter;
import com.celestedeathtracker.api.service.AttemptService;
import com.celestedeathtracker.api.service.LiveHub;
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
    private final AttemptService attemptService;
    private final LiveHub liveHub;
    private final RateLimiter rateLimiter;

    public IngestController(DeathRepository deaths, AttemptService attemptService, LiveHub liveHub,
                            RateLimiter rateLimiter) {
        this.deaths = deaths;
        this.attemptService = attemptService;
        this.liveHub = liveHub;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/deaths")
    @ResponseStatus(HttpStatus.CREATED)
    public DeathResponse death(@AuthenticationPrincipal Long userId, @Valid @RequestBody DeathRequest r) {
        limit(userId);
        // La mort est rattachée à sa tentative (créée au besoin) quand le mod récent l'indique
        Long attemptId = r.attemptId() == null ? null
                : attemptService.getOrCreate(userId, r.attemptId(), r.chapter(), r.side(),
                        r.saveSlot(), r.timestamp(), r.timeInRoomMs()).getId();
        Death saved = deaths.save(new Death(userId, r.timestamp(), r.chapter(), r.room(),
                r.deathX(), r.deathY(), r.totalDeaths(), r.sessionDeaths(), r.timeInRoomMs(),
                attemptId, r.side(), r.saveSlot()));
        liveHub.publish(userId); // les onglets ouverts voient la mort tout de suite
        return DeathResponse.from(saved);
    }

    /** Événements de tentative : attempt_start, room_enter, attempt_end. */
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void event(@AuthenticationPrincipal Long userId, @Valid @RequestBody AttemptEventRequest r) {
        limit(userId);
        attemptService.apply(userId, r);
        liveHub.publish(userId);
    }

    private void limit(Long userId) {
        if (!rateLimiter.tryAcquire("ingest:" + userId, MAX_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop d'envois");
        }
    }
}
