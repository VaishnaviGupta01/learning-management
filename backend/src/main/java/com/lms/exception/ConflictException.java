package com.lms.exception;

/** Mapped to HTTP 409 (duplicate email, duplicate edge, already-submitted attempt, ...). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
