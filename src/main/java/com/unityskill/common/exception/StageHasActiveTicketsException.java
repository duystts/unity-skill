package com.unityskill.common.exception;

public class StageHasActiveTicketsException extends RuntimeException {
    public StageHasActiveTicketsException() {
        super("Stage has active tickets \u2014 reassign first");
    }
}
