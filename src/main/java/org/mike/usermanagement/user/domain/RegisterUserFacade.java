package org.mike.usermanagement.user.domain;

import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.ratelimit.domain.RegistrationRateLimiterUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserFacade {

    private final RegistrationRateLimiterUseCase registrationRateLimiterUseCase;
    private final RegisterUserUseCase registerUserUseCase;

    @Transactional
    public RegisteredUser register(RegisterUserCommand command, String ipAddress) {
        registrationRateLimiterUseCase.checkAndRecordAttempt(ipAddress);
        return registerUserUseCase.register(command);
    }
}
