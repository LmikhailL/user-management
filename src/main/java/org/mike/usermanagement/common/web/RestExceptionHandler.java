package org.mike.usermanagement.common.web;

import org.mike.usermanagement.common.exception.ConflictException;
import org.mike.usermanagement.common.exception.NotFoundException;
import org.mike.usermanagement.common.exception.TooManyRequestsException;
import org.mike.usermanagement.common.exception.ValidationException;
import org.mike.usermanagement.web.generated.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return error(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", e.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String category, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, category));
    }
}
