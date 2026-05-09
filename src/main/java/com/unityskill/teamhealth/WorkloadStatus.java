package com.unityskill.teamhealth;

/**
 * Story 8.1: Workload classification for a workspace member based on open ticket count.
 * Thresholds (Story 8.3 will add PM-configurable overrides):
 * - AVAILABLE:  0–1 open tickets
 * - BALANCED:   2–5 open tickets
 * - OVERLOADED: >5 open tickets
 */
public enum WorkloadStatus {
    AVAILABLE, BALANCED, OVERLOADED;

    /**
     * Compute workload status using default thresholds (AVAILABLE 0–1, BALANCED 2–5, OVERLOADED >5).
     * Delegates to the parameterised overload. Used by TeamHealthService (Story 8.1) where no
     * custom workspace thresholds are loaded.
     */
    public static WorkloadStatus fromOpenTicketCount(int count) {
        return fromOpenTicketCount(count, 2, 5);
    }

    /**
     * Story 8.3: Compute workload status using PM-configurable thresholds stored on the Workspace entity.
     *
     * @param count               open ticket count for the member
     * @param balancedMin         lower bound for BALANCED (inclusive); below this → AVAILABLE
     * @param overloadedThreshold upper bound for BALANCED (inclusive); above this → OVERLOADED
     */
    public static WorkloadStatus fromOpenTicketCount(int count, int balancedMin, int overloadedThreshold) {
        if (count > overloadedThreshold) return OVERLOADED;
        if (count >= balancedMin) return BALANCED;
        return AVAILABLE;
    }
}
