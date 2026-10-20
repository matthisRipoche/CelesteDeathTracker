package com.celestedeathtracker.api.repository;

import com.celestedeathtracker.api.model.Attempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    // Toujours filtrer par propriétaire : l'identifiant client vient du mod, donc d'un client non fiable.
    Optional<Attempt> findByUserIdAndClientId(Long userId, String clientId);

    List<Attempt> findByUserIdOrderByStartedAtAsc(Long userId);

    List<Attempt> findByUserIdAndChapterAndSideOrderByStartedAtAsc(Long userId, String chapter, String side);

    // Pour le live : seulement les tentatives récentes, de la plus ancienne à la plus récente.
    List<Attempt> findByUserIdAndStartedAtGreaterThanEqualOrderByStartedAtAsc(Long userId, long since);
}
