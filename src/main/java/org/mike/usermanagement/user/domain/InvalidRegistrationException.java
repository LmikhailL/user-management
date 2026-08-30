package org.mike.usermanagement.user.domain;

import org.mike.usermanagement.common.exception.ValidationException;

public class InvalidRegistrationException extends ValidationException {

    public InvalidRegistrationException(String message) {
        super(message);
    }
}
