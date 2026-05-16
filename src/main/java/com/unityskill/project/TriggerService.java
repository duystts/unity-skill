package com.unityskill.project;

import com.unityskill.common.exception.InvalidTriggerException;
import com.unityskill.common.exception.StageNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.project.dto.CreateTriggerRuleRequest;
import com.unityskill.project.dto.TriggerRuleResponse;
import com.unityskill.project.entity.AutoTriggerRule;
import com.unityskill.project.entity.TriggerType;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TriggerService {

    private final AutoTriggerRuleRepository ruleRepository;
    private final WorkflowStageRepository stageRepository;
    private final TicketRepository ticketRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final TicketActivityService ticketActivityService;

    @Transactional
    public TriggerRuleResponse createRule(
            CreateTriggerRuleRequest req,
            UUID workspaceId,
            UUID projectId,
            UUID sourceStageId,
            UUID callerId) {

        requirePmOrAdmin(workspaceId, callerId);

        // Validate sourceStageId belongs to project
        stageRepository.findByIdAndProjectId(sourceStageId, projectId)
                .orElseThrow(StageNotFoundException::new);

        // Validate targetStageId belongs to same project
        stageRepository.findByIdAndProjectId(req.targetStageId(), projectId)
                .orElseThrow(() -> new InvalidTriggerException(
                        "Trigger references a stage in a different project"));

        AutoTriggerRule rule = AutoTriggerRule.builder()
                .projectId(projectId)
                .workspaceId(workspaceId)
                .triggerType(req.triggerType())
                .sourceStageId(sourceStageId)
                .targetStageId(req.targetStageId())
                .build();

        rule = ruleRepository.saveAndFlush(rule);
        return TriggerRuleResponse.from(rule);
    }

    public List<TriggerRuleResponse> listRules(UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return ruleRepository.findAllByProjectId(projectId)
                .stream()
                .map(TriggerRuleResponse::from)
                .toList();
    }

    @Transactional
    public void evaluate(TriggerType triggerType, UUID ticketId) {
        ticketRepository.findById(ticketId).ifPresent(ticket ->
            ruleRepository.findAllByProjectIdAndTriggerType(ticket.getProjectId(), triggerType)
                    .stream()
                    .filter(rule -> rule.getSourceStageId() == null
                                 || rule.getSourceStageId().equals(ticket.getStageId()))
                    .findFirst()
                    .ifPresent(rule -> {
                        UUID previousStageId = ticket.getStageId();
                        ticket.setStageId(rule.getTargetStageId());
                        // AC 4: auto-close if target stage is a closed state
                        stageRepository.findById(rule.getTargetStageId()).ifPresent(stage -> {
                            if (stage.isClosedState()) {
                                ticket.setClosedAt(Instant.now());
                            }
                        });
                        ticketRepository.save(ticket);
                        ticketActivityService.logStageChanged(ticket, previousStageId, rule.getTargetStageId(), null, "GitHub Automation");
                        // AC 2: publish WebSocket event
                        eventPublisher.publishToTopic(
                            "/topic/workspace/" + ticket.getWorkspaceId() + "/tickets",
                            "TICKET_STAGE_CHANGED",
                            Map.of("ticketId", ticketId.toString(), "newStageId", rule.getTargetStageId().toString())
                        );
                    })
        );
    }

    @Transactional
    public void deleteRule(UUID workspaceId, UUID projectId, UUID ruleId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);
        AutoTriggerRule rule = ruleRepository.findById(ruleId)
                .filter(r -> r.getProjectId().equals(projectId) && r.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new com.unityskill.common.exception.StageNotFoundException());
        ruleRepository.delete(rule);
    }

    private void requirePmOrAdmin(UUID workspaceId, UUID userId) {
        WorkspaceMember member = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedAccessException("Access denied: not a workspace member"));
        if (member.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Access denied: PM or Admin role required");
        }
    }

    private void requireMember(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedAccessException("Access denied: not a workspace member");
        }
    }
}
