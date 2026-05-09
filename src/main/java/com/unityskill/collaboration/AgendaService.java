package com.unityskill.collaboration;

import com.unityskill.ai.AiProvider;
import com.unityskill.collaboration.entity.AgendaStatus;
import com.unityskill.collaboration.entity.Meeting;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgendaService {

    private final MeetingRepository meetingRepository;
    private final TicketRepository ticketRepository;
    private final ChatRepository chatRepository;
    private final AiProvider aiProvider;
    private final WebSocketEventPublisher wsPublisher;

    /**
     * Asynchronously generates an AI agenda for the meeting.
     * Sets agendaStatus = FAILED on any error — no exception propagates (AC4).
     */
    @Async("taskExecutor")
    public void executeGeneration(UUID meetingId, UUID requesterId, UUID workspaceId) {
        try {
            Meeting meeting = meetingRepository.findById(meetingId)
                    .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));

            // Build context from open tickets and recent chat
            List<Ticket> openTickets = ticketRepository.findAllByProjectIdAndClosedAtIsNull(meeting.getProjectId());
            var recentMessages = chatRepository.findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(
                    workspaceId, meeting.getProjectId(), PageRequest.of(0, 20));

            String prompt = buildPrompt(meeting, openTickets,
                    recentMessages.getContent().stream().map(m -> m.getContent()).toList());

            String agenda = aiProvider.generateText(prompt);

            meeting.setAgenda(agenda);
            meeting.setAgendaStatus(AgendaStatus.READY);
            meetingRepository.save(meeting);

            wsPublisher.publishNotification(requesterId, "AGENDA_READY", Map.of(
                    "meetingId", meetingId.toString(),
                    "title", meeting.getTitle()
            ));

        } catch (Exception e) {
            log.error("Agenda generation failed for meeting {}: {}", meetingId, e.getMessage());
            meetingRepository.findById(meetingId).ifPresent(m -> {
                m.setAgendaStatus(AgendaStatus.FAILED);
                meetingRepository.save(m);
            });
        }
    }

    private String buildPrompt(Meeting meeting, List<Ticket> tickets, List<String> chatMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a structured meeting agenda for: ").append(meeting.getTitle()).append("\n\n");

        if (!tickets.isEmpty()) {
            sb.append("Open tickets in this project:\n");
            tickets.forEach(t -> sb.append("- ").append(t.getTitle()).append("\n"));
            sb.append("\n");
        }

        if (!chatMessages.isEmpty()) {
            sb.append("Recent team chat context (last 20 messages):\n");
            chatMessages.forEach(msg -> sb.append("- ").append(msg).append("\n"));
            sb.append("\n");
        }

        sb.append("Provide a concise, numbered agenda suitable for a 30-60 minute meeting. ");
        sb.append("Format each agenda item with a time estimate in parentheses.");
        return sb.toString();
    }
}
