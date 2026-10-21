package com.celestedeathtracker.api.dto;

import com.celestedeathtracker.api.model.AppUser;
import java.time.Instant;

/**
 * Ce qu'on expose d'un compte : jamais le hash du mot de passe. L'email est null pour un compte
 * créé par Discord ; les champs discord* sont null tant que Discord n'est pas rattaché.
 */
public record UserResponse(long id, String email, Instant createdAt,
                           String discordUsername, String discordAvatarUrl) {

    public static UserResponse from(AppUser u) {
        String avatar = u.getDiscordAvatar() == null ? null
                : "https://cdn.discordapp.com/avatars/" + u.getDiscordId() + "/" + u.getDiscordAvatar() + ".png";
        return new UserResponse(u.getId(), u.getEmail(), u.getCreatedAt(), u.getDiscordUsername(), avatar);
    }
}
