package org.mike.usermanagement.user.domain;

import org.mike.usermanagement.common.exception.ValidationException;

public class PasswordMismatchException extends ValidationException {

    public PasswordMismatchException() {
        super("Passwords do not match");
    }
}
