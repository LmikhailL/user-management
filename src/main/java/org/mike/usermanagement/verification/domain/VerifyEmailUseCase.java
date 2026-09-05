package org.mike.usermanagement.verification.domain;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.user.domain.RegisteredUser;
import org.mike.usermanagement.user.domain.User;
import org.mike.usermanagement.user.domain.UserStatus;
import org.mike.usermanagement.user.persistence.UserRepository;
import org.mike.usermanagement.verification.persistence.VerificationToken;
import org.mike.usermanagement.verification.persistence.VerificationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerifyEmailUseCase {

    private final VerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public RegisteredUser verify(String rawToken) {
        String tokenHash = VerificationTokenHasher.hash(rawToken == null ? "" : rawToken);
        VerificationToken token = verificationTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(InvalidOrExpiredVerificationTokenException::new);

        Instant now = Instant.now();
        if (token.getExpiresAt().isBefore(now)) {
            throw new InvalidOrExpiredVerificationTokenException();
        }

        // The affected-row count is the atomic single-use gate (see
        // VerificationTokenRepository.consumeIfUnused): 0 rows means either this token was
        // already consumed by an earlier verification (AC-5), or that earlier verification is
        // exactly what already activated the account this token points to (AC-6) — both look
        // identical from here, which is the behavior the spec deliberately calls for.
        int consumed = verificationTokenRepository.consumeIfUnused(token.getId(), now);
        if (consumed == 0) {
            throw new InvalidOrExpiredVerificationTokenException();
        }

        User user =
                userRepository.findById(token.getUserId()).orElseThrow(InvalidOrExpiredVerificationTokenException::new);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        return new RegisteredUser(user.getId(), user.getEmail());
    }
}
