package com.unityskill.contribution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.ai.AiProvider;
import com.unityskill.auth.UserRepository;
import com.unityskill.contribution.entity.ContributionEvent;
import com.unityskill.contribution.entity.SourceType;
import com.unityskill.tracking.ResourceType;
import com.unityskill.tracking.TrackingPermissionRepository;
import com.unityskill.workspace.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContributionService {

    private final ContributionRepository contributionRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final SkillEvidenceService skillEvidenceService;
    private final TrackingPermissionRepository trackingPermissionRepository;

    /**
     * Extracts per-contributor skill signals from a PROCESSED meeting transcript.
     * <p>
     * Runs asynchronously (fire-and-forget from TranscriptService.processAsync).
     * Silently swallows all exceptions — contribution extraction must never crash transcript processing.
     *
     * @param transcriptId the UUID of the processed transcript (becomes sourceRefId on ContributionEvent)
     * @param workspaceId  the workspace the meeting belongs to
     * @param rawContent   the raw VTT/text content already stored in the transcript record
     */
    @Async("taskExecutor")
    public void extractFromTranscript(UUID transcriptId, UUID workspaceId, String rawContent) {
        try {
            // 1. Build displayName → userId map for all workspace members
            Map<String, UUID> memberNameToId = buildMemberNameMap(workspaceId);
            if (memberNameToId.isEmpty()) {
                log.debug("No members in workspace {} — skipping contribution extraction", workspaceId);
                return;
            }

            // 2. Call AI to identify speakers and their skill signals
            List<String> memberNames = new ArrayList<>(memberNameToId.keySet());
            String aiResult = aiProvider.generateText(buildPrompt(rawContent, memberNames));

            // 3. Parse AI response into structured contributor signals
            List<ContributorSignal> signals = parseContributorSignals(aiResult);
            if (signals.isEmpty()) {
                log.debug("AI returned no contributor signals for transcript {}", transcriptId);
                return;
            }

            // 4. For each signal: match to workspace member, check incognito, persist
            for (ContributorSignal signal : signals) {
                UUID userId = matchMemberName(signal.memberName(), memberNameToId);
                if (userId == null) {
                    log.debug("No member match for speaker '{}' in transcript {}", signal.memberName(), transcriptId);
                    continue;
                }
                // AC3: skip contribution event if developer has incognito enabled
                boolean incognito = userRepository.findById(userId)
                        .map(u -> u.isIncognito())
                        .orElse(false);
                if (incognito) {
                    log.debug("Skipping incognito user {} in transcript {}", userId, transcriptId);
                    continue;
                }
                ContributionEvent event = ContributionEvent.builder()
                        .workspaceId(workspaceId)
                        .userId(userId)
                        .sourceType(SourceType.MEETING)
                        .sourceRefId(transcriptId)
                        .skillSignals(toJson(signal.skillSignals()))
                        .processedAt(Instant.now())
                        .build();
                contributionRepository.save(event);
                log.info("Created ContributionEvent for user {} from transcript {}", userId, transcriptId);
                skillEvidenceService.generateEvidence(userId, workspaceId);
            }

        } catch (Exception e) {
            // Silent failure — contribution extraction must never crash the transcript pipeline
            log.error("Contribution extraction failed for transcript {}: {}", transcriptId, e.getMessage());
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns a map of displayName → userId for all members of the given workspace.
     * On duplicate display names, the first encountered userId is kept.
     */
    private Map<String, UUID> buildMemberNameMap(UUID workspaceId) {
        List<UUID> memberUserIds = memberRepository.findAllByWorkspaceId(workspaceId)
                .stream()
                .map(m -> m.getUserId())
                .collect(Collectors.toList());
        if (memberUserIds.isEmpty()) return Map.of();
        return userRepository.findAllById(memberUserIds)
                .stream()
                .collect(Collectors.toMap(
                        u -> u.getDisplayName(),
                        u -> u.getId(),
                        (existing, duplicate) -> existing  // keep first on name collision
                ));
    }

    /**
     * Attempts to match a speaker name from the AI response to a workspace member.
     * First tries exact case-insensitive match, then substring containment.
     */
    private UUID matchMemberName(String speakerName, Map<String, UUID> memberNameToId) {
        if (speakerName == null || speakerName.isBlank()) return null;
        // Exact case-insensitive match
        for (Map.Entry<String, UUID> entry : memberNameToId.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(speakerName)) return entry.getValue();
        }
        // Partial match: member name contains speaker or vice versa
        String speakerLower = speakerName.toLowerCase();
        for (Map.Entry<String, UUID> entry : memberNameToId.entrySet()) {
            String memberLower = entry.getKey().toLowerCase();
            if (memberLower.contains(speakerLower) || speakerLower.contains(memberLower)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String buildPrompt(String rawContent, List<String> memberNames) {
        return "Analyze this meeting transcript and identify each speaker's technical contributions.\n\n"
                + "Workspace members (match speakers to these names):\n"
                + String.join(", ", memberNames) + "\n\n"
                + "Transcript:\n"
                + rawContent + "\n\n"
                + "Return ONLY a JSON array in this exact format (no extra text before the label):\n"
                + "CONTRIBUTOR_SIGNALS:\n"
                + "[{\"memberName\":\"<exact name from workspace members list>\",\"skillSignals\":[\"Skill1\",\"Skill2\"]}]\n\n"
                + "Rules:\n"
                + "- Only include members who actively spoke and contributed technical content\n"
                + "- skillSignals should be specific technical skills, languages, or methodologies mentioned\n"
                + "- Match speaker names to the workspace members list (use exact member names)\n"
                + "- Return CONTRIBUTOR_SIGNALS:\\n[] if no contributions can be identified";
    }

    /**
     * Parses the AI response looking for the CONTRIBUTOR_SIGNALS: section
     * followed by a JSON array of {memberName, skillSignals} objects.
     */
    private List<ContributorSignal> parseContributorSignals(String aiResult) {
        if (aiResult == null) return Collections.emptyList();
        int sigStart = aiResult.indexOf("CONTRIBUTOR_SIGNALS:");
        if (sigStart < 0) return Collections.emptyList();
        String after = aiResult.substring(sigStart + 20).strip();
        int arrStart = after.indexOf('[');
        int arrEnd = after.lastIndexOf(']');
        if (arrStart < 0 || arrEnd <= arrStart) return Collections.emptyList();
        String jsonArray = after.substring(arrStart, arrEnd + 1);
        try {
            return objectMapper.readValue(jsonArray, new TypeReference<List<ContributorSignal>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI contributor signals JSON for transcript: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts skill signals from a merged PR via AI analysis of the ticket context.
     * <p>
     * Runs asynchronously — never propagates exceptions to the webhook retry chain (AC3).
     *
     * @param ticketId        UUID of the merged ticket (becomes source_ref_id on ContributionEvent)
     * @param userId          ticket.assigneeId — the developer who worked on the PR
     * @param workspaceId     workspace scope for the ContributionEvent
     * @param prUrl           GitHub PR HTML URL (context for AI prompt)
     * @param ticketTitle     ticket title (primary AI context)
     * @param ticketDescription ticket description (may be null — handled gracefully)
     */
    @Async("taskExecutor")
    public void extractFromPr(UUID ticketId, UUID userId, UUID workspaceId,
                               String prUrl, String ticketTitle, String ticketDescription) {
        try {
            // AC4: skip if developer has incognito enabled (checked before AI call — userId already known)
            boolean incognito = userRepository.findById(userId)
                    .map(u -> u.isIncognito())
                    .orElse(false);
            if (incognito) {
                log.debug("Skipping incognito user {} for PR ticket {}", userId, ticketId);
                return;
            }

            // AC1 (Story 9.2): check GITHUB_REPO tracking permission — default = permitted when no record exists
            String repoName = extractRepoName(prUrl);
            if (repoName != null && trackingPermissionRepository
                    .existsByUserIdAndResourceTypeAndResourceIdAndEnabledFalse(
                            userId, ResourceType.GITHUB_REPO, repoName)) {
                log.debug("Skipping PR contribution for user {} — repo '{}' tracking disabled", userId, repoName);
                return;
            }

            // AC2: call AI to identify skills demonstrated by this PR
            String aiResult = aiProvider.generateText(buildPrPrompt(prUrl, ticketTitle, ticketDescription));

            List<String> skills = parsePrSkills(aiResult);

            ContributionEvent event = ContributionEvent.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .sourceType(SourceType.PR)
                    .sourceRefId(ticketId)        // ticket UUID is the PR reference (prUrl is String, not UUID)
                    .skillSignals(toJson(skills))
                    .processedAt(Instant.now())
                    .build();
            contributionRepository.save(event);
            log.info("Created PR ContributionEvent for user {} from ticket {}", userId, ticketId);
            // AC3 (NFR4): trigger evidence generation immediately after PR contribution saved
            skillEvidenceService.generateEvidence(userId, workspaceId);

        } catch (Exception e) {
            // AC3: silent failure — webhook retry chain must never be affected
            log.error("PR contribution extraction failed for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Extracts skill signals from a batch of chat messages sent by a single developer.
     * <p>
     * Called by {@link ChatContributionScheduler} once per unique sender per workspace, per daily run.
     * Runs asynchronously — silently swallows all exceptions (AC3).
     *
     * @param workspaceId     workspace scope for the ContributionEvent
     * @param userId          senderId — the developer whose messages are being analyzed
     * @param messagesSummary pre-formatted concatenation of message content (joined by "\n---\n")
     */
    @Async("taskExecutor")
    public void extractFromChat(UUID workspaceId, UUID userId, String messagesSummary) {
        try {
            // AC3: skip if developer has incognito enabled
            boolean incognito = userRepository.findById(userId)
                    .map(u -> u.isIncognito())
                    .orElse(false);
            if (incognito) {
                log.debug("Skipping incognito user {} for chat analysis in workspace {}", userId, workspaceId);
                return;
            }

            // AC2 (Story 9.2): channel-level permission check is in ChatContributionScheduler
            // (messages from disabled channels are filtered before this method is called)

            String aiResult = aiProvider.generateText(buildChatPrompt(messagesSummary));
            List<String> skills = parseChatSkills(aiResult);

            ContributionEvent event = ContributionEvent.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .sourceType(SourceType.CHAT)
                    .sourceRefId(null)        // CHAT events have no single UUID reference
                    .skillSignals(toJson(skills))
                    .processedAt(Instant.now())
                    .build();
            contributionRepository.save(event);
            log.info("Created CHAT ContributionEvent for user {} in workspace {}", userId, workspaceId);
            skillEvidenceService.generateEvidence(userId, workspaceId);

        } catch (Exception e) {
            // AC3: silent failure — scheduler must continue processing other users
            log.error("Chat contribution extraction failed for user {} in workspace {}: {}",
                    userId, workspaceId, e.getMessage());
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private String toJson(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items != null ? items : List.of());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String buildPrPrompt(String prUrl, String ticketTitle, String ticketDescription) {
        String descPart = (ticketDescription != null && !ticketDescription.isBlank())
                ? "\nDescription: " + ticketDescription
                : "";
        return "A developer merged a pull request for this task:\n"
                + "Title: " + ticketTitle + "\n"
                + descPart + "\n"
                + "PR URL: " + prUrl + "\n\n"
                + "Identify the technical skills demonstrated by this contribution.\n"
                + "Return ONLY in this exact format:\n"
                + "CONTRIBUTOR_SKILLS:\n"
                + "[\"Skill1\",\"Skill2\",\"Skill3\"]\n\n"
                + "Rules:\n"
                + "- Return specific technical skills, languages, frameworks, or methodologies\n"
                + "- Infer from the task title and description context\n"
                + "- Return CONTRIBUTOR_SKILLS:\\n[] if no skills can be identified";
    }

    private List<String> parsePrSkills(String aiResult) {
        if (aiResult == null) return Collections.emptyList();
        int start = aiResult.indexOf("CONTRIBUTOR_SKILLS:");
        if (start < 0) return Collections.emptyList();
        String after = aiResult.substring(start + 19).strip();
        int arrStart = after.indexOf('[');
        int arrEnd = after.lastIndexOf(']');
        if (arrStart < 0 || arrEnd <= arrStart) return Collections.emptyList();
        String jsonArray = after.substring(arrStart, arrEnd + 1);
        try {
            return objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI PR skill signals JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildChatPrompt(String messagesSummary) {
        return "Analyze these chat messages written by a single developer in a software team:\n\n"
                + messagesSummary + "\n\n"
                + "Identify technical skills, communication patterns, and problem-solving behaviors demonstrated.\n"
                + "Return ONLY in this exact format:\n"
                + "CHAT_CONTRIBUTION_SKILLS:\n"
                + "[\"Skill1\",\"Skill2\",\"Skill3\"]\n\n"
                + "Rules:\n"
                + "- Include technical skills mentioned (languages, frameworks, tools)\n"
                + "- Include soft skills with technical context (e.g., 'Code Review Feedback', 'Problem Solving')\n"
                + "- Return CHAT_CONTRIBUTION_SKILLS:\\n[] if no meaningful contributions can be identified";
    }

    private List<String> parseChatSkills(String aiResult) {
        if (aiResult == null) return Collections.emptyList();
        int start = aiResult.indexOf("CHAT_CONTRIBUTION_SKILLS:");
        if (start < 0) return Collections.emptyList();
        String after = aiResult.substring(start + 25).strip();
        int arrStart = after.indexOf('[');
        int arrEnd = after.lastIndexOf(']');
        if (arrStart < 0 || arrEnd <= arrStart) return Collections.emptyList();
        String jsonArray = after.substring(arrStart, arrEnd + 1);
        try {
            return objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI chat skill signals JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts "owner/repo" from a GitHub PR URL.
     * e.g. "https://github.com/owner/repo/pull/42" → "owner/repo"
     * Returns null if URL is null, blank, or doesn't match expected pattern.
     */
    private String extractRepoName(String prUrl) {
        if (prUrl == null || prUrl.isBlank()) return null;
        try {
            return prUrl.replaceFirst("https://github\\.com/", "")
                        .replaceFirst("/pull/.*", "");
        } catch (Exception e) {
            log.warn("Failed to extract repo name from prUrl: {}", prUrl);
            return null;
        }
    }

    /** Internal DTO matching the AI JSON response structure for meeting transcript analysis. */
    record ContributorSignal(String memberName, List<String> skillSignals) {}
}
