package com.celestedeathtracker.api.repository;

import com.celestedeathtracker.api.model.Death;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Une simple interface : Spring Data génère l'implémentation (save, findAll, count, ...).
 * Types génériques : <entité, type de la clé primaire>.
 */
public interface DeathRepository extends JpaRepository<Death, Long> {

    // Toujours filtrer par propriétaire : un compte ne doit jamais voir les morts d'un autre.
    List<Death> findByUserIdOrderByIdAsc(Long userId);
}
