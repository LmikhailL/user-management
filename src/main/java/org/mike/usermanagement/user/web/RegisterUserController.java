package org.mike.usermanagement.user.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.mike.usermanagement.user.domain.RegisterUserFacade;
import org.mike.usermanagement.user.domain.RegisteredUser;
import org.mike.usermanagement.web.generated.api.RegisterUserApi;
import org.mike.usermanagement.web.generated.model.RegisterUserRequest;
import org.mike.usermanagement.web.generated.model.RegisteredUserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegisterUserController implements RegisterUserApi {

    private final RegisterUserFacade registerUserFacade;
    private final RegisterUserWebMapper mapper;
    private final HttpServletRequest httpServletRequest;

    @Override
    public ResponseEntity<RegisteredUserResponse> registerUser(RegisterUserRequest registerUserRequest) {
        RegisteredUser registeredUser =
                registerUserFacade.register(mapper.toCommand(registerUserRequest), httpServletRequest.getRemoteAddr());

        signIn(registeredUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(registeredUser));
    }

    // Registration bypasses the normal login filter chain, so the session's SecurityContext has
    // to be established manually here — this is what makes the Set-Cookie session actually
    // authenticate the caller on their next request, not just exist.
    private void signIn(RegisteredUser registeredUser) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(registeredUser.email(), null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
