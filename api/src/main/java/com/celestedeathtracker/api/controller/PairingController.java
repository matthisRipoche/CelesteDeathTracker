package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.PairingDtos.CodeRequest;
import com.celestedeathtracker.api.dto.PairingDtos.LookupResponse;
import com.celestedeathtracker.api.dto.PairingDtos.PollRequest;
import com.celestedeathtracker.api.dto.PairingDtos.PollResponse;
import com.celestedeathtracker.api.dto.PairingDtos.StartRequest;
import com.celestedeathtracker.api.dto.PairingDtos.StartResponse;
import com.celestedeathtracker.api.security.CurrentUser;
import com.celestedeathtracker.api.security.RateLimiter;
import com.celestedeathtracker.api.service.PairingService;
import com.celestedeathtracker.api.service.PairingService.PollResult;
import com.celestedeathtracker.api.service.PairingService.Started;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deux publics, deux niveaux de confiance :
 *  - /start et /poll : appelés par le mod, SANS authentification (c'est le but : il n'a pas encore de token).
 *    Ils sont donc limités en débit, et ne révèlent rien tant qu'un compte n'a pas approuvé.
 *  - /lookup, /approve, /deny : appelés par le navigateur, session obligatoire.
 */
@RestController
@RequestMapping("/api/pairing")
public class PairingController {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final PairingService pairing;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;
    private final String publicUrl;

    public PairingController(PairingService pairing, CurrentUser currentUser, RateLimiter rateLimiter,
                             @Value("${app.public-url}") String publicUrl) {
        this.pairing = pairing;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    // ---------------- côté mod ----------------

    @PostMapping("/start")
    public StartResponse start(@Valid @RequestBody(required = false) StartRequest request,
                               HttpServletRequest http) {
        limit("pairing-start:" + http.getRemoteAddr(), 10);
        Started s = pairing.start(request == null ? null : request.deviceName(), http.getRemoteAddr());
        return new StartResponse(s.deviceCode(), s.userCode(), publicUrl + "/pair",
                s.expiresInSeconds(), PairingService.POLL_INTERVAL_SECONDS);
    }

    @PostMapping("/poll")
    public PollResponse poll(@Valid @RequestBody PollRequest request, HttpServletRequest http) {
        limit("pairing-poll:" + http.getRemoteAddr(), 60);
        PollResult r = pairing.poll(request.deviceCode());
        return new PollResponse(r.status(), r.token());
    }

    // ---------------- côté navigateur ----------------

    @GetMapping("/lookup")
    public LookupResponse lookup(Authentication authentication, @RequestParam String code) {
        // Limité par compte : empêche de deviner un code en essayant tout
        limit("pairing-attempt:" + currentUser.id(authentication), 10);
        return pairing.lookup(code)
                .map(r -> new LookupResponse(code.toUpperCase(), r.getDeviceName(), r.getCreatedAt()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Code inconnu ou expiré"));
    }

    @PostMapping("/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(Authentication authentication, @Valid @RequestBody CodeRequest request) {
        Long userId = currentUser.id(authentication);
        limit("pairing-attempt:" + userId, 10);
        if (!pairing.approve(userId, request.userCode())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code inconnu ou expiré");
        }
    }

    @PostMapping("/deny")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deny(Authentication authentication, @Valid @RequestBody CodeRequest request) {
        limit("pairing-attempt:" + currentUser.id(authentication), 10);
        if (!pairing.deny(request.userCode())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code inconnu ou expiré");
        }
    }

    private void limit(String key, int perMinute) {
        if (!rateLimiter.tryAcquire(key, perMinute, MINUTE)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop de requêtes, réessaie dans une minute");
        }
    }
}
