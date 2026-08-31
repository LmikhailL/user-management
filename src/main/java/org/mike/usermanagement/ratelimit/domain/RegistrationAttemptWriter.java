package org.mike.usermanagement.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.ratelimit.persistence.RegistrationAttempt;
import org.mike.usermanagement.ratelimit.persistence.RegistrationAttemptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Split out from RegistrationRateLimiterUseCase so each recording attempt runs in its own,
// separately-proxied REQUIRES_NEW transaction: Spring's @Transactional only takes effect on
// calls that go through the proxy, so this has to be a distinct bean rather than a private method
// RegistrationRateLimiterUseCase calls on itself (self-invocation bypasses the proxy entirely).
// @Component rather than @Service: this is a transaction-boundary helper, not itself a use case or
// facade, and the project's ArchUnit rules require @Service beans to be named as one of those.
@Component
@RequiredArgsConstructor
class RegistrationAttemptWriter {

    private final RegistrationAttemptRepository registrationAttemptRepository;

    // saveAndFlush forces the INSERT/UPDATE to execute here rather than being deferred to
    // transaction commit — a plain save() on a new, UUID-generated-id entity doesn't need an
    // immediate flush to obtain the id, so Hibernate would otherwise queue the insert until
    // commit, past the caller's try/catch, and a unique-constraint violation would surface as an
    // uncaught exception instead of the DataIntegrityViolationException callers actually handle.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordAttempt(String ipAddress, Instant now, Duration window) {
        RegistrationAttempt attempt = registrationAttemptRepository
                .findByIpAddress(ipAddress)
                .orElseGet(() -> new RegistrationAttempt(null, ipAddress, now, 0));

        if (Duration.between(attempt.getWindowStart(), now).compareTo(window) >= 0) {
            attempt.setWindowStart(now);
            attempt.setAttemptCount(0);
        }

        attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        registrationAttemptRepository.saveAndFlush(attempt);

        return attempt.getAttemptCount();
    }
}
