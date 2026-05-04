package com.unityskill.common.exception;

public class MeetingNotFoundException extends RuntimeException {

    public MeetingNotFoundException() {
        super("Meeting not found");
    }
}
