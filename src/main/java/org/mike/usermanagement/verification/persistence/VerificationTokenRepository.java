package org.mike.usermanagement.verification.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    // A single UPDATE guarded by "consumedAt IS NULL" is the atomic single-use gate: two
    // concurrent verify calls for the same token both attempt this update, but the database
    // only lets one of them actually change a row (the other's WHERE clause matches nothing
    // once the first commits), so the caller can tell "I won the race" from "already consumed"
    // purely from the returned row count, no separate locking needed.
    @Modifying
    @Query("UPDATE VerificationToken t SET t.consumedAt = :now WHERE t.id = :id AND t.consumedAt IS NULL")
    int consumeIfUnused(@Param("id") UUID id, @Param("now") Instant now);
}
