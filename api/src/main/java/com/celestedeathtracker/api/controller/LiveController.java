package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.LiveDtos.LiveSnapshot;
import com.celestedeathtracker.api.security.CurrentUser;
import com.celestedeathtracker.api.service.LiveHub;
import com.celestedeathtracker.api.service.LiveService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live de session (navigateur, session de connexion). Uniquement les tentatives du compte connecté.
 * Le mod n'appelle jamais ces routes : il alimente le live en envoyant ses événements d'ingestion.
 */
@RestController
@RequestMapping("/api/live")
public class LiveController {

    private final LiveService live;
    private final LiveHub hub;
    private final CurrentUser currentUser;

    public LiveController(LiveService live, LiveHub hub, CurrentUser currentUser) {
        this.live = live;
        this.hub = hub;
        this.currentUser = currentUser;
    }

    /** L'état courant, une seule fois (premier affichage, ou secours si le flux est coupé). */
    @GetMapping
    public LiveSnapshot snapshot(Authentication authentication) {
        return live.snapshot(currentUser.id(authentication), System.currentTimeMillis());
    }

    /**
     * Flux Server-Sent Events : un événement « snapshot » à l'ouverture, puis à chaque événement du jeu.
     * X-Accel-Buffering: no demande à nginx de ne pas mettre la réponse en mémoire tampon (sinon rien
     * n'arrive avant la fin de la connexion).
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return hub.subscribe(currentUser.id(authentication));
    }
}
