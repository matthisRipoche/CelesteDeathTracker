package com.celestedeathtracker.api.repository;

import com.celestedeathtracker.api.model.ApiToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByTokenHashAndRevokedFalse(String tokenHash);

    List<ApiToken> findByUserIdOrderByIdDesc(Long userId);

    Optional<ApiToken> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndRevokedFalse(Long userId);

    @Modifying
    @Query("update ApiToken t set t.lastUsedAt = :now where t.id = :id")
    void touch(@Param("id") Long id, @Param("now") Instant now);
}
