package com.unityskill.achievement;

import com.unityskill.achievement.dto.AchievementResponse;
import com.unityskill.achievement.dto.TagsResponse;
import com.unityskill.achievement.dto.UserAchievementResponse;
import com.unityskill.achievement.entity.TicketTag;
import com.unityskill.achievement.entity.TicketTagValue;
import com.unityskill.common.exception.TicketNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.TicketRepository;
import com.unityskill.workspace.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final TicketTagRepository ticketTagRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final TicketRepository ticketRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final AchievementEvaluator achievementEvaluator;

    // ── Ticket Tags ───────────────────────────────────────────────────────────

    @Transactional
    public TagsResponse addTag(UUID workspaceId, UUID ticketId, TicketTagValue tag, UUID callerId) {
        requireMember(workspaceId, callerId);
        ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);

        if (!ticketTagRepository.existsByTicketIdAndTag(ticketId, tag)) {
            ticketTagRepository.save(TicketTag.builder()
                .ticketId(ticketId)
                .tag(tag)
                .build());
        }

        return getTagsForTicket(workspaceId, ticketId, callerId);
    }

    @Transactional
    public TagsResponse removeTag(UUID workspaceId, UUID ticketId, TicketTagValue tag, UUID callerId) {
        requireMember(workspaceId, callerId);
        ticketTagRepository.findAllByTicketId(ticketId).stream()
            .filter(t -> t.getTag() == tag)
            .forEach(ticketTagRepository::delete);
        return getTagsForTicket(workspaceId, ticketId, callerId);
    }

    public TagsResponse getTagsForTicket(UUID workspaceId, UUID ticketId, UUID callerId) {
        requireMember(workspaceId, callerId);
        List<TicketTagValue> tags = ticketTagRepository.findAllByTicketId(ticketId)
            .stream()
            .map(TicketTag::getTag)
            .collect(Collectors.toList());
        return new TagsResponse(tags);
    }

    // ── Achievements ──────────────────────────────────────────────────────────

    public List<UserAchievementResponse> getMyAchievements(UUID userId) {
        return userAchievementRepository.findAllByUserIdOrderByEarnedAtDesc(userId)
            .stream()
            .map(UserAchievementResponse::from)
            .filter(r -> r != null)
            .collect(Collectors.toList());
    }

    public List<AchievementResponse> getAllDefinitions() {
        return AchievementDefinitions.ALL.stream()
            .map(AchievementResponse::from)
            .collect(Collectors.toList());
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void requireMember(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedAccessException("Not a workspace member");
        }
    }
}
