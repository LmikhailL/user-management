package org.mike.usermanagement.user.domain;

import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.ratelimit.domain.RegistrationRateLimiterUseCase;
import org.mike.usermanagement.verification.domain.IssueVerificationTokenUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserFacade {

    private final RegistrationRateLimiterUseCase registrationRateLimiterUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final IssueVerificationTokenUseCase issueVerificationTokenUseCase;

    @Transactional
    public RegistrationResult register(RegisterUserCommand command, String ipAddress) {
        registrationRateLimiterUseCase.checkAndRecordAttempt(ipAddress);
        RegisteredUser registeredUser = registerUserUseCase.register(command);
        String verificationToken = issueVerificationTokenUseCase.issue(registeredUser.id());
        return new RegistrationResult(registeredUser, verificationToken);
    }
}
