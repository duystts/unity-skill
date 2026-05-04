package com.unityskill.portfolio;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.AlreadyEndorsedException;
import com.unityskill.common.exception.BadRequestException;
import com.unityskill.common.exception.SkillEvidenceNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.EvidenceStatus;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.portfolio.dto.EndorsementResponse;
import com.unityskill.portfolio.entity.Endorsement;
import com.unityskill.workspace.WorkspaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndorsementServiceTest {

    @Mock EndorsementRepository endorsementRepository;
    @Mock SkillEvidenceRepository skillEvidenceRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @InjectMocks EndorsementService endorsementService;

    private SkillEvidence publishedEvidence(UUID evidenceId, UUID ownerId, UUID workspaceId) {
        return SkillEvidence.builder()
                .id(evidenceId).userId(ownerId).workspaceId(workspaceId)
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Summary.").isPublished(true).build();
    }

    @Test
    void createEndorsement_validRequest_createsAndReturns() {
        // AC1: published evidence + workspace member + not owner → 201
        UUID evidenceId  = UUID.randomUUID();
        UUID ownerId     = UUID.randomUUID();
        UUID endorserId  = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(skillEvidenceRepository.findById(evidenceId))
                .thenReturn(Optional.of(publishedEvidence(evidenceId, ownerId, workspaceId)));
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, endorserId)).thenReturn(true);
        when(endorsementRepository.existsByEvidenceIdAndEndorserId(evidenceId, endorserId)).thenReturn(false);
        when(endorsementRepository.save(any())).thenAnswer(inv -> {
            Endorsement e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(userRepository.findById(endorserId))
                .thenReturn(Optional.of(User.builder().id(endorserId)
                        .email("bob@example.com").displayName("Bob").build()));

        EndorsementResponse result = endorsementService.createEndorsement(evidenceId, endorserId);

        assertThat(result.endorserName()).isEqualTo("Bob");
        assertThat(result.evidenceId()).isEqualTo(evidenceId.toString());
        assertThat(result.endorserId()).isEqualTo(endorserId.toString());
        verify(endorsementRepository).save(any());
    }

    @Test
    void createEndorsement_duplicate_throwsAlreadyEndorsed() {
        // AC2: same user endorses same evidence twice → 409
        UUID evidenceId  = UUID.randomUUID();
        UUID ownerId     = UUID.randomUUID();
        UUID endorserId  = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(skillEvidenceRepository.findById(evidenceId))
                .thenReturn(Optional.of(publishedEvidence(evidenceId, ownerId, workspaceId)));
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, endorserId)).thenReturn(true);
        when(endorsementRepository.existsByEvidenceIdAndEndorserId(evidenceId, endorserId)).thenReturn(true);

        assertThatThrownBy(() -> endorsementService.createEndorsement(evidenceId, endorserId))
                .isInstanceOf(AlreadyEndorsedException.class)
                .hasMessage("Already endorsed");

        verify(endorsementRepository, never()).save(any());
    }

    @Test
    void createEndorsement_selfEndorsement_throwsBadRequest() {
        // AC3: owner ID == endorser ID → 400
        UUID evidenceId  = UUID.randomUUID();
        UUID ownerId     = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(skillEvidenceRepository.findById(evidenceId))
                .thenReturn(Optional.of(publishedEvidence(evidenceId, ownerId, workspaceId)));

        assertThatThrownBy(() -> endorsementService.createEndorsement(evidenceId, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot endorse your own contribution");

        verify(endorsementRepository, never()).save(any());
    }

    @Test
    void createEndorsement_evidenceNotPublished_throwsBadRequest() {
        // AC1 inverse: unpublished evidence → 400
        UUID evidenceId  = UUID.randomUUID();
        UUID ownerId     = UUID.randomUUID();
        UUID endorserId  = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        SkillEvidence unpublished = SkillEvidence.builder()
                .id(evidenceId).userId(ownerId).workspaceId(workspaceId)
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Summary.").isPublished(false).build();

        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> endorsementService.createEndorsement(evidenceId, endorserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only published evidence can be endorsed");

        verify(endorsementRepository, never()).save(any());
    }

    @Test
    void createEndorsement_evidenceNotFound_throwsNotFoundException() {
        UUID evidenceId = UUID.randomUUID();
        UUID endorserId = UUID.randomUUID();
        when(skillEvidenceRepository.findById(evidenceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> endorsementService.createEndorsement(evidenceId, endorserId))
                .isInstanceOf(SkillEvidenceNotFoundException.class);
    }
}
