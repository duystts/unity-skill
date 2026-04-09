package com.unityskill.common.exception;

public class AlreadyMemberException extends RuntimeException {

    public AlreadyMemberException() {
        super("Already a member");
    }
}
