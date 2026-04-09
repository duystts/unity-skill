package com.unityskill.common.exception;

public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("Cannot remove the only Admin");
    }
}
