package org.mike.usermanagement.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistrationRateLimiterUseCase {

    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RegistrationAttemptWriter registrationAttemptWriter;

    // checkAndRecordAttempt itself is intentionally NOT @Transactional: registrationAttemptWriter
    // records the attempt in its own REQUIRES_NEW transaction, which commits before this method
    // returns from that call. That means the count is durable before we ever decide whether to
    // throw below, so neither a later failure in the caller's enclosing transaction (e.g.
    // RegisterUserFacade.register rolling back on a validation error) nor the throw right here
    // for exceeding the limit can undo it.
    public void checkAndRecordAttempt(String ipAddress) {
        Instant now = Instant.now();
        int attemptCount;
        try {
            attemptCount = registrationAttemptWriter.recordAttempt(ipAddress, now, WINDOW);
        } catch (DataIntegrityViolationException e) {
            // Lost the race to insert the very first row for this IP — two concurrent first-time
            // requests both found no existing row and both tried to insert one, and the unique
            // constraint on ip_address rejected the loser, aborting that attempt's transaction.
            // Postgres won't allow any further statement on an aborted transaction, so the retry
            // must run in a brand new transaction rather than reusing this one — which is exactly
            // what a second call to the REQUIRES_NEW writer method gives us. The row now exists,
            // so this call finds and locks it like any other update, same as the analogous
            // email-uniqueness race in RegisterUserUseCase. In the vanishingly rare case that this
            // retry *also* loses an insert race (a third concurrent first-time request), let it
            // surface as rate-limited rather than an unmapped 500 — indistinguishable in practice
            // from genuinely heavy concurrent traffic from this IP, and RestExceptionHandler
            // already maps this exception to a 429 the client can sensibly retry.
            try {
                attemptCount = registrationAttemptWriter.recordAttempt(ipAddress, now, WINDOW);
            } catch (DataIntegrityViolationException stillRacing) {
                throw new TooManyRegistrationAttemptsException();
            }
        }

        if (attemptCount > MAX_ATTEMPTS_PER_WINDOW) {
            throw new TooManyRegistrationAttemptsException();
        }
    }
}
