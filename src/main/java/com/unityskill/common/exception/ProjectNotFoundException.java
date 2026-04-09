package com.unityskill.common.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException() {
        super("Project not found");
    }
}
