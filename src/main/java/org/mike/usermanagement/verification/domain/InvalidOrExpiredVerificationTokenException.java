package org.mike.usermanagement.verification.domain;

import org.mike.usermanagement.common.exception.NotFoundException;

public class InvalidOrExpiredVerificationTokenException extends NotFoundException {

    public InvalidOrExpiredVerificationTokenException() {
        super("Invalid or expired verification link");
    }
}
