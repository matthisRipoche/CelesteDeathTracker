package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.model.AppUser;
import com.celestedeathtracker.api.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relie une identité Discord à un compte : connexion d'un compte déjà lié, création d'un compte
 * à la première connexion, ou rattachement à un compte existant (email + mot de passe).
 */
@Service
public class DiscordAccountService {

    /** Ce compte Discord (ou ce compte du site) est déjà rattaché ailleurs. */
    public static class AlreadyLinkedException extends RuntimeException {
        public AlreadyLinkedException(String message) {
            super(message);
        }
    }

    private final UserRepository users;

    public DiscordAccountService(UserRepository users) {
        this.users = users;
    }

    /**
     * @param linkToUserId compte connecté qui demande le rattachement, ou null pour une simple connexion
     */
    @Transactional
    public AppUser resolve(String discordId, String username, String avatar, Long linkToUserId) {
        Optional<AppUser> existing = users.findByDiscordId(discordId);

        if (linkToUserId == null) {
            // Connexion : on retrouve le compte lié, sinon on en crée un
            AppUser user = existing.orElseGet(() -> AppUser.fromDiscord(discordId, username, avatar));
            user.updateDiscordProfile(username, avatar);
            return users.save(user);
        }

        // Rattachement : ce Discord ne doit appartenir à personne d'autre
        if (existing.isPresent() && !existing.get().getId().equals(linkToUserId)) {
            throw new AlreadyLinkedException("Ce compte Discord est déjà rattaché à un autre compte");
        }
        AppUser target = users.findById(linkToUserId).orElseThrow();
        if (target.getDiscordId() != null && !target.getDiscordId().equals(discordId)) {
            throw new AlreadyLinkedException("Ce compte a déjà un autre Discord rattaché");
        }
        target.linkDiscord(discordId, username, avatar);
        return users.save(target);
    }
}
