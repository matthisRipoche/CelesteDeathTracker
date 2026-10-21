package com.celestedeathtracker.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un compte utilisateur. Nommé AppUser pour ne pas être confondu avec
 * org.springframework.security.core.userdetails.User.
 * Un compte a un email + mot de passe, un compte Discord, ou les deux (compte rattaché).
 */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null pour un compte créé par Discord (on ne demande pas l'email à Discord)
    @Column(unique = true)
    private String email;

    // Jamais le mot de passe en clair : uniquement le hash BCrypt. Null si le compte vient de Discord.
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "discord_id", unique = true)
    private String discordId;

    @Column(name = "discord_username")
    private String discordUsername;

    // Hash de l'avatar : l'image est à https://cdn.discordapp.com/avatars/<discordId>/<hash>.png
    @Column(name = "discord_avatar")
    private String discordAvatar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    /** Compte créé à la première connexion Discord. */
    public static AppUser fromDiscord(String discordId, String username, String avatar) {
        AppUser u = new AppUser(null, null);
        u.linkDiscord(discordId, username, avatar);
        return u;
    }

    public void linkDiscord(String discordId, String username, String avatar) {
        this.discordId = discordId;
        updateDiscordProfile(username, avatar);
    }

    /** Le pseudo et l'avatar changent côté Discord : on les rafraîchit à chaque connexion. */
    public void updateDiscordProfile(String username, String avatar) {
        this.discordUsername = username;
        this.discordAvatar = avatar;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDiscordId() { return discordId; }
    public String getDiscordUsername() { return discordUsername; }
    public String getDiscordAvatar() { return discordAvatar; }
    public Instant getCreatedAt() { return createdAt; }
}
