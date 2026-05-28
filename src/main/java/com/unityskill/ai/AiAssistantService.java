package com.unityskill.ai;

import com.unityskill.ai.dto.AiChatRequest;
import com.unityskill.ai.dto.AiChatRequest.ChatTurn;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.ProjectRepository;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.WorkflowStageRepository;
import com.unityskill.project.entity.Project;
import com.unityskill.project.entity.Ticket;
import com.unityskill.project.entity.WorkflowStage;
import com.unityskill.workspace.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAssistantService {

    private final AiProvider aiProvider;
    private final ProjectRepository projectRepository;
    private final WorkflowStageRepository stageRepository;
    private final TicketRepository ticketRepository;
    private final WorkspaceMemberRepository memberRepository;

    /**
     * Sends a user message to the AI assistant with project context embedded in the system prompt.
     * Conversation history is included in the prompt so the model can refer to previous turns.
     */
    public String chat(UUID workspaceId, UUID projectId, UUID callerId,
                       String message, List<ChatTurn> history) {

        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)) {
            throw new UnauthorizedAccessException("Access denied: not a workspace member");
        }

        // ── Build project context ─────────────────────────────────────────────
        Project project = projectRepository
                .findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        List<WorkflowStage> stages = stageRepository
                .findAllByProjectIdOrderByPositionAsc(projectId);

        List<Ticket> tickets = ticketRepository.findAllByProjectId(projectId);

        // Count tickets per stage
        Map<UUID, Long> countByStage = tickets.stream()
                .filter(t -> t.getStageId() != null)
                .collect(Collectors.groupingBy(Ticket::getStageId, Collectors.counting()));

        long totalTickets  = tickets.size();
        long openTickets   = tickets.stream().filter(t -> t.getClosedAt() == null).count();
        long closedTickets = totalTickets - openTickets;
        long withPr        = tickets.stream().filter(t -> t.getGithubPrUrl() != null).count();

        // ── Compose system context block ──────────────────────────────────────
        StringBuilder ctx = new StringBuilder();
        ctx.append("You are an AI assistant for Unity Skill, a Kanban-based project management tool.\n");
        ctx.append("You help the team understand their project status, answer questions about tickets and workflow, and offer helpful suggestions.\n\n");
        ctx.append("=== Current Project Context ===\n");
        ctx.append("Project: ").append(project.getName()).append("\n");
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            ctx.append("Description: ").append(project.getDescription()).append("\n");
        }
        ctx.append("\nWorkflow Stages (in order):\n");
        for (WorkflowStage s : stages) {
            long cnt = countByStage.getOrDefault(s.getId(), 0L);
            ctx.append("  - ").append(s.getName());
            if (s.isClosedState()) ctx.append(" [CLOSED STATE]");
            ctx.append(" — ").append(cnt).append(" ticket").append(cnt == 1 ? "" : "s").append("\n");
        }
        ctx.append("\nTicket Summary:\n");
        ctx.append("  Total : ").append(totalTickets).append("\n");
        ctx.append("  Open  : ").append(openTickets).append("\n");
        ctx.append("  Closed: ").append(closedTickets).append("\n");
        ctx.append("  With PR: ").append(withPr).append("\n");
        ctx.append("=== End of Context ===\n\n");

        ctx.append("Instructions:\n");
        ctx.append("- Answer in the same language the user writes in (Vietnamese or English).\n");
        ctx.append("- Be concise and helpful. Focus on project management topics.\n");
        ctx.append("- When you don't know something specific (e.g., a ticket's detail), say so honestly.\n\n");

        // ── Append conversation history ───────────────────────────────────────
        if (history != null && !history.isEmpty()) {
            ctx.append("Previous conversation:\n");
            for (ChatTurn turn : history) {
                String label = "user".equals(turn.role()) ? "User" : "Assistant";
                ctx.append(label).append(": ").append(turn.content()).append("\n");
            }
            ctx.append("\n");
        }

        ctx.append("User: ").append(message).append("\nAssistant:");

        return aiProvider.generateText(ctx.toString());
    }
}
