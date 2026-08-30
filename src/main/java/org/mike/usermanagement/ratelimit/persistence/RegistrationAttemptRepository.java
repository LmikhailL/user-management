package org.mike.usermanagement.ratelimit.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RegistrationAttemptRepository extends JpaRepository<RegistrationAttempt, UUID> {

    // Pessimistic write lock: two registration attempts from the same IP arriving at the same
    // instant must be serialized, otherwise both could read the same count and both increment
    // to the same next value, letting more than 5 attempts through.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationAttempt> findByIpAddress(String ipAddress);
}
