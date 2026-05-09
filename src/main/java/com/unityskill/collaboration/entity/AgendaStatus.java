package com.unityskill.collaboration.entity;

public enum AgendaStatus {
    GENERATING,  // AgendaService is in progress (Story 5.3)
    READY,       // AI agenda generation complete (Story 5.3)
    FAILED       // AI API unavailable (Story 5.3)
}
