package com.unityskill.common.exception;

public class AlreadyEndorsedException extends RuntimeException {
    public AlreadyEndorsedException() {
        super("Already endorsed");
    }
}
