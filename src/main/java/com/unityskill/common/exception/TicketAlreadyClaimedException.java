package com.unityskill.common.exception;

public class TicketAlreadyClaimedException extends RuntimeException {
    public TicketAlreadyClaimedException() {
        super("Ticket already claimed");
    }
}
