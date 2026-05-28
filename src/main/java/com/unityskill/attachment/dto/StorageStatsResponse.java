package com.unityskill.attachment.dto;

public record StorageStatsResponse(
    long usedBytes,
    long limitBytes,
    long fileCount
) {
    public int usedPercent() {
        return limitBytes > 0 ? (int) (usedBytes * 100 / limitBytes) : 0;
    }
}
