package com.unityskill.teamhealth.dto;

import com.unityskill.teamhealth.WorkloadStatus;

import java.util.List;

public record WorkloadResponse(List<MemberWorkloadInfo> members) {

    public record MemberWorkloadInfo(
            String userId,
            String displayName,
            String role,
            int openTicketCount,
            int inProgressTicketCount,   // open tickets that also have a stageId (not stage-less)
            WorkloadStatus workloadStatus
    ) {}
}
