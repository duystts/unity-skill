package com.unityskill.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.contribution.ContributionService;
import com.unityskill.project.ProjectRepository;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.TriggerService;
import com.unityskill.project.entity.TriggerType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GithubEventService {

    private static final Logger log = LoggerFactory.getLogger(GithubEventService.class);

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

            log.info("GitHub event: type='{}', action='{}', projectId='{}'", eventType, action, projectId);

            if ("pull_request".equals(eventType)) {
                String branchName = json.path("pull_request").path("head").path("ref").asText("");
                String prTitle   = json.path("pull_request").path("title").asText("");
                String prUrl     = json.path("pull_request").path("html_url").asText("");
                boolean merged   = json.path("pull_request").path("merged").asBoolean(false);

                log.info("PR event: action='{}', title='{}', branch='{}', merged={}", action, prTitle, branchName, merged);

                // 1st try: legacy branch-name pattern  TICKET-{uuid}
                Optional<UUID> ticketId = extractTicketId(branchName);
                // 2nd try: PR title pattern  [PREFIX-N]
                if (ticketId.isEmpty()) {
                    ticketId = extractTicketIdFromTitle(prTitle, projectId);
                }
                if (ticketId.isEmpty()) {
                    log.warn("No ticket found for PR title='{}', branch='{}', projectId='{}'", prTitle, branchName, projectId);
                    return;
                }

                log.info("Matched ticketId='{}', action='{}'", ticketId.get(), action);

                if ("opened".equals(action) || "reopened".equals(action)) {
                    handlePrOpened(ticketId.get(), prUrl, projectId);
                } else if ("closed".equals(action) && merged) {
                    handlePrMerged(ticketId.get(), projectId);
                } else if ("closed".equals(action) && !merged) {
                    handlePrClosed(ticketId.get(), projectId);
                }

            } else if ("pull_request_review".equals(eventType) && "submitted".equals(action)) {
                String branchName = json.path("pull_request").path("head").path("ref").asText("");
                String prTitle    = json.path("pull_request").path("title").asText("");
                Optional<UUID> ticketId = extractTicketId(branchName);
                if (ticketId.isEmpty()) {
                    ticketId = extractTicketIdFromTitle(prTitle, projectId);
                }
                if (ticketId.isEmpty()) {
                    log.warn("No ticket found for review PR title='{}', branch='{}'", prTitle, branchName);
                    return;
                }
                handlePrReviewed(ticketId.get(), projectId);
            }

        } catch (Exception e) {
            log.error("Error processing GitHub event type='{}': {}", eventType, e.getMessage(), e);
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

    void handlePrClosed(UUID ticketId, UUID projectId) {
        ticketRepository.findById(ticketId)
            .filter(t -> t.getProjectId().equals(projectId))
            .ifPresent(ticket -> triggerService.evaluate(TriggerType.PR_CLOSED, ticketId));
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
            .filter(p -> {
                boolean match = parsedPrefix.equalsIgnoreCase(p.getKeyPrefix());
                log.info("Prefix check: parsedPrefix='{}', projectKeyPrefix='{}', match={}", parsedPrefix, p.getKeyPrefix(), match);
                return match;
            })
            .flatMap(p -> {
                var ticket = ticketRepository.findByProjectIdAndTicketNumber(projectId, ticketNumber);
                log.info("Ticket lookup: projectId='{}', ticketNumber={}, found={}", projectId, ticketNumber, ticket.isPresent());
                return ticket;
            })
            .map(t -> t.getId());
    }
}
