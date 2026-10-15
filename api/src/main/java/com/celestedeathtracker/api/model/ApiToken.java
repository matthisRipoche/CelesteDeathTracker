package com.celestedeathtracker.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Un token d'API. Le token en clair n'existe jamais ici : seulement son hash SHA-256. */
@Entity
@Table(name = "api_tokens")
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private String label;

    // Renseigné côté code (et non par le DEFAULT de la base) pour être disponible
    // immédiatement dans la réponse de création, sans relecture.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Mis à jour par une requête dédiée (TokenService.touch), pas par l'entité
    @Column(name = "last_used_at", insertable = false, updatable = false)
    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean revoked;

    protected ApiToken() {
    }

    public ApiToken(Long userId, String tokenHash, String label) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.label = label;
        this.createdAt = Instant.now();
    }

    public void revoke() { this.revoked = true; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public String getLabel() { return label; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public boolean isRevoked() { return revoked; }
}
