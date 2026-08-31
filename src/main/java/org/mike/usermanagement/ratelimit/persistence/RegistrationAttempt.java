package org.mike.usermanagement.ratelimit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "registration_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ip_address", nullable = false, unique = true)
    private String ipAddress;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
}
