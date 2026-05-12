package com.unityskill.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.contribution.ContributionService;
import com.unityskill.project.ProjectRepository;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.TriggerService;
import com.unityskill.project.entity.TriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GithubEventService {

    /** Legacy: branch name contains TICKET-{uuid} */
    private static final Pattern TICKET_ID_PATTERN =
        Pattern.compile("TICKET-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
            Pattern.CASE_INSENSITIVE);

    /** New: PR title contains [PREFIX-N] e.g. "[US-3] Fix login bug" */
    private static final Pattern PR_TITLE_CODE_PATTERN =
        Pattern.compile("\\[([A-Za-z]{1,10})-(\\d+)\\]");

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final TriggerService triggerService;
    private final ObjectMapper objectMapper;
    private final ContributionService contributionService;

    public void handleEvent(String eventType, String payloadJson, UUID workspaceId, UUID projectId) {
        try {
            JsonNode json = objectMapper.readTree(payloadJson);
            String action = json.path("action").asText();

            if ("pull_request".equals(eventType)) {
                String branchName = json.path("pull_request").path("head").path("ref").asText("");
                String prTitle   = json.path("pull_request").path("title").asText("");
                String prUrl     = json.path("pull_request").path("html_url").asText("");
                boolean merged   = json.path("pull_request").path("merged").asBoolean(false);

                // 1st try: legacy branch-name pattern  TICKET-{uuid}
                Optional<UUID> ticketId = extractTicketId(branchName);
                // 2nd try: PR title pattern  [PREFIX-N]
                if (ticketId.isEmpty()) {
                    ticketId = extractTicketIdFromTitle(prTitle, projectId);
                }
                if (ticketId.isEmpty()) return; // no recognisable ticket reference — discard

                if ("opened".equals(action)) {
                    handlePrOpened(ticketId.get(), prUrl, projectId);
                } else if ("closed".equals(action) && merged) {
                    handlePrMerged(ticketId.get(), projectId);
                }

            } else if ("pull_request_review".equals(eventType) && "submitted".equals(action)) {
                String branchName = json.path("pull_request").path("head").path("ref").asText("");
                String prTitle    = json.path("pull_request").path("title").asText("");
                Optional<UUID> ticketId = extractTicketId(branchName);
                if (ticketId.isEmpty()) {
                    ticketId = extractTicketIdFromTitle(prTitle, projectId);
                }
                if (ticketId.isEmpty()) return;
                handlePrReviewed(ticketId.get(), projectId);
            }
            // Unknown event types: no-op (silently discard)

        } catch (Exception e) {
            // AC5: malformed payload — log and discard silently
        }
    }

    @Transactional
    void handlePrOpened(UUID ticketId, String prUrl, UUID projectId) {
        ticketRepository.findById(ticketId)
            .filter(t -> t.getProjectId().equals(projectId)) // AC5: cross-project safety
            .ifPresent(ticket -> {
                ticket.setGithubPrUrl(prUrl);
                ticketRepository.save(ticket);
                triggerService.evaluate(TriggerType.PR_OPENED, ticketId);
            });
    }

    void handlePrReviewed(UUID ticketId, UUID projectId) {
        ticketRepository.findById(ticketId)
            .filter(t -> t.getProjectId().equals(projectId))
            .ifPresent(ticket -> triggerService.evaluate(TriggerType.PR_REVIEWED, ticketId));
    }

    void handlePrMerged(UUID ticketId, UUID projectId) {
        ticketRepository.findById(ticketId)
            .filter(t -> t.getProjectId().equals(projectId))
            .ifPresent(ticket -> {
                triggerService.evaluate(TriggerType.PR_MERGED, ticketId);
                // AC1/AC5: only trigger contribution analysis if ticket has an assigned developer and PR URL
                if (ticket.getAssigneeId() != null && ticket.getGithubPrUrl() != null) {
                    contributionService.extractFromPr(
                            ticket.getId(),
                            ticket.getAssigneeId(),
                            ticket.getWorkspaceId(),
                            ticket.getGithubPrUrl(),
                            ticket.getTitle(),
                            ticket.getDescription()
                    );
                }
            });
    }

    Optional<UUID> extractTicketId(String branchName) {
        if (branchName == null || branchName.isBlank()) return Optional.empty();
        Matcher m = TICKET_ID_PATTERN.matcher(branchName);
        if (m.find()) {
            try {
                return Optional.of(UUID.fromString(m.group(1)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Extracts a ticket UUID from a PR title that follows the convention:
     *   [PREFIX-N] description   e.g. "[US-3] Fix login redirect bug"
     *
     * Looks up the project's keyPrefix, verifies it matches, then finds the ticket
     * by (projectId, ticketNumber).
     */
    Optional<UUID> extractTicketIdFromTitle(String prTitle, UUID projectId) {
        if (prTitle == null || prTitle.isBlank()) return Optional.empty();
        Matcher m = PR_TITLE_CODE_PATTERN.matcher(prTitle);
        if (!m.find()) return Optional.empty();

        String parsedPrefix = m.group(1).toUpperCase();
        int ticketNumber;
        try {
            ticketNumber = Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        return projectRepository.findById(projectId)
            .filter(p -> parsedPrefix.equalsIgnoreCase(p.getKeyPrefix()))
            .flatMap(p -> ticketRepository.findByProjectIdAndTicketNumber(projectId, ticketNumber))
            .map(t -> t.getId());
    }
}
