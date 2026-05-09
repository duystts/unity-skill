package com.unityskill.common.exception;

import java.util.UUID;

public class SkillEvidenceNotFoundException extends RuntimeException {
    public SkillEvidenceNotFoundException(UUID evidenceId) {
        super("Skill evidence not found: " + evidenceId);
    }
}
