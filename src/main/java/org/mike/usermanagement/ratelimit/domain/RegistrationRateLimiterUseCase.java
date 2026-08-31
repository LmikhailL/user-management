package org.mike.usermanagement.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.ratelimit.persistence.RegistrationAttempt;
import org.mike.usermanagement.ratelimit.persistence.RegistrationAttemptRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationRateLimiterUseCase {

    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RegistrationAttemptRepository registrationAttemptRepository;

    // REQUIRES_NEW so the recorded attempt always commits on its own, regardless of whether the
    // caller's enclosing transaction (e.g. RegisterUserFacade.register) later rolls back because
    // registration itself failed. Otherwise every invalid/duplicate registration attempt would
    // roll back its own rate-limit count along with it, letting an attacker retry indefinitely.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndRecordAttempt(String ipAddress) {
        Instant now = Instant.now();
        RegistrationAttempt attempt;
        try {
            attempt = registrationAttemptRepository
                    .findByIpAddress(ipAddress)
                    .orElseGet(() -> new RegistrationAttempt(null, ipAddress, now, 0));
            recordAttempt(attempt, now);
        } catch (DataIntegrityViolationException e) {
            // Lost the race to insert the very first row for this IP — two concurrent first-time
            // requests both found no existing row and both tried to insert one, and the unique
            // constraint on ip_address rejected the loser. The row now exists, so re-fetch it;
            // the pessimistic lock then serializes us with whoever won, same as the analogous
            // email-uniqueness race in RegisterUserUseCase.
            attempt = registrationAttemptRepository.findByIpAddress(ipAddress).orElseThrow(() -> e);
            recordAttempt(attempt, now);
        }

        if (attempt.getAttemptCount() > MAX_ATTEMPTS_PER_WINDOW) {
            throw new TooManyRegistrationAttemptsException();
        }
    }

    private void recordAttempt(RegistrationAttempt attempt, Instant now) {
        if (Duration.between(attempt.getWindowStart(), now).compareTo(WINDOW) >= 0) {
            attempt.setWindowStart(now);
            attempt.setAttemptCount(0);
        }

        attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        registrationAttemptRepository.save(attempt);
    }
}
