package com.unityskill.common.exception;

/**
 * Thrown when an AI provider responds with HTTP 429 (quota / rate-limit exceeded).
 * This exception is NOT retried — the caller should surface a user-friendly message.
 */
public class AiRateLimitException extends RuntimeException {
    public AiRateLimitException(String message) {
        super(message);
    }
}
