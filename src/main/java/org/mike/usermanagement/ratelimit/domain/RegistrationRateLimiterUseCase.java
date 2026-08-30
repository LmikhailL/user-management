package org.mike.usermanagement.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;
import org.mike.usermanagement.ratelimit.persistence.RegistrationAttempt;
import org.mike.usermanagement.ratelimit.persistence.RegistrationAttemptRepository;
import org.springframework.stereotype.Service;

@Service
public class RegistrationRateLimiterUseCase {

    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RegistrationAttemptRepository registrationAttemptRepository;

    public RegistrationRateLimiterUseCase(RegistrationAttemptRepository registrationAttemptRepository) {
        this.registrationAttemptRepository = registrationAttemptRepository;
    }

    public void checkAndRecordAttempt(String ipAddress) {
        Instant now = Instant.now();
        RegistrationAttempt attempt = registrationAttemptRepository
                .findByIpAddress(ipAddress)
                .orElseGet(() -> new RegistrationAttempt(null, ipAddress, now, 0));

        if (Duration.between(attempt.getWindowStart(), now).compareTo(WINDOW) >= 0) {
            attempt.setWindowStart(now);
            attempt.setAttemptCount(0);
        }

        attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        registrationAttemptRepository.save(attempt);

        if (attempt.getAttemptCount() > MAX_ATTEMPTS_PER_WINDOW) {
            throw new TooManyRegistrationAttemptsException();
        }
    }
}
