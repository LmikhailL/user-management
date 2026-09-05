package org.mike.usermanagement.verification.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import org.mike.usermanagement.web.generated.model.VerifyEmailResponse;
import org.springframework.beans.factory.annotation.Autowired;

class VerifyEmailIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName(
            "given a freshly registered pending account, when verifying with the token returned from registration, then the account is activated and the response is 200")
    void verifiesFreshRegistration() throws Exception {
        // Given: a real registration over HTTP, whose response carries the raw verification
        // token (no real mailer yet — see US-002 Decision 5)
        String email = "ada.%s@example.com".formatted(UUID.randomUUID());
        String password = "Str0ngPass1!";
        RegisterUserRequest registerRequest = new RegisterUserRequest(email, password, password);
        HttpRequest registerHttpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(registerRequest)))
                .build();
        HttpResponse<String> registerResponse =
                httpClient.send(registerHttpRequest, HttpResponse.BodyHandlers.ofString());
        RegisteredUserResponse registered =
                objectMapper.readValue(registerResponse.body(), RegisteredUserResponse.class);

        // When: verifying with that token over a real HTTP call
        String encodedToken = URLEncoder.encode(registered.getVerificationToken(), StandardCharsets.UTF_8);
        HttpRequest verifyHttpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/users/verify?token=" + encodedToken))
                .GET()
                .build();
        HttpResponse<String> verifyResponse = httpClient.send(verifyHttpRequest, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(verifyResponse.statusCode()).isEqualTo(200);
        VerifyEmailResponse body = objectMapper.readValue(verifyResponse.body(), VerifyEmailResponse.class);
        assertThat(body.getEmail()).isEqualTo(email);
        assertThat(body.getId()).isEqualTo(registered.getId());

        Optional<User> saved = userRepository.findByEmail(email);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
