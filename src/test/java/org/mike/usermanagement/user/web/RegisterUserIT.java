package org.mike.usermanagement.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mike.usermanagement.AbstractIntegrationTest;
import org.mike.usermanagement.user.domain.User;
import org.mike.usermanagement.user.domain.UserStatus;
import org.mike.usermanagement.user.persistence.UserRepository;
import org.mike.usermanagement.web.generated.model.RegisterUserRequest;
import org.mike.usermanagement.web.generated.model.RegisteredUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegisterUserIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName(
            "given no user exists with the email, when POSTing valid registration details, then a pending_verification account is created, a verification token is returned, and no session cookie is set")
    void registersNewUser() throws Exception {
        // Given
        String email = "ada.%s@example.com".formatted(UUID.randomUUID());
        String password = "Str0ngPass1!";
        RegisterUserRequest request = new RegisterUserRequest(email, password, password);

        // When
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(201);

        RegisteredUserResponse body = objectMapper.readValue(response.body(), RegisteredUserResponse.class);
        assertThat(body.getEmail()).isEqualTo(email);
        assertThat(body.getId()).isNotNull();
        assertThat(body.getVerificationToken()).isNotBlank();

        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();

        Optional<User> saved = userRepository.findByEmail(email);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(passwordEncoder.matches(password, saved.get().getPasswordHash()))
                .isTrue();
    }
}
