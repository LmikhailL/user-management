package org.mike.usermanagement.user.domain;

public record RegistrationResult(RegisteredUser user, String verificationToken) {}
