package org.mike.usermanagement.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        .requestMatchers(matcher.matcher(REGISTER_USER_PATH), matcher.matcher(ACTUATOR_HEALTH_PATH))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // Registration is a JSON API call, not a browser form submission, and the
                // session it creates is a *result* of the call rather than something used to
                // authenticate the call itself — so CSRF protection doesn't apply here.
                .csrf(csrf -> csrf.ignoringRequestMatchers(matcher.matcher(REGISTER_USER_PATH)));

        return http.build();
    }
}
