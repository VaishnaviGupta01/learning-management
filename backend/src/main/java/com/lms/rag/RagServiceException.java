package com.lms.rag;

/**
 * rag-service was unreachable or returned an error. {@link #getUserMessage()} is safe to show to users;
 * {@link #getStatus()} is rag-service's HTTP status, or 0 when it could not be reached.
 */
public class RagServiceException extends RuntimeException {

    private final String userMessage;
    private final int status;

    public RagServiceException(String message, String userMessage, Throwable cause) {
        this(message, userMessage, 0, cause);
    }

    public RagServiceException(String message, String userMessage, int status, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
        this.status = status;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public int getStatus() {
        return status;
    }

    /** The request itself was rejected (bad file, too large, ...), as opposed to the service being unavailable. */
    public boolean isClientError() {
        return status >= 400 && status < 500;
    }
}
