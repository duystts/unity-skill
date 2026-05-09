package com.unityskill.collaboration.entity;

public enum TranscriptStatus {
    UPLOADED,    // file stored, awaiting AI processing
    PROCESSING,  // Story 5.5: AI pipeline running
    PROCESSED,   // Story 5.5: summary + action items populated
    FAILED       // Story 5.5: AI processing failed
}
