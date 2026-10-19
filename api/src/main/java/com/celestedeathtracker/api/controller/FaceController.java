package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.FaceDtos.FaceDetail;
import com.celestedeathtracker.api.dto.FaceDtos.FaceSummary;
import com.celestedeathtracker.api.security.CurrentUser;
import com.celestedeathtracker.api.service.FaceStatsService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lecture des statistiques par face de map (navigateur, session). Uniquement les tentatives du compte
 * connecté. Le SID contient des « / » : on le passe en paramètre de requête, pas dans le chemin.
 */
@RestController
@RequestMapping("/api/faces")
public class FaceController {

    private final FaceStatsService faces;
    private final CurrentUser currentUser;

    public FaceController(FaceStatsService faces, CurrentUser currentUser) {
        this.faces = faces;
        this.currentUser = currentUser;
    }

    /** « Mes maps » : une ligne par face jouée. */
    @GetMapping
    public List<FaceSummary> list(Authentication authentication) {
        return faces.faces(currentUser.id(authentication));
    }

    /** Détail d'une face : GET /api/faces/detail?sid=Celeste/1-ForsakenCity&side=B */
    @GetMapping("/detail")
    public FaceDetail detail(Authentication authentication,
                             @RequestParam String sid,
                             @RequestParam String side) {
        if (!side.matches("[ABC]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "side doit valoir A, B ou C");
        }
        return faces.face(currentUser.id(authentication), sid, side)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune tentative sur cette face"));
    }
}
