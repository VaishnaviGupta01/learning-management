package com.lms.rag;

/** rag-service was unreachable or returned an error. {@link #getUserMessage()} is safe to show to users. */
public class RagServiceException extends RuntimeException {

    private final String userMessage;

    public RagServiceException(String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
