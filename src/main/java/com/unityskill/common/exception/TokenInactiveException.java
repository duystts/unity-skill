package com.unityskill.common.exception;

public class TokenInactiveException extends RuntimeException {
    public TokenInactiveException() {
        super("Refresh token is inactive or expired");
    }
    public TokenInactiveException(String message) {
        super(message);
    }
}
