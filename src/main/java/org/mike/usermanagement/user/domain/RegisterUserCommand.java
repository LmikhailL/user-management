package org.mike.usermanagement.user.domain;

public record RegisterUserCommand(String email, String password, String passwordConfirmation) {}
