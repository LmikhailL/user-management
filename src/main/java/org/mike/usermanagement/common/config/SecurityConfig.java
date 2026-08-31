package org.mike.usermanagement.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String REGISTER_USER_PATH = "/api/users";
    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        PathPatternRequestMatcher.Builder matcher = PathPatternRequestMatcher.withDefaults();

        http.authorizeHttpRequests(authorize -> authorize
                        // Method-scoped matchers: an unscoped matcher.matcher(path) permits every
                        // HTTP verb on that path, not just the one this endpoint actually exposes.
                        // That's harmless today since nothing else is mapped to /api/users, but it
                        // would silently become a public hole the moment a GET/PUT/DELETE is added
                        // there without anyone revisiting this config.
                        .requestMatchers(
                                matcher.matcher(HttpMethod.POST, REGISTER_USER_PATH),
                                matcher.matcher(HttpMethod.GET, ACTUATOR_HEALTH_PATH))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // Registration is a JSON API call, not a browser form submission, and the
                // session it creates is a *result* of the call rather than something used to
                // authenticate the call itself — so CSRF protection doesn't apply here. Scoped to
                // POST for the same reason as the authorization matcher above: an unscoped
                // matcher would exempt every verb on this path from CSRF protection, which is the
                // more security-sensitive of the two to leave broad.
                .csrf(csrf -> csrf.ignoringRequestMatchers(matcher.matcher(HttpMethod.POST, REGISTER_USER_PATH)));

        return http.build();
    }
}
