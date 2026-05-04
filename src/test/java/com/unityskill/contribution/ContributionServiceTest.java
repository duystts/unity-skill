package com.unityskill.contribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.ai.AiProvider;
import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.contribution.entity.ContributionEvent;
import com.unityskill.contribution.entity.SourceType;
import com.unityskill.tracking.ResourceType;
import com.unityskill.tracking.TrackingPermissionRepository;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import com.unityskill.contribution.SkillEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContributionServiceTest {

    @Mock ContributionRepository contributionRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @Mock AiProvider aiProvider;
    @Mock SkillEvidenceService skillEvidenceService;
    @Mock TrackingPermissionRepository trackingPermissionRepository;
    @Spy  ObjectMapper objectMapper;   // real Jackson for JSON parsing
    @InjectMocks ContributionService contributionService;

    UUID workspaceId;
    UUID transcriptId;
    UUID aliceId;
    UUID bobId;

    @BeforeEach
    void setUp() {
        workspaceId  = UUID.randomUUID();
        transcriptId = UUID.randomUUID();
        aliceId      = UUID.randomUUID();
        bobId        = UUID.randomUUID();
    }

    private WorkspaceMember member(UUID userId) {
        return WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(userId)
                .role(WorkspaceRole.DEVELOPER)
                .build();
    }

    private User user(UUID id, String displayName, boolean incognito) {
        return User.builder()
                .id(id)
                .email(displayName.replace(" ", ".").toLowerCase() + "@test.com")
                .displayName(displayName)
                .isIncognito(incognito)
                .build();
    }

    // ── extractFromTranscript ─────────────────────────────────────────────────

    @Test
    void extractFromTranscript_matchedMember_createsContributionEvent() {
        // Arrange
        when(memberRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(member(aliceId)));
        when(userRepository.findAllById(List.of(aliceId)))
                .thenReturn(List.of(user(aliceId, "Alice Johnson", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SIGNALS:\n" +
                "[{\"memberName\":\"Alice Johnson\",\"skillSignals\":[\"Java\",\"Spring Boot\"]}]");
        when(userRepository.findById(aliceId))
                .thenReturn(Optional.of(user(aliceId, "Alice Johnson", false)));
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        contributionService.extractFromTranscript(
                transcriptId, workspaceId,
                "WEBVTT\n<v Alice Johnson>Let's use Spring Boot for this.\n");

        // Assert
        ArgumentCaptor<ContributionEvent> captor = ArgumentCaptor.forClass(ContributionEvent.class);
        verify(contributionRepository).save(captor.capture());
        ContributionEvent saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(aliceId);
        assertThat(saved.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(saved.getSourceType()).isEqualTo(SourceType.MEETING);
        assertThat(saved.getSourceRefId()).isEqualTo(transcriptId);
        assertThat(saved.getSkillSignals()).contains("Java");
        assertThat(saved.getSkillSignals()).contains("Spring Boot");
        assertThat(saved.getProcessedAt()).isNotNull();
        verify(skillEvidenceService).generateEvidence(eq(aliceId), eq(workspaceId));
    }

    @Test
    void extractFromTranscript_incognitoUser_skipsEventCreation() {
        // AC3: no ContributionEvent when developer has incognito=true
        when(memberRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(member(bobId)));
        when(userRepository.findAllById(List.of(bobId)))
                .thenReturn(List.of(user(bobId, "Bob Smith", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SIGNALS:\n[{\"memberName\":\"Bob Smith\",\"skillSignals\":[\"SQL\"]}]");
        when(userRepository.findById(bobId))
                .thenReturn(Optional.of(user(bobId, "Bob Smith", true)));  // incognito=true

        contributionService.extractFromTranscript(transcriptId, workspaceId, "WEBVTT content");

        verifyNoInteractions(contributionRepository);  // AC3: no event created
        verifyNoInteractions(skillEvidenceService);
    }

    @Test
    void extractFromTranscript_aiThrows_swallowsException() {
        // Silent failure — must never propagate exceptions to caller
        when(memberRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(member(aliceId)));
        when(userRepository.findAllById(List.of(aliceId)))
                .thenReturn(List.of(user(aliceId, "Alice", false)));
        when(aiProvider.generateText(anyString()))
                .thenThrow(new RuntimeException("AI timeout"));

        // Must NOT throw
        contributionService.extractFromTranscript(transcriptId, workspaceId, "content");

        verifyNoInteractions(contributionRepository);
        verifyNoInteractions(skillEvidenceService);
    }

    @Test
    void extractFromTranscript_noMembersInWorkspace_createsNoEventsAndSkipsAi() {
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        contributionService.extractFromTranscript(transcriptId, workspaceId, "content");

        verifyNoInteractions(aiProvider);
        verifyNoInteractions(contributionRepository);
    }

    @Test
    void extractFromTranscript_speakerNotInMemberList_createsNoEvent() {
        // AI returns a name that doesn't match any workspace member
        when(memberRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(member(aliceId)));
        when(userRepository.findAllById(List.of(aliceId)))
                .thenReturn(List.of(user(aliceId, "Alice Johnson", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SIGNALS:\n[{\"memberName\":\"Unknown Person\",\"skillSignals\":[\"Java\"]}]");

        contributionService.extractFromTranscript(transcriptId, workspaceId, "content");

        verifyNoInteractions(contributionRepository);  // no name match → no event
    }

    @Test
    void extractFromTranscript_malformedAiJson_swallowsAndCreatesNoEvents() {
        when(memberRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(member(aliceId)));
        when(userRepository.findAllById(List.of(aliceId)))
                .thenReturn(List.of(user(aliceId, "Alice", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SIGNALS:\nnot valid json {{ bad");

        contributionService.extractFromTranscript(transcriptId, workspaceId, "content");

        verifyNoInteractions(contributionRepository);  // parse failure → no events
    }

    @Test
    void extractFromTranscript_aiReturnsEmptyArray_createsNoEvents() {
        when(memberRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(member(aliceId)));
        when(userRepository.findAllById(List.of(aliceId)))
                .thenReturn(List.of(user(aliceId, "Alice", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SIGNALS:\n[]");

        contributionService.extractFromTranscript(transcriptId, workspaceId, "content");

        verifyNoInteractions(contributionRepository);
    }

    @Test
    void extractFromTranscript_multipleMembers_createsEventForEachMatchedNonIncognito() {
        when(memberRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(member(aliceId), member(bobId)));
        when(userRepository.findAllById(anyList()))
                .thenReturn(List.of(
                        user(aliceId, "Alice Johnson", false),
                        user(bobId, "Bob Smith", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SIGNALS:\n" +
                "[{\"memberName\":\"Alice Johnson\",\"skillSignals\":[\"Java\"]}," +
                "{\"memberName\":\"Bob Smith\",\"skillSignals\":[\"SQL\",\"PostgreSQL\"]}]");
        when(userRepository.findById(aliceId))
                .thenReturn(Optional.of(user(aliceId, "Alice Johnson", false)));
        when(userRepository.findById(bobId))
                .thenReturn(Optional.of(user(bobId, "Bob Smith", false)));
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contributionService.extractFromTranscript(transcriptId, workspaceId, "content");

        verify(contributionRepository, times(2)).save(any(ContributionEvent.class));
    }

    @Test
    void extractFromTranscript_partialNameMatch_resolvesCorrectUser() {
        // AI returns "Alice" but member's display name is "Alice Johnson"
        when(memberRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(member(aliceId)));
        when(userRepository.findAllById(List.of(aliceId)))
                .thenReturn(List.of(user(aliceId, "Alice Johnson", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SIGNALS:\n[{\"memberName\":\"Alice\",\"skillSignals\":[\"Java\"]}]");
        when(userRepository.findById(aliceId))
                .thenReturn(Optional.of(user(aliceId, "Alice Johnson", false)));
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contributionService.extractFromTranscript(transcriptId, workspaceId, "content");

        // Partial match ("alice johnson".contains("alice")) should match
        ArgumentCaptor<ContributionEvent> captor = ArgumentCaptor.forClass(ContributionEvent.class);
        verify(contributionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(aliceId);
    }

    // ── extractFromPr ─────────────────────────────────────────────────────────

    @Test
    void extractFromPr_assignedNonIncognito_createsContributionEvent() {
        // AC2: happy path — non-incognito developer gets a PR ContributionEvent
        UUID ticketId    = UUID.randomUUID();
        UUID assigneeId  = aliceId;
        String prUrl     = "https://github.com/owner/repo/pull/42";
        String title     = "Fix login bug";
        String desc      = "JWT token was not validated";

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(user(assigneeId, "Alice Johnson", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SKILLS:\n[\"Java\",\"Spring Security\",\"JWT\"]");
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contributionService.extractFromPr(ticketId, assigneeId, workspaceId, prUrl, title, desc);

        ArgumentCaptor<ContributionEvent> captor = ArgumentCaptor.forClass(ContributionEvent.class);
        verify(contributionRepository).save(captor.capture());
        ContributionEvent saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(assigneeId);
        assertThat(saved.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(saved.getSourceType()).isEqualTo(SourceType.PR);
        assertThat(saved.getSourceRefId()).isEqualTo(ticketId);
        assertThat(saved.getSkillSignals()).contains("Java");
        assertThat(saved.getSkillSignals()).contains("Spring Security");
        assertThat(saved.getProcessedAt()).isNotNull();
        verify(skillEvidenceService).generateEvidence(eq(assigneeId), eq(workspaceId));
    }

    @Test
    void extractFromPr_incognitoUser_skipsEventCreation() {
        // AC4: no ContributionEvent when developer has incognito=true
        UUID ticketId   = UUID.randomUUID();
        UUID assigneeId = bobId;

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(user(assigneeId, "Bob Smith", true)));  // incognito=true

        contributionService.extractFromPr(ticketId, assigneeId, workspaceId,
                "https://github.com/owner/repo/pull/5", "Some task", null);

        verifyNoInteractions(aiProvider);    // AI must not be called
        verifyNoInteractions(contributionRepository); // AC4: no event created
        verifyNoInteractions(skillEvidenceService);
    }

    @Test
    void extractFromPr_aiThrows_swallowsException() {
        // AC3: silent failure — must never propagate exceptions to webhook retry chain
        UUID ticketId   = UUID.randomUUID();
        UUID assigneeId = aliceId;

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(user(assigneeId, "Alice Johnson", false)));
        when(aiProvider.generateText(anyString()))
                .thenThrow(new RuntimeException("AI service unavailable"));

        // Must NOT throw
        contributionService.extractFromPr(ticketId, assigneeId, workspaceId,
                "https://github.com/owner/repo/pull/7", "Task title", "Description");

        verifyNoInteractions(contributionRepository);
        verifyNoInteractions(skillEvidenceService);
    }

    @Test
    void extractFromPr_aiReturnsEmptySkills_savesEventWithEmptySignals() {
        // Edge case: AI identifies no skills — event still saved with empty skill array
        UUID ticketId   = UUID.randomUUID();
        UUID assigneeId = aliceId;

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(user(assigneeId, "Alice Johnson", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SKILLS:\n[]");
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contributionService.extractFromPr(ticketId, assigneeId, workspaceId,
                "https://github.com/owner/repo/pull/9", "Minor fix", "Small tweak");

        ArgumentCaptor<ContributionEvent> captor = ArgumentCaptor.forClass(ContributionEvent.class);
        verify(contributionRepository).save(captor.capture());
        assertThat(captor.getValue().getSkillSignals()).isEqualTo("[]");
    }

    @Test
    void extractFromPr_nullDescription_buildsPrPromptWithoutDescription() {
        // Edge case: null ticketDescription must not cause NPE or malformed prompt
        UUID ticketId   = UUID.randomUUID();
        UUID assigneeId = aliceId;

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(user(assigneeId, "Alice Johnson", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CONTRIBUTOR_SKILLS:\n[\"React\"]");
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Must not throw even with null description
        contributionService.extractFromPr(ticketId, assigneeId, workspaceId,
                "https://github.com/owner/repo/pull/11", "Frontend task", null);

        verify(contributionRepository).save(any(ContributionEvent.class));
        // Verify AI was called (prompt was built without NPE)
        verify(aiProvider).generateText(anyString());
    }

    @Test
    void extractFromPr_repoTrackingDisabled_skipsEventCreation() {
        // AC1 (Story 9.2): no ContributionEvent when user has disabled tracking for the repo
        UUID ticketId   = UUID.randomUUID();
        UUID assigneeId = aliceId;
        String prUrl    = "https://github.com/owner/repo/pull/42";

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(user(assigneeId, "Alice Johnson", false)));
        when(trackingPermissionRepository
                .existsByUserIdAndResourceTypeAndResourceIdAndEnabledFalse(
                        assigneeId, ResourceType.GITHUB_REPO, "owner/repo"))
                .thenReturn(true);  // tracking disabled for this repo

        contributionService.extractFromPr(ticketId, assigneeId, workspaceId,
                prUrl, "Fix login bug", "Some description");

        verifyNoInteractions(aiProvider);             // no AI call when tracking disabled
        verifyNoInteractions(contributionRepository); // no event created
        verifyNoInteractions(skillEvidenceService);
    }

    // ── extractFromChat ───────────────────────────────────────────────────────

    @Test
    void extractFromChat_nonIncognito_createsContributionEvent() {
        // AC2: chat messages produce a CHAT ContributionEvent with sourceRefId = null
        String messages = "We should refactor the auth module\n---\nLet's add JWT refresh tokens";

        when(userRepository.findById(aliceId))
                .thenReturn(Optional.of(user(aliceId, "Alice Johnson", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CHAT_CONTRIBUTION_SKILLS:\n[\"Problem Solving\",\"JWT\",\"Refactoring\"]");
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contributionService.extractFromChat(workspaceId, aliceId, messages);

        ArgumentCaptor<ContributionEvent> captor = ArgumentCaptor.forClass(ContributionEvent.class);
        verify(contributionRepository).save(captor.capture());
        ContributionEvent saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(aliceId);
        assertThat(saved.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(saved.getSourceType()).isEqualTo(SourceType.CHAT);
        assertThat(saved.getSourceRefId()).isNull();       // AC2: no single UUID ref for chat batches
        assertThat(saved.getSkillSignals()).contains("Problem Solving");
        assertThat(saved.getSkillSignals()).contains("JWT");
        assertThat(saved.getProcessedAt()).isNotNull();
        verify(skillEvidenceService).generateEvidence(eq(aliceId), eq(workspaceId));
    }

    @Test
    void extractFromChat_incognitoUser_skipsEventCreation() {
        // AC3: incognito developer — no event, no AI call
        when(userRepository.findById(bobId))
                .thenReturn(Optional.of(user(bobId, "Bob Smith", true)));  // incognito=true

        contributionService.extractFromChat(workspaceId, bobId,
                "How does the auth flow work?");

        verifyNoInteractions(aiProvider);       // AC3: AI must not be called
        verifyNoInteractions(contributionRepository);  // AC3: no event created
        verifyNoInteractions(skillEvidenceService);
    }

    @Test
    void extractFromChat_aiThrows_swallowsException() {
        // AC3: silent failure — scheduler must not crash when one user's analysis fails
        when(userRepository.findById(aliceId))
                .thenReturn(Optional.of(user(aliceId, "Alice", false)));
        when(aiProvider.generateText(anyString()))
                .thenThrow(new RuntimeException("AI unavailable"));

        // Must NOT throw
        contributionService.extractFromChat(workspaceId, aliceId, "Some chat content");

        verifyNoInteractions(contributionRepository);
        verifyNoInteractions(skillEvidenceService);
    }

    @Test
    void extractFromChat_aiReturnsEmptySkills_savesEventWithEmptySignals() {
        // Edge case: AI detects no meaningful contribution patterns
        when(userRepository.findById(aliceId))
                .thenReturn(Optional.of(user(aliceId, "Alice", false)));
        when(aiProvider.generateText(anyString())).thenReturn(
                "CHAT_CONTRIBUTION_SKILLS:\n[]");
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contributionService.extractFromChat(workspaceId, aliceId, "Hi everyone!");

        ArgumentCaptor<ContributionEvent> captor = ArgumentCaptor.forClass(ContributionEvent.class);
        verify(contributionRepository).save(captor.capture());
        assertThat(captor.getValue().getSkillSignals()).isEqualTo("[]");
        assertThat(captor.getValue().getSourceType()).isEqualTo(SourceType.CHAT);
    }
}
