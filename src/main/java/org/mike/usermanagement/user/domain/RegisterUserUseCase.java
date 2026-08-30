package org.mike.usermanagement.user.domain;

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
            user = userRepository.save(user);
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
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidRegistrationException("Password must be at most 72 characters");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new InvalidRegistrationException("Password must contain a letter and a number");
        }
    }
}
