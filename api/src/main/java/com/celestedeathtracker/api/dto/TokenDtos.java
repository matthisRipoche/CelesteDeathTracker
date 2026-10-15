package com.celestedeathtracker.api.dto;

import com.celestedeathtracker.api.model.ApiToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TokenDtos {

    private TokenDtos() {
    }

    public record CreateTokenRequest(@NotBlank @Size(max = 100) String label) {
    }

    /** Ce qu'on liste : jamais le token ni son hash. */
    public record TokenResponse(long id, String label, Instant createdAt, Instant lastUsedAt, boolean revoked) {
        public static TokenResponse from(ApiToken t) {
            return new TokenResponse(t.getId(), t.getLabel(), t.getCreatedAt(), t.getLastUsedAt(), t.isRevoked());
        }
    }

    /** Réponse à la création : le seul moment où le token en clair est renvoyé. */
    public record CreatedTokenResponse(long id, String label, Instant createdAt, String token) {
    }
}
