package com.celestedeathtracker.api.controller;

import com.celestedeathtracker.api.dto.TokenDtos.CreateTokenRequest;
import com.celestedeathtracker.api.dto.TokenDtos.CreatedTokenResponse;
import com.celestedeathtracker.api.dto.TokenDtos.TokenResponse;
import com.celestedeathtracker.api.security.CurrentUser;
import com.celestedeathtracker.api.service.TokenService;
import com.celestedeathtracker.api.service.TokenService.CreatedToken;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Gestion des tokens d'API par l'utilisateur connecté (session). */
@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenService tokenService;
    private final CurrentUser currentUser;

    public TokenController(TokenService tokenService, CurrentUser currentUser) {
        this.tokenService = tokenService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<TokenResponse> list(Authentication authentication) {
        return tokenService.list(currentUser.id(authentication)).stream().map(TokenResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedTokenResponse create(Authentication authentication, @Valid @RequestBody CreateTokenRequest request) {
        CreatedToken created = tokenService.create(currentUser.id(authentication), request.label());
        return new CreatedTokenResponse(created.token().getId(), created.token().getLabel(),
                created.token().getCreatedAt(), created.plaintext());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication authentication, @PathVariable Long id) {
        if (!tokenService.revoke(currentUser.id(authentication), id)) {
            // 404 aussi quand le token appartient à quelqu'un d'autre : on ne révèle pas son existence
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
