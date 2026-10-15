package com.celestedeathtracker.api.dto;

import com.celestedeathtracker.api.model.AppUser;
import java.time.Instant;

/** Ce qu'on expose d'un compte : jamais le hash du mot de passe. */
public record UserResponse(long id, String email, Instant createdAt) {

    public static UserResponse from(AppUser u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getCreatedAt());
    }
}
