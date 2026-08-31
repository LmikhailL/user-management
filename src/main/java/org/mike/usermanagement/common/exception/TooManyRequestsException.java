package org.mike.usermanagement.common.exception;

/**
 * A fourth failure category alongside {@link NotFoundException}, {@link ConflictException} and
 * {@link ValidationException} — rate limiting doesn't semantically fit any of those three.
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
