package org.mike.usermanagement.user.domain;

import org.mike.usermanagement.ratelimit.domain.RegistrationRateLimiterUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserFacade {

    private final RegistrationRateLimiterUseCase registrationRateLimiterUseCase;
    private final RegisterUserUseCase registerUserUseCase;

    public RegisterUserFacade(
            RegistrationRateLimiterUseCase registrationRateLimiterUseCase, RegisterUserUseCase registerUserUseCase) {
        this.registrationRateLimiterUseCase = registrationRateLimiterUseCase;
        this.registerUserUseCase = registerUserUseCase;
    }

    @Transactional
    public RegisteredUser register(RegisterUserCommand command, String ipAddress) {
        registrationRateLimiterUseCase.checkAndRecordAttempt(ipAddress);
        return registerUserUseCase.register(command);
    }
}
