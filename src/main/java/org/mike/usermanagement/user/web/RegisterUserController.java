package org.mike.usermanagement.user.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.user.domain.RegisterUserFacade;
import org.mike.usermanagement.user.domain.RegistrationResult;
import org.mike.usermanagement.web.generated.api.RegisterUserApi;
import org.mike.usermanagement.web.generated.model.RegisterUserRequest;
import org.mike.usermanagement.web.generated.model.RegisteredUserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegisterUserController implements RegisterUserApi {

    private final RegisterUserFacade registerUserFacade;
    private final RegisterUserWebMapper mapper;
    private final HttpServletRequest httpServletRequest;

    // A registration no longer yields an immediately-usable account (it starts
    // pending_verification, per US-002), so this endpoint never signs the caller in or sets a
    // session cookie — that only happens once a future sign-in story exists, or after
    // verification activates the account.
    @Override
    public ResponseEntity<RegisteredUserResponse> registerUser(RegisterUserRequest registerUserRequest) {
        RegistrationResult registrationResult =
                registerUserFacade.register(mapper.toCommand(registerUserRequest), httpServletRequest.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(registrationResult));
    }
}
