package com.unityskill.common.exception;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException() {
        super("Workspace not found or access denied");
    }

    public WorkspaceNotFoundException(String message) {
        super(message);
    }
}
