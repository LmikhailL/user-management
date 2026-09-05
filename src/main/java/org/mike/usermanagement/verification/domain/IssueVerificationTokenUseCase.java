package org.mike.usermanagement.verification.domain;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.verification.persistence.VerificationToken;
import org.mike.usermanagement.verification.persistence.VerificationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IssueVerificationTokenUseCase {

    private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final VerificationTokenRepository verificationTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issue(UUID userId) {
        byte[] rawBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);

        Instant now = Instant.now();
        VerificationToken token = new VerificationToken();
        token.setUserId(userId);
        token.setTokenHash(VerificationTokenHasher.hash(rawToken));
        token.setExpiresAt(now.plus(TOKEN_VALIDITY));
        token.setCreatedAt(now);

        verificationTokenRepository.save(token);

        // Returned to the caller, never logged: a verification token is a bearer credential,
        // and AGENTS.md's "never log secrets or PII" rule applies to it exactly as it does to
        // passwords. Until a real mailer exists, the raw token is surfaced only in the
        // registration response body — see US-002's Decision 5.
        return rawToken;
    }
}
