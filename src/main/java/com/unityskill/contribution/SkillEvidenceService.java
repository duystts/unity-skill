package com.unityskill.contribution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.ai.AiProvider;
import com.unityskill.common.exception.BadRequestException;
import com.unityskill.common.exception.SkillEvidenceNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.contribution.dto.PublishEvidenceRequest;
import com.unityskill.contribution.dto.ReviewAction;
import com.unityskill.contribution.dto.ReviewEvidenceRequest;
import com.unityskill.contribution.dto.SkillEvidenceResponse;
import com.unityskill.contribution.dto.SkillProfileResponse;
import com.unityskill.contribution.entity.ContributionEvent;
import com.unityskill.contribution.entity.EvidenceStatus;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.portfolio.ContributionStreakRepository;
import com.unityskill.workspace.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillEvidenceService {

    private final ContributionRepository contributionRepository;
    private final SkillEvidenceRepository skillEvidenceRepository;
    private final AiProvider aiProvider;
    private final WebSocketEventPublisher wsPublisher;
    private final ObjectMapper objectMapper;
    private final WorkspaceMemberRepository memberRepository;
    private final ContributionStreakRepository streakRepository; // Story 7.5

    /**
     * Aggregates all ContributionEvents for the given developer and workspace,
     * generates a SkillEvidence via AI, and notifies the developer via WebSocket.
     * <p>
     * Runs asynchronously — never propagates exceptions (AC3: NFR4 met because
     * called immediately after ContributionEvent is saved in extractFromPr).
     *
     * @param userId      the developer whose contributions are being evaluated
     * @param workspaceId workspace scope
     */
    @Async("taskExecutor")
    public void generateEvidence(UUID userId, UUID workspaceId) {
        try {
            // AC1: load all contribution events for this developer in this workspace
            List<ContributionEvent> events =
                    contributionRepository.findAllByWorkspaceIdAndUserId(workspaceId, userId);

            if (events.isEmpty()) {
                log.debug("No contribution events for user {} in workspace {} — skipping evidence", userId, workspaceId);
                return;
            }

            // Aggregate all skill signals from all events
            List<String> allSignals = events.stream()
                    .map(ContributionEvent::getSkillSignals)
                    .filter(s -> s != null && !s.isBlank())
                    .flatMap(s -> parseSignals(s).stream())
                    .distinct()
                    .collect(Collectors.toList());

            if (allSignals.isEmpty()) {
                log.debug("No skill signals found for user {} in workspace {}", userId, workspaceId);
                return;
            }

            // AC1: call AI to generate skill category and summary
            String aiResult = aiProvider.generateText(buildEvidencePrompt(allSignals));
            SkillEvidenceAiResult parsed = parseEvidenceResult(aiResult);

            if (parsed == null) {
                log.warn("Failed to parse AI evidence result for user {} in workspace {}", userId, workspaceId);
                return;
            }

            // AC1: collect source event IDs
            List<String> eventIds = events.stream()
                    .map(e -> e.getId().toString())
                    .collect(Collectors.toList());
            String sourceEventsJson = toJson(eventIds);

            // AC1: create SkillEvidence with status = PENDING
            SkillEvidence evidence = SkillEvidence.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .status(EvidenceStatus.PENDING)
                    .skillCategory(parsed.category())
                    .aiSummary(parsed.summary())
                    .sourceEvents(sourceEventsJson)
                    .build();
            evidence = skillEvidenceRepository.save(evidence);
            log.info("Created SkillEvidence {} for user {} in workspace {}", evidence.getId(), userId, workspaceId);

            // AC2: WebSocket notification to /user/queue/notifications (type=SKILL_EVIDENCE_PENDING)
            wsPublisher.publishNotification(userId, "SKILL_EVIDENCE_PENDING", Map.of(
                    "evidenceId",    evidence.getId().toString(),
                    "skillCategory", evidence.getSkillCategory()
            ));

        } catch (Exception e) {
            // Silent failure — must never block the contribution pipeline
            log.error("Skill evidence generation failed for user {} in workspace {}: {}",
                    userId, workspaceId, e.getMessage());
        }
    }

    // ─── Public synchronous API methods ───────────────────────────────────

    /**
     * Returns all SkillEvidence for the calling developer in the specified workspace.
     * Optionally filters by status (AC1: only the developer's own evidence is returned).
     *
     * @param workspaceId workspace scope
     * @param callerId    the authenticated developer — only their evidence is returned
     * @param status      optional filter; if null, returns all statuses
     */
    public List<SkillEvidenceResponse> getEvidence(UUID workspaceId, UUID callerId, EvidenceStatus status) {
        // AC1: caller must be a workspace member
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)) {
            throw new UnauthorizedAccessException("Not a member of workspace " + workspaceId);
        }
        List<SkillEvidence> evidences = (status != null)
                ? skillEvidenceRepository.findAllByUserIdAndWorkspaceIdAndStatus(callerId, workspaceId, status)
                : skillEvidenceRepository.findAllByUserIdAndWorkspaceId(callerId, workspaceId);
        return evidences.stream()
                .map(SkillEvidenceResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Returns the caller's private skill profile: all APPROVED evidence grouped by
     * skill_category, each group ordered by reviewed_at descending.
     * AC2: always returns caller's OWN data — 403 if not a workspace member.
     */
    public SkillProfileResponse getSkillProfile(UUID workspaceId, UUID callerId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)) {
            throw new UnauthorizedAccessException("Not a member of workspace " + workspaceId);
        }
        List<SkillEvidence> approved = skillEvidenceRepository
                .findAllByUserIdAndWorkspaceIdAndStatus(callerId, workspaceId, EvidenceStatus.APPROVED);

        // Sort by reviewedAt descending before grouping so LinkedHashMap preserves
        // insertion order — first category = most recently reviewed
        Map<String, List<SkillEvidence>> grouped = approved.stream()
                .sorted(Comparator.comparing(SkillEvidence::getReviewedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.groupingBy(
                        SkillEvidence::getSkillCategory,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<SkillProfileResponse.SkillCategoryGroup> categories = grouped.entrySet().stream()
                .map(entry -> new SkillProfileResponse.SkillCategoryGroup(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .map(SkillEvidenceResponse::from)
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        // Story 7.5: include per-workspace streak in private profile
        SkillProfileResponse.StreakInfo streak = streakRepository
                .findByUserIdAndWorkspaceId(callerId, workspaceId)
                .map(s -> new SkillProfileResponse.StreakInfo(
                        s.getCurrentStreakWeeks(),
                        s.getLongestStreakWeeks()))
                .orElse(null);

        return new SkillProfileResponse(approved.size(), categories, streak);
    }

    /**
     * Publishes or unpublishes a SkillEvidence for the developer's public portfolio.
     * AC1: sets is_published=true only if status=APPROVED.
     * AC2: sets is_published=false unconditionally (always allowed).
     * AC3: 400 if trying to publish non-APPROVED evidence.
     */
    public SkillEvidenceResponse setPublished(UUID evidenceId, UUID callerId, boolean isPublished) {
        SkillEvidence evidence = skillEvidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new SkillEvidenceNotFoundException(evidenceId));

        if (!evidence.getUserId().equals(callerId)) {
            throw new UnauthorizedAccessException("Cannot modify evidence belonging to another user");
        }

        if (isPublished && evidence.getStatus() != EvidenceStatus.APPROVED) {
            throw new BadRequestException("Only approved evidence can be published");
        }

        evidence.setPublished(isPublished);
        evidence.setPublishedAt(isPublished ? Instant.now() : null);

        return SkillEvidenceResponse.from(skillEvidenceRepository.save(evidence));
    }

    /**
     * Approves, rejects, or edits (approve + add notes) a SkillEvidence record.
     * Throws 403 if the evidence belongs to another developer (AC5).
     *
     * @param evidenceId  the evidence to review
     * @param workspaceId workspace scope — evidence must belong to this workspace
     * @param callerId    must equal evidence.userId, otherwise 403
     * @param request     action + optional developerNotes
     */
    public SkillEvidenceResponse reviewEvidence(UUID evidenceId, UUID workspaceId, UUID callerId,
                                                 ReviewEvidenceRequest request) {
        SkillEvidence evidence = skillEvidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new SkillEvidenceNotFoundException(evidenceId));

        // AC5: ownership check — 403 if caller does not own the evidence
        if (!evidence.getUserId().equals(callerId)) {
            throw new UnauthorizedAccessException("Cannot review evidence belonging to another user");
        }
        // Workspace scope validation — treat as 404 to avoid information leakage
        if (!evidence.getWorkspaceId().equals(workspaceId)) {
            throw new SkillEvidenceNotFoundException(evidenceId);
        }
        // AC4: developerNotes required for EDIT
        if (request.action() == ReviewAction.EDIT &&
                (request.developerNotes() == null || request.developerNotes().isBlank())) {
            throw new BadRequestException("developerNotes is required for EDIT action");
        }

        switch (request.action()) {
            case APPROVE -> {
                evidence.setStatus(EvidenceStatus.APPROVED);
                evidence.setReviewedAt(Instant.now());
            }
            case REJECT -> {
                evidence.setStatus(EvidenceStatus.REJECTED);
                evidence.setReviewedAt(Instant.now());
            }
            case EDIT -> {
                evidence.setDeveloperNotes(request.developerNotes());
                evidence.setStatus(EvidenceStatus.APPROVED);
                evidence.setReviewedAt(Instant.now());
            }
        }

        return SkillEvidenceResponse.from(skillEvidenceRepository.save(evidence));
    }

    // ─── Private helpers ───────────────────────────────────────────────────

    private String buildEvidencePrompt(List<String> signals) {
        return "A developer has demonstrated these technical skills and contributions:\n"
                + String.join(", ", signals) + "\n\n"
                + "Based on these contributions, generate:\n"
                + "1. A skill category (choose the most specific from: Backend Development, "
                + "Frontend Development, Database Design, DevOps, API Design, Problem Solving, "
                + "Code Review, Architecture, Testing, Mobile Development, Security)\n"
                + "2. A concise 2-3 sentence professional summary of what this developer demonstrated\n\n"
                + "Return ONLY in this exact format:\n"
                + "SKILL_EVIDENCE:\n"
                + "{\"category\":\"Backend Development\",\"summary\":\"The developer demonstrated...\"}\n\n"
                + "Rules:\n"
                + "- category must be exactly one from the list above\n"
                + "- summary must be 2-3 sentences, professional tone, third person\n"
                + "- Return SKILL_EVIDENCE:\\n{} if skills cannot be categorized";
    }

    private SkillEvidenceAiResult parseEvidenceResult(String aiResult) {
        if (aiResult == null) return null;
        int start = aiResult.indexOf("SKILL_EVIDENCE:");
        if (start < 0) return null;
        String after = aiResult.substring(start + 15).strip();
        int objStart = after.indexOf('{');
        int objEnd = after.lastIndexOf('}');
        if (objStart < 0 || objEnd <= objStart) return null;
        String json = after.substring(objStart, objEnd + 1);
        try {
            var node = objectMapper.readTree(json);
            String category = node.path("category").asText(null);
            String summary  = node.path("summary").asText(null);
            if (category == null || category.isBlank() || summary == null || summary.isBlank()) return null;
            return new SkillEvidenceAiResult(category, summary);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI evidence JSON: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSignals(String skillSignalsJson) {
        try {
            return objectMapper.readValue(skillSignalsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String toJson(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items != null ? items : List.of());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /** Internal DTO for AI response parsing. */
    record SkillEvidenceAiResult(String category, String summary) {}
}
