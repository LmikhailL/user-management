package org.mike.usermanagement.user.domain;

import org.mike.usermanagement.common.exception.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException() {
        super("That email is already registered");
    }
}
