package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.SummaryDtos.Summary;
import com.celestedeathtracker.api.security.CurrentUser;
import com.celestedeathtracker.api.service.SummaryService;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Le tableau de bord (navigateur, session). Uniquement les tentatives du compte connecté. */
@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final SummaryService summary;
    private final CurrentUser currentUser;

    public SummaryController(SummaryService summary, CurrentUser currentUser) {
        this.summary = summary;
        this.currentUser = currentUser;
    }

    /** GET /api/summary?tz=Europe/Paris : tz = fuseau IANA du navigateur, UTC par défaut. */
    @GetMapping
    public Summary get(Authentication authentication, @RequestParam(defaultValue = "UTC") String tz) {
        ZoneId zone;
        try {
            zone = ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tz doit être un fuseau IANA (Europe/Paris)");
        }
        return summary.summary(currentUser.id(authentication), zone);
    }
}
