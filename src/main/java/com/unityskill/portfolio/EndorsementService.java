package com.unityskill.portfolio;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.AlreadyEndorsedException;
import com.unityskill.common.exception.BadRequestException;
import com.unityskill.common.exception.SkillEvidenceNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.portfolio.dto.EndorsementResponse;
import com.unityskill.portfolio.entity.Endorsement;
import com.unityskill.workspace.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndorsementService {

    private final EndorsementRepository endorsementRepository;
    private final SkillEvidenceRepository skillEvidenceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    /**
     * Creates an endorsement for published skill evidence.
     * Check order (defense in depth):
     * 1. Evidence exists (404)
     * 2. Self-endorsement check (400) — cheapest, no extra DB lookups
     * 3. Evidence published check (400)
     * 4. Workspace membership check (403)
     * 5. Duplicate check (409)
     *
     * @param evidenceId the evidence to endorse
     * @param endorserId the authenticated caller
     * @return the created endorsement with endorser display name
     */
    public EndorsementResponse createEndorsement(UUID evidenceId, UUID endorserId) {
        SkillEvidence evidence = skillEvidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new SkillEvidenceNotFoundException(evidenceId));

        // AC3: self-endorsement check
        if (evidence.getUserId().equals(endorserId)) {
            throw new BadRequestException("Cannot endorse your own contribution");
        }

        // AC1: only published evidence can be endorsed
        if (!evidence.isPublished()) {
            throw new BadRequestException("Only published evidence can be endorsed");
        }

        // AC1: endorser must be a member of the evidence's workspace
        if (!memberRepository.existsByWorkspaceIdAndUserId(evidence.getWorkspaceId(), endorserId)) {
            throw new UnauthorizedAccessException("Must be a workspace member to endorse");
        }

        // AC2: duplicate endorsement check
        if (endorsementRepository.existsByEvidenceIdAndEndorserId(evidenceId, endorserId)) {
            throw new AlreadyEndorsedException();
        }

        Endorsement endorsement = endorsementRepository.save(
                Endorsement.builder()
                        .evidenceId(evidenceId)
                        .endorserId(endorserId)
                        .workspaceId(evidence.getWorkspaceId())
                        .build()
        );

        String endorserName = userRepository.findById(endorserId)
                .map(User::getDisplayName)
                .orElse("Unknown");

        return EndorsementResponse.from(endorsement, endorserName);
    }
}
