package com.celestedeathtracker.api.repository;

import com.celestedeathtracker.api.model.PairingRequest;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PairingRequestRepository extends JpaRepository<PairingRequest, Long> {

    Optional<PairingRequest> findByDeviceCodeHash(String deviceCodeHash);

    Optional<PairingRequest> findByUserCode(String userCode);

    boolean existsByUserCode(String userCode);

    /**
     * Transitions atomiques : la condition "status = ..." est dans la requête SQL elle-même.
     * Deux requêtes simultanées ne peuvent donc pas toutes les deux réussir (retour = 1 ligne
     * modifiée pour une seule d'entre elles). Sans cela, deux interrogations rapprochées du mod
     * pourraient générer deux tokens pour une seule approbation.
     */
    @Modifying
    @Query("update PairingRequest p set p.status = 'APPROVED', p.userId = :userId "
            + "where p.id = :id and p.status = 'PENDING' and p.expiresAt > :now")
    int approve(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("update PairingRequest p set p.status = 'DENIED' "
            + "where p.id = :id and p.status = 'PENDING' and p.expiresAt > :now")
    int deny(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("update PairingRequest p set p.status = 'CONSUMED' where p.id = :id and p.status = 'APPROVED'")
    int consume(@Param("id") Long id);

    @Modifying
    @Query("delete from PairingRequest p where p.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
