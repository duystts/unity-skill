package com.unityskill.common.exception;

public class StageNotFoundException extends RuntimeException {
    public StageNotFoundException() {
        super("Stage not found");
    }
}
