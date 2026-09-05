package org.mike.usermanagement.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mike.usermanagement.user.domain.RegisteredUser;
import org.mike.usermanagement.user.domain.User;
import org.mike.usermanagement.user.domain.UserStatus;
import org.mike.usermanagement.user.persistence.UserRepository;
import org.mike.usermanagement.verification.persistence.VerificationToken;
import org.mike.usermanagement.verification.persistence.VerificationTokenRepository;
import org.mockito.ArgumentCaptor;

class VerifyEmailUseCaseTest {

    private static final String RAW_TOKEN = "a-raw-token-value";

    private final VerificationTokenRepository verificationTokenRepository = mock(VerificationTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final VerifyEmailUseCase useCase = new VerifyEmailUseCase(verificationTokenRepository, userRepository);

    private static VerificationToken tokenFor(UUID userId, Instant expiresAt) {
        VerificationToken token = new VerificationToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(VerificationTokenHasher.hash(RAW_TOKEN));
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(expiresAt.minus(24, ChronoUnit.HOURS));
        return token;
    }

    private static User pendingUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("ada@example.com");
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        return user;
    }

    private void stubLookup(VerificationToken token) {
        when(verificationTokenRepository.findByTokenHash(VerificationTokenHasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));
    }

    @Nested
    class SuccessfulVerification {

        @Test
        @DisplayName(
                "given a valid, unexpired, unused token, when verifying, then the account is activated and the token is consumed")
        void activatesAccountAndConsumesToken() {
            // Given
            UUID userId = UUID.randomUUID();
            VerificationToken token = tokenFor(userId, Instant.now().plusSeconds(3600));
            stubLookup(token);
            when(verificationTokenRepository.consumeIfUnused(eq(token.getId()), any()))
                    .thenReturn(1);
            User user = pendingUser(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // When
            RegisteredUser result = useCase.verify(RAW_TOKEN);

            // Then
            assertThat(result).isEqualTo(new RegisteredUser(userId, "ada@example.com"));
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

            ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    @Nested
    class UnknownOrMalformedToken {

        @Test
        @DisplayName("given no token matches the supplied value, when verifying, then it fails as invalid or expired")
        void rejectsUnknownToken() {
            // Given
            when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> useCase.verify(RAW_TOKEN))
                    .isInstanceOf(InvalidOrExpiredVerificationTokenException.class)
                    .hasMessage("Invalid or expired verification link");
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName(
                "given a null token (missing query parameter), when verifying, then it fails the same as an unknown token rather than throwing on the null")
        void treatsNullTokenAsUnknown() {
            // Given
            when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> useCase.verify(null))
                    .isInstanceOf(InvalidOrExpiredVerificationTokenException.class)
                    .hasMessage("Invalid or expired verification link");
            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    class ExpiredToken {

        @Test
        @DisplayName(
                "given a token whose expiry has passed, when verifying, then it fails as invalid or expired and the account stays pending")
        void rejectsExpiredToken() {
            // Given
            UUID userId = UUID.randomUUID();
            VerificationToken token = tokenFor(userId, Instant.now().minusSeconds(1));
            stubLookup(token);

            // When / Then
            assertThatThrownBy(() -> useCase.verify(RAW_TOKEN))
                    .isInstanceOf(InvalidOrExpiredVerificationTokenException.class)
                    .hasMessage("Invalid or expired verification link");
            verify(verificationTokenRepository, never()).consumeIfUnused(any(), any());
            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    class AlreadyUsedToken {

        @Test
        @DisplayName(
                "given a token that was already consumed by an earlier verification, when verifying again, then it fails as invalid or expired")
        void rejectsReusedToken() {
            // Given
            UUID userId = UUID.randomUUID();
            VerificationToken token = tokenFor(userId, Instant.now().plusSeconds(3600));
            stubLookup(token);
            when(verificationTokenRepository.consumeIfUnused(eq(token.getId()), any()))
                    .thenReturn(0);

            // When / Then
            assertThatThrownBy(() -> useCase.verify(RAW_TOKEN))
                    .isInstanceOf(InvalidOrExpiredVerificationTokenException.class)
                    .hasMessage("Invalid or expired verification link");
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName(
                "given a token tied to an account that is already active (consumed by the verification that activated it), when verifying again, then it fails identically to a reused token")
        void rejectsTokenBehindAlreadyActiveAccount() {
            // Given: same mechanism as rejectsReusedToken — consumeIfUnused finds 0 unconsumed
            // rows because this token was already spent activating the account.
            UUID userId = UUID.randomUUID();
            VerificationToken token = tokenFor(userId, Instant.now().plusSeconds(3600));
            stubLookup(token);
            when(verificationTokenRepository.consumeIfUnused(eq(token.getId()), any()))
                    .thenReturn(0);

            // When / Then
            assertThatThrownBy(() -> useCase.verify(RAW_TOKEN))
                    .isInstanceOf(InvalidOrExpiredVerificationTokenException.class)
                    .hasMessage("Invalid or expired verification link");
        }
    }
}
