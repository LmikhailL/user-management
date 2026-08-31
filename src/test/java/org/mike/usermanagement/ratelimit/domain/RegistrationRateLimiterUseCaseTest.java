package org.mike.usermanagement.ratelimit.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RegistrationRateLimiterUseCaseTest {

    private static final String IP = "203.0.113.7";

    private RegistrationAttemptWriter registrationAttemptWriter;
    private RegistrationRateLimiterUseCase useCase;

    @BeforeEach
    void setUp() {
        registrationAttemptWriter = mock(RegistrationAttemptWriter.class);
        useCase = new RegistrationRateLimiterUseCase(registrationAttemptWriter);
    }

    @Nested
    class WithinLimit {

        @Test
        @DisplayName("given the writer records a count within the limit, when checked, then the attempt is allowed")
        void allowsAttemptWithinLimit() {
            // Given
            when(registrationAttemptWriter.recordAttempt(eq(IP), any(Instant.class), any(Duration.class)))
                    .thenReturn(5);

            // When / Then: no exception
            useCase.checkAndRecordAttempt(IP);
        }
    }

    @Nested
    class OverLimit {

        @Test
        @DisplayName(
                "given the writer records a count over the limit, when checked, then it fails with the rate-limit message")
        void rejectsAttemptOverLimit() {
            // Given
            when(registrationAttemptWriter.recordAttempt(eq(IP), any(Instant.class), any(Duration.class)))
                    .thenReturn(6);

            // When / Then
            assertThatThrownBy(() -> useCase.checkAndRecordAttempt(IP))
                    .isInstanceOf(TooManyRegistrationAttemptsException.class)
                    .hasMessage("Too many attempts, please try again later");
        }
    }

    @Nested
    class ConcurrentFirstAttempt {

        @Test
        @DisplayName(
                "given the writer loses the insert race for the first attempt from this IP, when checked, then it retries once against the winner's row instead of failing")
        void retriesOnceAfterLosingInsertRace() {
            // Given: the writer's own REQUIRES_NEW transaction was already rolled back by the
            // failed insert, so recovering means calling the writer again in a fresh transaction
            // - not reusing any state from the failed call.
            when(registrationAttemptWriter.recordAttempt(eq(IP), any(Instant.class), any(Duration.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"))
                    .thenReturn(2);

            // When
            useCase.checkAndRecordAttempt(IP);

            // Then
            verify(registrationAttemptWriter, times(2)).recordAttempt(eq(IP), any(Instant.class), any(Duration.class));
        }

        @Test
        @DisplayName(
                "given the writer loses the insert race twice in a row, when checked, then the second failure propagates rather than retrying indefinitely")
        void propagatesIfRetryAlsoFails() {
            // Given
            DataIntegrityViolationException secondFailure = new DataIntegrityViolationException("duplicate key");
            when(registrationAttemptWriter.recordAttempt(eq(IP), any(Instant.class), any(Duration.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"))
                    .thenThrow(secondFailure);

            // When / Then
            assertThatThrownBy(() -> useCase.checkAndRecordAttempt(IP)).isSameAs(secondFailure);
            verify(registrationAttemptWriter, times(2)).recordAttempt(eq(IP), any(Instant.class), any(Duration.class));
        }
    }
}
