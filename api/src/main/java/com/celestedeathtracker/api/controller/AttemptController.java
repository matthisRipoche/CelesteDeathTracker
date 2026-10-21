package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.AttemptDtos.AttemptDetail;
import com.celestedeathtracker.api.dto.AttemptDtos.AttemptPage;
import com.celestedeathtracker.api.security.CurrentUser;
import com.celestedeathtracker.api.service.AttemptHistoryService;
import com.celestedeathtracker.api.service.AttemptHistoryService.Filter;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Historique des tentatives (navigateur, session). Uniquement celles du compte connecté.
 * Le SID passe en paramètre de requête, comme pour /api/faces.
 */
@RestController
@RequestMapping("/api/attempts")
public class AttemptController {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private static final Set<String> RESULTS = Set.of("success", "death", "abandoned", "ongoing");
    // Périodes prédéfinies : « all » = sans limite de date.
    private static final Map<String, Integer> PERIODS = Map.of("7d", 7, "30d", 30);

    private final AttemptHistoryService history;
    private final CurrentUser currentUser;

    public AttemptController(AttemptHistoryService history, CurrentUser currentUser) {
        this.history = history;
        this.currentUser = currentUser;
    }

    /** GET /api/attempts?sid=Celeste/1-ForsakenCity&side=B&result=death&period=7d&page=0&size=20 */
    @GetMapping
    public AttemptPage list(Authentication authentication,
                            @RequestParam(required = false) String sid,
                            @RequestParam(required = false) String side,
                            @RequestParam(required = false) String result,
                            @RequestParam(defaultValue = "all") String period,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size) {
        if (side != null && !side.matches("[ABC]")) {
            throw bad("side doit valoir A, B ou C");
        }
        if (result != null && !RESULTS.contains(result)) {
            throw bad("result doit valoir success, death, abandoned ou ongoing");
        }
        if (!period.equals("all") && !PERIODS.containsKey(period)) {
            throw bad("period doit valoir 7d, 30d ou all");
        }
        if (page < 0) {
            throw bad("page doit être supérieure ou égale à 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw bad("size doit être compris entre 1 et " + MAX_SIZE);
        }
        return history.list(currentUser.id(authentication),
                new Filter(sid, side, result, PERIODS.get(period)), page, size);
    }

    /** GET /api/attempts/42 : parcours et morts d'une tentative. */
    @GetMapping("/{id}")
    public AttemptDetail detail(Authentication authentication, @PathVariable long id) {
        return history.detail(currentUser.id(authentication), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tentative introuvable"));
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
