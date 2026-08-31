package org.mike.usermanagement.user.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.user.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserUseCase.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    // BCrypt silently truncates input beyond 72 bytes; enforcing this as a hard cap keeps the
    // stored hash actually derived from the whole password rather than a truncated prefix.
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisteredUser register(RegisterUserCommand command) {
        String email = validateAndNormalizeEmail(command.email());
        validatePassword(command.password());

        if (!command.password().equals(command.passwordConfirmation())) {
            throw new PasswordMismatchException();
        }

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.now());

        try {
            // saveAndFlush, not save: this entity's id is a client-generated UUID, so a plain
            // save() doesn't need to flush immediately and Hibernate would otherwise queue the
            // INSERT until the enclosing transaction commits — past this try/catch entirely,
            // letting the violation escape uncaught instead of becoming EmailAlreadyRegistered.
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // The unique index is the final authority: two requests for the same email racing
            // past the existsByEmail check above both reach here, and only one insert wins.
            throw new EmailAlreadyRegisteredException();
        }

        log.info("Registered new user id={}", user.getId());

        return new RegisteredUser(user.getId(), user.getEmail());
    }

    private String validateAndNormalizeEmail(String rawEmail) {
        String trimmed = rawEmail == null ? "" : rawEmail.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidRegistrationException("Email is required");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidRegistrationException("Enter a valid email address");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new InvalidRegistrationException("Password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRegistrationException("Password must be at least 8 characters");
        }
        // BCrypt's 72-byte limit is a UTF-8 byte count, not a character count: a multi-byte
        // character (emoji, non-Latin scripts) can pass a naive length() check while still
        // exceeding 72 bytes once encoded, so length must be measured in encoded bytes.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_LENGTH) {
            throw new InvalidRegistrationException("Password must be at most 72 bytes");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new InvalidRegistrationException("Password must contain a letter and a number");
        }
    }
}
