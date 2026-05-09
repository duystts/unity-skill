package com.unityskill.teamhealth.dto;

import com.unityskill.teamhealth.WorkloadStatus;

import java.util.List;

public record TeamHealthResponse(
        int totalOpenTickets,
        int overdueTickets,
        List<MemberHealthInfo> members
) {
    public record MemberHealthInfo(
            String userId,
            String displayName,
            String role,
            int openTicketCount,
            String lastActivityDate,   // ISO instant string, null if no activity
            WorkloadStatus workloadStatus
    ) {}
}
