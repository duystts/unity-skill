package com.unityskill.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.contribution.ContributionService;
import com.unityskill.project.ProjectRepository;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.TriggerService;
import com.unityskill.project.entity.Ticket;
import com.unityskill.project.entity.TriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubEventServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TriggerService triggerService;
    @Mock ContributionService contributionService;

    GithubEventService githubEventService;

    @BeforeEach
    void setUp() {
        githubEventService = new GithubEventService(ticketRepository, projectRepository, triggerService, new ObjectMapper(), contributionService);
    }

    // --- extractTicketId ---

    @Test
    void extractTicketId_validBranch_returnsUuid() {
        UUID expected = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        Optional<UUID> result = githubEventService.extractTicketId("feature/TICKET-550e8400-e29b-41d4-a716-446655440000");
        assertThat(result).contains(expected);
    }

    @Test
    void extractTicketId_caseInsensitive_returnsUuid() {
        UUID expected = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        Optional<UUID> result = githubEventService.extractTicketId("feature/ticket-550e8400-e29b-41d4-a716-446655440000");
        assertThat(result).contains(expected);
    }

    @Test
    void extractTicketId_noBranchTicketId_returnsEmpty() {
        assertThat(githubEventService.extractTicketId("feature/my-feature")).isEmpty();
    }

    @Test
    void extractTicketId_emptyBranch_returnsEmpty() {
        assertThat(githubEventService.extractTicketId("")).isEmpty();
    }

    // --- handlePrOpened ---

    @Test
    void handlePrOpened_ticketInProject_setsUrlAndEvaluates() {
        UUID ticketId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().id(ticketId).projectId(projectId).workspaceId(UUID.randomUUID()).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handlePrOpened(ticketId, "https://github.com/owner/repo/pull/1", projectId);

        assertThat(ticket.getGithubPrUrl()).isEqualTo("https://github.com/owner/repo/pull/1");
        verify(ticketRepository).save(ticket);
        verify(triggerService).evaluate(TriggerType.PR_OPENED, ticketId);
    }

    @Test
    void handlePrOpened_ticketInDifferentProject_silentlyDiscards() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().id(ticketId).projectId(UUID.randomUUID()).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handlePrOpened(ticketId, "https://...", UUID.randomUUID());

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(triggerService);
    }

    @Test
    void handlePrOpened_ticketNotFound_silentlyDiscards() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        githubEventService.handlePrOpened(ticketId, "https://...", UUID.randomUUID());

        verifyNoInteractions(triggerService);
    }

    // --- handlePrReviewed / handlePrMerged ---

    @Test
    void handlePrReviewed_ticketInProject_evaluates() {
        UUID ticketId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().id(ticketId).projectId(projectId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handlePrReviewed(ticketId, projectId);

        verify(triggerService).evaluate(TriggerType.PR_REVIEWED, ticketId);
    }

    @Test
    void handlePrMerged_ticketWithAssigneeAndPrUrl_evaluatesAndTriggersContribution() {
        UUID ticketId   = UUID.randomUUID();
        UUID projectId  = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId).projectId(projectId).workspaceId(workspaceId)
                .assigneeId(assigneeId).githubPrUrl("https://github.com/owner/repo/pull/1")
                .title("Fix login bug").description("JWT token was not validated").build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handlePrMerged(ticketId, projectId);

        verify(triggerService).evaluate(TriggerType.PR_MERGED, ticketId);
        verify(contributionService).extractFromPr(
                eq(ticketId), eq(assigneeId), eq(workspaceId),
                eq("https://github.com/owner/repo/pull/1"),
                eq("Fix login bug"), eq("JWT token was not validated"));
    }

    @Test
    void handlePrMerged_noAssignee_skipsContributionAnalysis() {
        UUID ticketId  = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        // AC5: ticket has no assignee — contribution analysis must be skipped
        Ticket ticket = Ticket.builder()
                .id(ticketId).projectId(projectId)
                .assigneeId(null)
                .githubPrUrl("https://github.com/owner/repo/pull/2")
                .title("Refactor auth").build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handlePrMerged(ticketId, projectId);

        verify(triggerService).evaluate(TriggerType.PR_MERGED, ticketId);
        verifyNoInteractions(contributionService);
    }

    @Test
    void handlePrMerged_noPrUrl_skipsContributionAnalysis() {
        UUID ticketId   = UUID.randomUUID();
        UUID projectId  = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        // Ticket has assignee but prUrl not yet stored (edge case)
        Ticket ticket = Ticket.builder()
                .id(ticketId).projectId(projectId)
                .assigneeId(assigneeId).githubPrUrl(null)
                .title("Task").build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handlePrMerged(ticketId, projectId);

        verify(triggerService).evaluate(TriggerType.PR_MERGED, ticketId);
        verifyNoInteractions(contributionService);
    }

    // --- handleEvent dispatch ---

    @Test
    void handleEvent_prOpened_dispatchesToHandlePrOpened() {
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String payload = """
            {
              "action": "opened",
              "pull_request": {
                "html_url": "https://github.com/owner/repo/pull/1",
                "head": {"ref": "feature/TICKET-550e8400-e29b-41d4-a716-446655440000"},
                "merged": false
              }
            }
            """;
        Ticket ticket = Ticket.builder().id(ticketId).projectId(projectId).workspaceId(UUID.randomUUID()).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handleEvent("pull_request", payload, UUID.randomUUID(), projectId);

        verify(triggerService).evaluate(TriggerType.PR_OPENED, ticketId);
        assertThat(ticket.getGithubPrUrl()).isEqualTo("https://github.com/owner/repo/pull/1");
    }

    @Test
    void handleEvent_prMerged_dispatchesToHandlePrMerged() {
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String payload = """
            {
              "action": "closed",
              "pull_request": {
                "html_url": "https://github.com/owner/repo/pull/1",
                "head": {"ref": "feature/TICKET-550e8400-e29b-41d4-a716-446655440000"},
                "merged": true
              }
            }
            """;
        Ticket ticket = Ticket.builder().id(ticketId).projectId(projectId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handleEvent("pull_request", payload, UUID.randomUUID(), projectId);

        verify(triggerService).evaluate(TriggerType.PR_MERGED, ticketId);
    }

    @Test
    void handleEvent_prClosed_notMerged_doesNotEvaluate() {
        String payload = """
            {
              "action": "closed",
              "pull_request": {
                "html_url": "https://...",
                "head": {"ref": "feature/TICKET-550e8400-e29b-41d4-a716-446655440000"},
                "merged": false
              }
            }
            """;

        githubEventService.handleEvent("pull_request", payload, UUID.randomUUID(), UUID.randomUUID());

        // A closed-but-not-merged PR queries the ticket repo but must never trigger rule evaluation
        verifyNoInteractions(triggerService);
    }

    @Test
    void handleEvent_prReviewSubmitted_dispatchesToHandlePrReviewed() {
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String payload = """
            {
              "action": "submitted",
              "review": {"state": "approved"},
              "pull_request": {
                "head": {"ref": "feature/TICKET-550e8400-e29b-41d4-a716-446655440000"}
              }
            }
            """;
        Ticket ticket = Ticket.builder().id(ticketId).projectId(projectId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        githubEventService.handleEvent("pull_request_review", payload, UUID.randomUUID(), projectId);

        verify(triggerService).evaluate(TriggerType.PR_REVIEWED, ticketId);
    }

    @Test
    void handleEvent_noBranchTicketId_silentlyDiscards() {
        String payload = """
            {
              "action": "opened",
              "pull_request": {
                "html_url": "https://...",
                "head": {"ref": "feature/no-ticket-here"},
                "merged": false
              }
            }
            """;

        githubEventService.handleEvent("pull_request", payload, UUID.randomUUID(), UUID.randomUUID());

        verifyNoInteractions(ticketRepository, triggerService);
    }

    @Test
    void handleEvent_malformedJson_silentlyDiscards() {
        githubEventService.handleEvent("pull_request", "not-json-at-all", UUID.randomUUID(), UUID.randomUUID());

        verifyNoInteractions(ticketRepository, triggerService);
    }
}
