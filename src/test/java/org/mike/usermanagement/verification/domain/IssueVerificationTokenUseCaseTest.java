package org.mike.usermanagement.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mike.usermanagement.verification.persistence.VerificationToken;
import org.mike.usermanagement.verification.persistence.VerificationTokenRepository;
import org.mockito.ArgumentCaptor;

class IssueVerificationTokenUseCaseTest {

    private final VerificationTokenRepository verificationTokenRepository = mock(VerificationTokenRepository.class);
    private final IssueVerificationTokenUseCase useCase =
            new IssueVerificationTokenUseCase(verificationTokenRepository);

    @Test
    @DisplayName(
            "given a user id, when issuing a verification token, then a hashed, 24h-expiring token is persisted and the raw token is returned")
    void issuesHashedSingleUseToken() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        // When
        String rawToken = useCase.issue(userId);

        // Then
        Instant after = Instant.now();
        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(captor.capture());
        VerificationToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getConsumedAt()).isNull();
        assertThat(saved.getCreatedAt()).isBetween(before, after);
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plus(Duration.ofHours(24)));

        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(rawToken));
    }

    @Test
    @DisplayName("given two issuances, when tokens are generated, then each raw token is different")
    void generatesDistinctTokensPerCall() {
        // Given / When
        String first = useCase.issue(UUID.randomUUID());
        String second = useCase.issue(UUID.randomUUID());

        // Then
        assertThat(first).isNotEqualTo(second);
        verify(verificationTokenRepository, org.mockito.Mockito.times(2)).save(any(VerificationToken.class));
    }

    private static String sha256Hex(String raw) throws Exception {
        byte[] digest =
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
