package com.celestedeathtracker.api.repository;

import com.celestedeathtracker.api.model.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    // Méthode dérivée : Spring Data génère la requête à partir du nom
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<AppUser> findByDiscordId(String discordId);
}
