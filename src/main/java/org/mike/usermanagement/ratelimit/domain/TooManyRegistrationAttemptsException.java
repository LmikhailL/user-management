package org.mike.usermanagement.ratelimit.domain;

import org.mike.usermanagement.common.exception.TooManyRequestsException;

public class TooManyRegistrationAttemptsException extends TooManyRequestsException {

    public TooManyRegistrationAttemptsException() {
        super("Too many attempts, please try again later");
    }
}
