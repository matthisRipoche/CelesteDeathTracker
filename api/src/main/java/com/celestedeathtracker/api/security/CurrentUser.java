package com.celestedeathtracker.api.security;

import com.celestedeathtracker.api.model.AppUser;
import com.celestedeathtracker.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Retrouve le compte à partir de l'utilisateur connecté par session (son nom est l'ID du compte). */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Long id(Authentication authentication) {
        return user(authentication).getId();
    }

    public AppUser user(Authentication authentication) {
        try {
            return users.findById(Long.parseLong(authentication.getName()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        } catch (NumberFormatException e) {
            // Session ouverte avant le passage à l'ID (nom = email) : il faut se reconnecter
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
