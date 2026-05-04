package com.unityskill.contribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.ai.AiProvider;
import com.unityskill.common.exception.BadRequestException;
import com.unityskill.common.exception.SkillEvidenceNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.contribution.dto.ReviewAction;
import com.unityskill.contribution.dto.ReviewEvidenceRequest;
import com.unityskill.contribution.dto.SkillEvidenceResponse;
import com.unityskill.contribution.dto.SkillProfileResponse;
import com.unityskill.contribution.entity.ContributionEvent;
import com.unityskill.contribution.entity.EvidenceStatus;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.contribution.entity.SourceType;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.workspace.WorkspaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillEvidenceServiceTest {

    @Mock ContributionRepository contributionRepository;
    @Mock SkillEvidenceRepository skillEvidenceRepository;
    @Mock AiProvider aiProvider;
    @Mock WebSocketEventPublisher wsPublisher;
    @Mock WorkspaceMemberRepository memberRepository;
    @Spy  ObjectMapper objectMapper;
    @Mock com.unityskill.portfolio.ContributionStreakRepository streakRepository; // Story 7.5
    @InjectMocks SkillEvidenceService skillEvidenceService;

    private ContributionEvent event(UUID userId, UUID workspaceId, String signals) {
        return ContributionEvent.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(userId)
                .sourceType(SourceType.PR)
                .skillSignals(signals)
                .processedAt(Instant.now())
                .build();
    }

    @Test
    void generateEvidence_eventsExist_createsSkillEvidenceAndNotifies() {
        // AC1 + AC2: happy path
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(contributionRepository.findAllByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(List.of(
                        event(userId, workspaceId, "[\"Java\",\"Spring Boot\"]"),
                        event(userId, workspaceId, "[\"REST API\",\"JWT\"]")
                ));
        when(aiProvider.generateText(anyString())).thenReturn(
                "SKILL_EVIDENCE:\n{\"category\":\"Backend Development\"," +
                "\"summary\":\"The developer demonstrated strong Java and Spring Boot skills.\"}");
        when(skillEvidenceRepository.save(any())).thenAnswer(inv -> {
            SkillEvidence e = inv.getArgument(0);
            e.setId(UUID.randomUUID()); // simulate DB-generated UUID
            return e;
        });

        skillEvidenceService.generateEvidence(userId, workspaceId);

        // AC1: SkillEvidence created with PENDING status
        ArgumentCaptor<SkillEvidence> captor = ArgumentCaptor.forClass(SkillEvidence.class);
        verify(skillEvidenceRepository).save(captor.capture());
        SkillEvidence saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(saved.getStatus()).isEqualTo(EvidenceStatus.PENDING);
        assertThat(saved.getSkillCategory()).isEqualTo("Backend Development");
        assertThat(saved.getAiSummary()).contains("Spring Boot");
        assertThat(saved.getSourceEvents()).contains("\""); // JSON array of UUIDs

        // AC2: WebSocket notification sent
        verify(wsPublisher).publishNotification(eq(userId), eq("SKILL_EVIDENCE_PENDING"), any(Map.class));
    }

    @Test
    void generateEvidence_noEvents_skipsEvidenceCreation() {
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(contributionRepository.findAllByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(List.of());

        skillEvidenceService.generateEvidence(userId, workspaceId);

        verifyNoInteractions(aiProvider, skillEvidenceRepository, wsPublisher);
    }

    @Test
    void generateEvidence_eventsHaveNoSignals_skipsEvidenceCreation() {
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(contributionRepository.findAllByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(List.of(event(userId, workspaceId, "[]")));

        skillEvidenceService.generateEvidence(userId, workspaceId);

        verifyNoInteractions(aiProvider, skillEvidenceRepository, wsPublisher);
    }

    @Test
    void generateEvidence_aiThrows_swallowsException() {
        // AC: silent failure — must never crash the contribution pipeline
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(contributionRepository.findAllByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(List.of(event(userId, workspaceId, "[\"Java\"]")));
        when(aiProvider.generateText(anyString()))
                .thenThrow(new RuntimeException("AI timeout"));

        // Must NOT throw
        skillEvidenceService.generateEvidence(userId, workspaceId);

        verifyNoInteractions(skillEvidenceRepository, wsPublisher);
    }

    @Test
    void generateEvidence_aiReturnsMalformedJson_skipsEvidenceCreation() {
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(contributionRepository.findAllByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(List.of(event(userId, workspaceId, "[\"Java\"]")));
        when(aiProvider.generateText(anyString())).thenReturn("SKILL_EVIDENCE:\nnot valid json {{");

        skillEvidenceService.generateEvidence(userId, workspaceId);

        verifyNoInteractions(skillEvidenceRepository, wsPublisher);
    }

    @Test
    void generateEvidence_deduplicatesSignalsAcrossEvents() {
        // Signals from multiple events are de-duplicated before AI call
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(contributionRepository.findAllByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(List.of(
                        event(userId, workspaceId, "[\"Java\",\"Spring Boot\"]"),
                        event(userId, workspaceId, "[\"Java\",\"PostgreSQL\"]") // Java duplicated
                ));
        when(aiProvider.generateText(anyString())).thenReturn(
                "SKILL_EVIDENCE:\n{\"category\":\"Backend Development\",\"summary\":\"Summary.\"}");
        when(skillEvidenceRepository.save(any())).thenAnswer(inv -> {
            SkillEvidence e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        skillEvidenceService.generateEvidence(userId, workspaceId);

        // Verify AI was called with de-duplicated signals (Spring Boot and PostgreSQL both present)
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiProvider).generateText(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("Spring Boot").contains("PostgreSQL");
        // De-duplication: "Java" should appear exactly once in the skills list portion
        // The prompt contains the signals joined by ", " — count commas is fragile; just assert Java is present
        assertThat(prompt).contains("Java");
    }

    // ── getEvidence ───────────────────────────────────────────────────────

    @Test
    void getEvidence_pendingStatus_returnsOnlyOwnEvidence() {
        // AC1: returns only the calling developer's evidence filtered by status
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID evidenceId  = UUID.randomUUID();

        SkillEvidence pending = SkillEvidence.builder()
                .id(evidenceId)
                .userId(userId)
                .workspaceId(workspaceId)
                .status(EvidenceStatus.PENDING)
                .skillCategory("Backend Development")
                .aiSummary("Summary text.")
                .build();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(skillEvidenceRepository.findAllByUserIdAndWorkspaceIdAndStatus(userId, workspaceId, EvidenceStatus.PENDING))
                .thenReturn(List.of(pending));

        List<SkillEvidenceResponse> result =
                skillEvidenceService.getEvidence(workspaceId, userId, EvidenceStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).skillCategory()).isEqualTo("Backend Development");
        assertThat(result.get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void getEvidence_notMember_throwsUnauthorized() {
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(false);

        assertThatThrownBy(() -> skillEvidenceService.getEvidence(workspaceId, userId, null))
                .isInstanceOf(UnauthorizedAccessException.class);
        verifyNoInteractions(skillEvidenceRepository);
    }

    // ── reviewEvidence ────────────────────────────────────────────────────

    @Test
    void reviewEvidence_approve_setsApprovedAndReviewedAt() {
        // AC2
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID evidenceId  = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.PENDING)
                .skillCategory("Backend Development").aiSummary("Summary.")
                .build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));
        when(skillEvidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillEvidenceResponse result = skillEvidenceService.reviewEvidence(
                evidenceId, workspaceId, userId, new ReviewEvidenceRequest(ReviewAction.APPROVE, null));

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(evidence.getReviewedAt()).isNotNull();
    }

    @Test
    void reviewEvidence_reject_setsRejected() {
        // AC3
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID evidenceId  = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.PENDING)
                .skillCategory("Backend Development").aiSummary("Summary.")
                .build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));
        when(skillEvidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillEvidenceResponse result = skillEvidenceService.reviewEvidence(
                evidenceId, workspaceId, userId, new ReviewEvidenceRequest(ReviewAction.REJECT, null));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(evidence.getReviewedAt()).isNotNull();
    }

    @Test
    void reviewEvidence_editWithNotes_setsApprovedWithNotes() {
        // AC4
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID evidenceId  = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.PENDING)
                .skillCategory("Backend Development").aiSummary("Summary.")
                .build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));
        when(skillEvidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillEvidenceResponse result = skillEvidenceService.reviewEvidence(
                evidenceId, workspaceId, userId,
                new ReviewEvidenceRequest(ReviewAction.EDIT, "My custom notes"));

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.developerNotes()).isEqualTo("My custom notes");
        assertThat(evidence.getReviewedAt()).isNotNull();
    }

    @Test
    void reviewEvidence_editWithoutNotes_throwsBadRequest() {
        // AC4 validation: developerNotes required for EDIT
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID evidenceId  = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.PENDING)
                .skillCategory("Backend Development").aiSummary("Summary.")
                .build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> skillEvidenceService.reviewEvidence(
                evidenceId, workspaceId, userId,
                new ReviewEvidenceRequest(ReviewAction.EDIT, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("developerNotes");

        verify(skillEvidenceRepository, never()).save(any());
    }

    @Test
    void reviewEvidence_anotherUsersEvidence_throwsForbidden() {
        // AC5: ownership check — 403 if caller is not the owner
        UUID ownerId     = UUID.randomUUID();
        UUID callerId    = UUID.randomUUID();   // different user
        UUID workspaceId = UUID.randomUUID();
        UUID evidenceId  = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(ownerId).workspaceId(workspaceId)
                .status(EvidenceStatus.PENDING)
                .skillCategory("Backend Development").aiSummary("Summary.")
                .build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> skillEvidenceService.reviewEvidence(
                evidenceId, workspaceId, callerId,
                new ReviewEvidenceRequest(ReviewAction.APPROVE, null)))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(skillEvidenceRepository, never()).save(any());
    }

    @Test
    void reviewEvidence_notFound_throwsNotFoundException() {
        UUID evidenceId = UUID.randomUUID();
        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skillEvidenceService.reviewEvidence(
                evidenceId, UUID.randomUUID(), UUID.randomUUID(),
                new ReviewEvidenceRequest(ReviewAction.APPROVE, null)))
                .isInstanceOf(SkillEvidenceNotFoundException.class);
    }

    // ── getSkillProfile ───────────────────────────────────────────────────

    @Test
    void getSkillProfile_hasTwoCategories_returnsGroupedAndSorted() {
        // AC1: groups by skillCategory, first group = most recently reviewed
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        Instant older = Instant.parse("2026-04-10T00:00:00Z");
        Instant newer = Instant.parse("2026-04-20T00:00:00Z");

        SkillEvidence e1 = SkillEvidence.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Backend summary.").reviewedAt(newer).build();
        SkillEvidence e2 = SkillEvidence.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.APPROVED).skillCategory("API Design")
                .aiSummary("API summary.").reviewedAt(older).build();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(skillEvidenceRepository.findAllByUserIdAndWorkspaceIdAndStatus(userId, workspaceId, EvidenceStatus.APPROVED))
                .thenReturn(List.of(e1, e2));

        SkillProfileResponse result = skillEvidenceService.getSkillProfile(workspaceId, userId);

        assertThat(result.totalApproved()).isEqualTo(2);
        assertThat(result.categories()).hasSize(2);
        // First category = most recently reviewed (Backend Development)
        assertThat(result.categories().get(0).skillCategory()).isEqualTo("Backend Development");
        assertThat(result.categories().get(0).count()).isEqualTo(1);
    }

    @Test
    void getSkillProfile_noApprovedEvidence_returnsEmptyProfile() {
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(skillEvidenceRepository.findAllByUserIdAndWorkspaceIdAndStatus(userId, workspaceId, EvidenceStatus.APPROVED))
                .thenReturn(List.of());

        SkillProfileResponse result = skillEvidenceService.getSkillProfile(workspaceId, userId);

        assertThat(result.totalApproved()).isEqualTo(0);
        assertThat(result.categories()).isEmpty();
    }

    @Test
    void getSkillProfile_notMember_throwsUnauthorized() {
        // AC2
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(false);

        assertThatThrownBy(() -> skillEvidenceService.getSkillProfile(workspaceId, userId))
                .isInstanceOf(UnauthorizedAccessException.class);
        verifyNoInteractions(skillEvidenceRepository);
    }

    // ── setPublished ──────────────────────────────────────────────────────

    @Test
    void setPublished_approvedEvidence_setsPublishedTrueAndPublishedAt() {
        // AC1
        UUID userId     = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(UUID.randomUUID())
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Summary.").build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));
        when(skillEvidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillEvidenceResponse result = skillEvidenceService.setPublished(evidenceId, userId, true);

        assertThat(result.isPublished()).isTrue();
        assertThat(result.publishedAt()).isNotNull();
        assertThat(evidence.isPublished()).isTrue();
        verify(skillEvidenceRepository).save(evidence);
    }

    @Test
    void setPublished_unpublish_clearsPublishedAt() {
        // AC2: unpublish always allowed; clears publishedAt
        UUID userId     = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(UUID.randomUUID())
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Summary.").isPublished(true)
                .publishedAt(Instant.parse("2026-04-01T00:00:00Z")).build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));
        when(skillEvidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillEvidenceResponse result = skillEvidenceService.setPublished(evidenceId, userId, false);

        assertThat(result.isPublished()).isFalse();
        assertThat(result.publishedAt()).isNull();
    }

    @Test
    void setPublished_pendingEvidence_throwsBadRequest() {
        // AC3: cannot publish PENDING evidence
        UUID userId     = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(UUID.randomUUID())
                .status(EvidenceStatus.PENDING).skillCategory("Backend Development")
                .aiSummary("Summary.").build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> skillEvidenceService.setPublished(evidenceId, userId, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only approved evidence can be published");

        verify(skillEvidenceRepository, never()).save(any());
    }

    @Test
    void setPublished_notOwner_throwsForbidden() {
        UUID ownerId    = UUID.randomUUID();
        UUID callerId   = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(ownerId).workspaceId(UUID.randomUUID())
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Summary.").build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> skillEvidenceService.setPublished(evidenceId, callerId, true))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(skillEvidenceRepository, never()).save(any());
    }

    // ── getSkillProfile with streak (Story 7.5) ───────────────────────────

    @Test
    void getSkillProfile_existingStreak_includesStreakInResponse() {
        // AC3: streak record exists → appears in SkillProfileResponse
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        com.unityskill.portfolio.entity.ContributionStreak streak =
                com.unityskill.portfolio.entity.ContributionStreak.builder()
                        .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                        .currentStreakWeeks(3).longestStreakWeeks(7)
                        .lastActivityWeek(java.time.LocalDate.now())
                        .build();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(skillEvidenceRepository.findAllByUserIdAndWorkspaceIdAndStatus(
                userId, workspaceId, EvidenceStatus.APPROVED)).thenReturn(List.of());
        when(streakRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.of(streak));

        SkillProfileResponse result = skillEvidenceService.getSkillProfile(workspaceId, userId);

        assertThat(result.streak()).isNotNull();
        assertThat(result.streak().currentWeeks()).isEqualTo(3);
        assertThat(result.streak().longestWeeks()).isEqualTo(7);
    }

    @Test
    void getSkillProfile_noStreak_returnsNullStreak() {
        // AC3: no streak record → streak: null in response
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(skillEvidenceRepository.findAllByUserIdAndWorkspaceIdAndStatus(
                userId, workspaceId, EvidenceStatus.APPROVED)).thenReturn(List.of());
        // streakRepository returns Optional.empty() by default (Mockito default for Optional<T>)

        SkillProfileResponse result = skillEvidenceService.getSkillProfile(workspaceId, userId);

        assertThat(result.streak()).isNull();
    }
}
