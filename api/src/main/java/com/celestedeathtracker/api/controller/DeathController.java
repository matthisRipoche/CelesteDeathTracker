package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.DeathResponse;
import com.celestedeathtracker.api.repository.DeathRepository;
import com.celestedeathtracker.api.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecture des morts par le navigateur (session). L'écriture se fait uniquement par le mod,
 * via /api/ingest/deaths (token d'API).
 */
@RestController
@RequestMapping("/api/deaths")
public class DeathController {

    private final DeathRepository deaths;
    private final CurrentUser currentUser;

    public DeathController(DeathRepository deaths, CurrentUser currentUser) {
        this.deaths = deaths;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<DeathResponse> list(Authentication authentication) {
        // Uniquement les morts du compte connecté
        return deaths.findByUserIdOrderByIdAsc(currentUser.id(authentication))
                .stream().map(DeathResponse::from).toList();
    }
}
