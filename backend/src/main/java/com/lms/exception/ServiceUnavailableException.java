package com.lms.exception;

/** Mapped to HTTP 503 when a downstream service (e.g. the AI tutor) cannot serve the request. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
