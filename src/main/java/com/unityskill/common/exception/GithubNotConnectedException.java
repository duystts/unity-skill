package com.unityskill.common.exception;

public class GithubNotConnectedException extends RuntimeException {
    public GithubNotConnectedException() {
        super("GitHub repository not connected");
    }
}
