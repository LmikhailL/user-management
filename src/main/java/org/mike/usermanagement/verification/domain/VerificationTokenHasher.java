package org.mike.usermanagement.verification.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Shared by IssueVerificationTokenUseCase (hash on write) and VerifyEmailUseCase (hash on
// lookup) so both sides of the single-use-token flow always compute the digest the same way.
final class VerificationTokenHasher {

    private VerificationTokenHasher() {}

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JVM-mandated algorithm (JLS/JCA baseline); this can't happen at runtime.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
