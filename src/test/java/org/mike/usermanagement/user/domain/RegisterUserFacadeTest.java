package org.mike.usermanagement.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mike.usermanagement.ratelimit.domain.RegistrationRateLimiterUseCase;
import org.mike.usermanagement.ratelimit.domain.TooManyRegistrationAttemptsException;
import org.mike.usermanagement.verification.domain.IssueVerificationTokenUseCase;

class RegisterUserFacadeTest {

    private static final String IP = "203.0.113.7";

    private final RegistrationRateLimiterUseCase registrationRateLimiterUseCase =
            mock(RegistrationRateLimiterUseCase.class);
    private final RegisterUserUseCase registerUserUseCase = mock(RegisterUserUseCase.class);
    private final IssueVerificationTokenUseCase issueVerificationTokenUseCase =
            mock(IssueVerificationTokenUseCase.class);
    private final RegisterUserFacade facade =
            new RegisterUserFacade(registrationRateLimiterUseCase, registerUserUseCase, issueVerificationTokenUseCase);

    @Test
    @DisplayName(
            "given the rate limit is not exceeded, when registering, then the rate limiter is checked, the user is registered, and a verification token is issued for it")
    void registersWhenWithinLimit() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand("ada@example.com", "Str0ngPass1!", "Str0ngPass1!");
        RegisteredUser expectedUser = new RegisteredUser(UUID.randomUUID(), "ada@example.com");
        when(registerUserUseCase.register(command)).thenReturn(expectedUser);
        when(issueVerificationTokenUseCase.issue(expectedUser.id())).thenReturn("raw-token");

        // When
        RegistrationResult result = facade.register(command, IP);

        // Then
        assertThat(result).isEqualTo(new RegistrationResult(expectedUser, "raw-token"));
    }

    @Test
    @DisplayName(
            "given the rate limit is exceeded, when registering, then it fails without ever calling the register use case")
    void skipsRegistrationWhenRateLimited() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand("ada@example.com", "Str0ngPass1!", "Str0ngPass1!");
        TooManyRegistrationAttemptsException rateLimitFailure = new TooManyRegistrationAttemptsException();
        org.mockito.Mockito.doThrow(rateLimitFailure)
                .when(registrationRateLimiterUseCase)
                .checkAndRecordAttempt(IP);

        // When / Then
        assertThatThrownBy(() -> facade.register(command, IP)).isSameAs(rateLimitFailure);
        verifyNoInteractions(registerUserUseCase);
    }
}
