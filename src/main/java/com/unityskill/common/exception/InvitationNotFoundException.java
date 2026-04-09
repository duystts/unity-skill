package com.unityskill.common.exception;

public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException() {
        super("Invitation not found or expired");
    }
}
