package com.twinmind.live.exception;

public class GroqApiException extends RuntimeException {

    public GroqApiException(String message) {
        super(message);
    }

    public GroqApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
