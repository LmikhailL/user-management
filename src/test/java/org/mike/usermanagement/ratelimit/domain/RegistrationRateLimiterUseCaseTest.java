package org.mike.usermanagement.ratelimit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mike.usermanagement.ratelimit.persistence.RegistrationAttempt;
import org.mike.usermanagement.ratelimit.persistence.RegistrationAttemptRepository;
import org.mockito.ArgumentCaptor;

class RegistrationRateLimiterUseCaseTest {

    private static final String IP = "203.0.113.7";

    private RegistrationAttemptRepository repository;
    private RegistrationRateLimiterUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(RegistrationAttemptRepository.class);
        useCase = new RegistrationRateLimiterUseCase(repository);
        when(repository.save(any(RegistrationAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class WithinLimit {

        @Test
        @DisplayName(
                "given no prior attempts from this IP, when the first attempt is made, then it is recorded and allowed")
        void allowsFirstAttempt() {
            // Given
            when(repository.findByIpAddress(IP)).thenReturn(Optional.empty());

            // When / Then
            useCase.checkAndRecordAttempt(IP);

            ArgumentCaptor<RegistrationAttempt> captor = ArgumentCaptor.forClass(RegistrationAttempt.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("given 4 prior attempts in the current window, when the 5th attempt is made, then it is allowed")
        void allowsFifthAttempt() {
            // Given
            RegistrationAttempt existing = new RegistrationAttempt(UUID.randomUUID(), IP, Instant.now(), 4);
            when(repository.findByIpAddress(IP)).thenReturn(Optional.of(existing));

            // When / Then
            useCase.checkAndRecordAttempt(IP);

            assertThat(existing.getAttemptCount()).isEqualTo(5);
        }
    }

    @Nested
    class OverLimit {

        @Test
        @DisplayName(
                "given 5 attempts already made in the last minute, when a 6th attempt is made, then it fails with the rate-limit message")
        void rejectsSixthAttempt() {
            // Given
            RegistrationAttempt existing = new RegistrationAttempt(UUID.randomUUID(), IP, Instant.now(), 5);
            when(repository.findByIpAddress(IP)).thenReturn(Optional.of(existing));

            // When / Then
            assertThatThrownBy(() -> useCase.checkAndRecordAttempt(IP))
                    .isInstanceOf(TooManyRegistrationAttemptsException.class)
                    .hasMessage("Too many attempts, please try again later");
        }

        @Test
        @DisplayName(
                "given the 6th attempt is rejected, when it is recorded, then the count is still persisted so further attempts stay blocked")
        void stillPersistsCountOnRejection() {
            // Given
            RegistrationAttempt existing = new RegistrationAttempt(UUID.randomUUID(), IP, Instant.now(), 5);
            when(repository.findByIpAddress(IP)).thenReturn(Optional.of(existing));

            // When
            try {
                useCase.checkAndRecordAttempt(IP);
            } catch (TooManyRegistrationAttemptsException ignored) {
                // expected
            }

            // Then
            verify(repository).save(eq(existing));
            assertThat(existing.getAttemptCount()).isEqualTo(6);
        }
    }

    @Nested
    class WindowExpiry {

        @Test
        @DisplayName(
                "given the last window started more than a minute ago, when a new attempt is made, then the count resets instead of accumulating")
        void resetsAfterWindowExpires() {
            // Given
            Instant staleWindowStart = Instant.now().minus(90, ChronoUnit.SECONDS);
            RegistrationAttempt existing = new RegistrationAttempt(UUID.randomUUID(), IP, staleWindowStart, 5);
            when(repository.findByIpAddress(IP)).thenReturn(Optional.of(existing));

            // When / Then
            useCase.checkAndRecordAttempt(IP);

            assertThat(existing.getAttemptCount()).isEqualTo(1);
            assertThat(existing.getWindowStart()).isAfter(staleWindowStart);
        }
    }
}
