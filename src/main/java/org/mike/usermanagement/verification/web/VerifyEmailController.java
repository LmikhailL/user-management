package org.mike.usermanagement.verification.web;

import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.user.domain.RegisteredUser;
import org.mike.usermanagement.verification.domain.VerifyEmailUseCase;
import org.mike.usermanagement.web.generated.api.VerifyEmailApi;
import org.mike.usermanagement.web.generated.model.VerifyEmailResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VerifyEmailController implements VerifyEmailApi {

    private final VerifyEmailUseCase verifyEmailUseCase;
    private final VerifyEmailWebMapper mapper;

    @Override
    public ResponseEntity<VerifyEmailResponse> verifyEmail(String token) {
        RegisteredUser registeredUser = verifyEmailUseCase.verify(token);
        return ResponseEntity.ok(mapper.toResponse(registeredUser));
    }
}
