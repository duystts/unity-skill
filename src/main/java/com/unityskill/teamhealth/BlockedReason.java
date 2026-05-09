package com.unityskill.teamhealth;

public enum BlockedReason {
    /** (a) AssignmentMode.OPEN_POOL ticket with no assignee for > 24 h. */
    UNASSIGNED_OPEN_POOL,

    /** (b) Ticket with a githubPrUrl set but no state change for > 48 h. */
    PENDING_PR_REVIEW,

    /** (c) Ticket in a workflow stage but no state change for > 3 days (INACTIVITY_THRESHOLD_DAYS). */
    STALE_STAGE
}
