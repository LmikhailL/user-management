package org.mike.usermanagement.ratelimit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
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

class RegistrationAttemptWriterTest {

    private static final String IP = "203.0.113.7";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private RegistrationAttemptRepository repository;
    private RegistrationAttemptWriter writer;

    @BeforeEach
    void setUp() {
        repository = mock(RegistrationAttemptRepository.class);
        writer = new RegistrationAttemptWriter(repository);
        when(repository.saveAndFlush(any(RegistrationAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class NoPriorAttempt {

        @Test
        @DisplayName("given no prior attempt from this IP, when recorded, then a new row is flushed with count 1")
        void recordsFirstAttempt() {
            // Given
            when(repository.findByIpAddress(IP)).thenReturn(Optional.empty());
            Instant now = Instant.now();

            // When
            int attemptCount = writer.recordAttempt(IP, now, WINDOW);

            // Then
            assertThat(attemptCount).isEqualTo(1);
            ArgumentCaptor<RegistrationAttempt> captor = ArgumentCaptor.forClass(RegistrationAttempt.class);
            verify(repository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getIpAddress()).isEqualTo(IP);
            assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        }
    }

    @Nested
    class WithinWindow {

        @Test
        @DisplayName("given a prior attempt within the current window, when recorded, then the count is incremented")
        void incrementsExistingCount() {
            // Given
            RegistrationAttempt existing = new RegistrationAttempt(UUID.randomUUID(), IP, Instant.now(), 4);
            when(repository.findByIpAddress(IP)).thenReturn(Optional.of(existing));

            // When
            int attemptCount = writer.recordAttempt(IP, Instant.now(), WINDOW);

            // Then
            assertThat(attemptCount).isEqualTo(5);
            assertThat(existing.getAttemptCount()).isEqualTo(5);
            verify(repository).saveAndFlush(existing);
        }
    }

    @Nested
    class WindowExpiry {

        @Test
        @DisplayName(
                "given the last window started more than a minute ago, when recorded, then the count resets instead of accumulating")
        void resetsAfterWindowExpires() {
            // Given
            Instant staleWindowStart = Instant.now().minus(90, ChronoUnit.SECONDS);
            RegistrationAttempt existing = new RegistrationAttempt(UUID.randomUUID(), IP, staleWindowStart, 5);
            when(repository.findByIpAddress(IP)).thenReturn(Optional.of(existing));
            Instant now = Instant.now();

            // When
            int attemptCount = writer.recordAttempt(IP, now, WINDOW);

            // Then
            assertThat(attemptCount).isEqualTo(1);
            assertThat(existing.getWindowStart()).isAfter(staleWindowStart);
        }
    }
}
