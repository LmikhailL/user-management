package org.mike.usermanagement.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mike.usermanagement.user.persistence.UserRepository;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegisterUserUseCaseTest {

    private static final String VALID_PASSWORD = "Str0ngPass1!";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RegisterUserUseCase useCase;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(12);
        useCase = new RegisterUserUseCase(userRepository, passwordEncoder);

        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(RegisterUserUseCase.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(RegisterUserUseCase.class)).detachAppender(logAppender);
    }

    @Nested
    class SuccessfulRegistration {

        @Test
        @DisplayName(
                "given no user exists with the email, when registering with valid input, then a pending_verification user is created and returned")
        void registersPendingVerificationUser() {
            // Given
            when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
            RegisterUserCommand command = new RegisterUserCommand("ada@example.com", VALID_PASSWORD, VALID_PASSWORD);

            // When
            RegisteredUser result = useCase.register(command);

            // Then
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo("ada@example.com");
            assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(result.email()).isEqualTo("ada@example.com");
            assertThat(result.id()).isEqualTo(saved.getId());
        }
    }

    @Nested
    class PasswordHashing {

        @Test
        @DisplayName(
                "given a valid registration, when the password is stored, then it is a bcrypt hash and not the raw password")
        void storesBcryptHash() {
            // Given
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            RegisterUserCommand command = new RegisterUserCommand("ada@example.com", VALID_PASSWORD, VALID_PASSWORD);

            // When
            useCase.register(command);

            // Then
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(captor.capture());
            String storedHash = captor.getValue().getPasswordHash();
            assertThat(storedHash).isNotEqualTo(VALID_PASSWORD);
            assertThat(storedHash).startsWith("$2");
            assertThat(passwordEncoder.matches(VALID_PASSWORD, storedHash)).isTrue();
        }

        @Test
        @DisplayName(
                "given a valid registration, when it completes, then the raw password never appears in any log line")
        void neverLogsRawPassword() {
            // Given
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            RegisterUserCommand command = new RegisterUserCommand("ada@example.com", VALID_PASSWORD, VALID_PASSWORD);

            // When
            useCase.register(command);

            // Then
            assertThat(logAppender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(VALID_PASSWORD));
        }
    }

    @Nested
    class EmailNormalization {

        @Test
        @DisplayName(
                "given an email with surrounding whitespace and mixed case, when registering, then the stored and returned email is trimmed and lowercased")
        void normalizesEmail() {
            // Given
            when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
            RegisterUserCommand command =
                    new RegisterUserCommand("  Ada@Example.COM  ", VALID_PASSWORD, VALID_PASSWORD);

            // When
            RegisteredUser result = useCase.register(command);

            // Then
            assertThat(result.email()).isEqualTo("ada@example.com");
            verify(userRepository).existsByEmail("ada@example.com");
        }
    }

    @Nested
    class DuplicateEmail {

        @Test
        @DisplayName(
                "given a user already exists with the email, when registering again, then it fails with the duplicate-email message and never saves")
        void rejectsKnownDuplicate() {
            // Given
            when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);
            RegisterUserCommand command = new RegisterUserCommand("ada@example.com", VALID_PASSWORD, VALID_PASSWORD);

            // When / Then
            assertThatThrownBy(() -> useCase.register(command))
                    .isInstanceOf(EmailAlreadyRegisteredException.class)
                    .hasMessage("That email is already registered");
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName(
                "given two requests race past the uniqueness check, when the database rejects the insert, then it fails with the duplicate-email message")
        void rejectsRaceOnUniqueIndex() {
            // Given
            when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));
            RegisterUserCommand command = new RegisterUserCommand("ada@example.com", VALID_PASSWORD, VALID_PASSWORD);

            // When / Then
            assertThatThrownBy(() -> useCase.register(command))
                    .isInstanceOf(EmailAlreadyRegisteredException.class)
                    .hasMessage("That email is already registered");
        }
    }

    @Nested
    class PasswordConfirmation {

        @Test
        @DisplayName(
                "given a password and a different confirmation, when registering, then it fails with the mismatch message and touches no repository")
        void rejectsMismatch() {
            // Given
            RegisterUserCommand command = new RegisterUserCommand("ada@example.com", "Str0ngPass1!", "Str0ngPass2!");

            // When / Then
            assertThatThrownBy(() -> useCase.register(command))
                    .isInstanceOf(PasswordMismatchException.class)
                    .hasMessage("Passwords do not match");
            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    class InvalidInput {

        @ParameterizedTest(name = "email=\"{0}\", password=\"{1}\" -> {2}")
        @MethodSource("org.mike.usermanagement.user.domain.RegisterUserUseCaseTest#invalidInputCases")
        @DisplayName(
                "given invalid input, when registering, then it fails with the matching validation message and touches no repository")
        void rejectsInvalidInput(String email, String password, String expectedMessage) {
            // Given
            RegisterUserCommand command = new RegisterUserCommand(email, password, password);

            // When / Then
            assertThatThrownBy(() -> useCase.register(command))
                    .isInstanceOf(InvalidRegistrationException.class)
                    .hasMessage(expectedMessage);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName(
                "given a password longer than bcrypt's 72-byte limit, when registering, then it fails rather than silently truncating")
        void rejectsOverlongPassword() {
            // Given
            String tooLong = "Aa1".repeat(30);
            RegisterUserCommand command = new RegisterUserCommand("ada@example.com", tooLong, tooLong);

            // When / Then
            assertThatThrownBy(() -> useCase.register(command))
                    .isInstanceOf(InvalidRegistrationException.class)
                    .hasMessage("Password must be at most 72 bytes");
        }

        @Test
        @DisplayName(
                "given a password within bcrypt's 72-character count but over 72 UTF-8 bytes due to multi-byte characters, when registering, then it fails rather than silently truncating")
        void rejectsPasswordOverlongInBytesButNotChars() {
            // Given: 62 UTF-16 chars (under the old, buggy character-count limit), but each 'é'
            // encodes to 2 UTF-8 bytes, so the true encoded length is 122 bytes — over the limit.
            String password = "é".repeat(60) + "A1";
            assertThat(password.length()).isLessThanOrEqualTo(72);
            RegisterUserCommand command = new RegisterUserCommand("ada@example.com", password, password);

            // When / Then
            assertThatThrownBy(() -> useCase.register(command))
                    .isInstanceOf(InvalidRegistrationException.class)
                    .hasMessage("Password must be at most 72 bytes");
        }
    }

    static Stream<Arguments> invalidInputCases() {
        return Stream.of(
                Arguments.of("", "Str0ngPass1!", "Email is required"),
                Arguments.of("not-an-email", "Str0ngPass1!", "Enter a valid email address"),
                Arguments.of("ada@example.com", "", "Password is required"),
                Arguments.of("ada@example.com", "Sh0rt1!", "Password must be at least 8 characters"),
                Arguments.of("ada@example.com", "passwordonly", "Password must contain a letter and a number"));
    }
}
