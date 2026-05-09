package com.unityskill.common.exception;

public class InvalidAssigneeException extends RuntimeException {
    public InvalidAssigneeException() {
        super("Assignee is not a workspace member");
    }
}
