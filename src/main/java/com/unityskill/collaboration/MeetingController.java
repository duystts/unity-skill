package com.unityskill.collaboration;

import com.unityskill.collaboration.dto.CreateMeetingRequest;
import com.unityskill.collaboration.dto.MeetingResponse;
import com.unityskill.collaboration.dto.UpdateMeetingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final AgendaService agendaService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createMeeting(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal String userId) {
        MeetingResponse response = meetingService.createMeeting(
                workspaceId, UUID.fromString(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", response));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listMeetings(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        List<MeetingResponse> meetings = meetingService.listMeetings(
                workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", meetings));
    }

    @PatchMapping("/{meetingId}")
    public ResponseEntity<Map<String, Object>> updateMeeting(
            @PathVariable UUID workspaceId,
            @PathVariable UUID meetingId,
            @Valid @RequestBody UpdateMeetingRequest request,
            @AuthenticationPrincipal String userId) {
        MeetingResponse response = meetingService.updateMeeting(
                workspaceId, meetingId, UUID.fromString(userId), request);
        return ResponseEntity.ok(Map.of("data", response));
    }

    @GetMapping("/{meetingId}")
    public ResponseEntity<Map<String, Object>> getMeeting(
            @PathVariable UUID workspaceId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal String userId) {
        MeetingResponse response = meetingService.getMeeting(
                workspaceId, meetingId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", response));
    }

    @PostMapping("/{meetingId}/agenda/generate")
    public ResponseEntity<Map<String, Object>> generateAgenda(
            @PathVariable UUID workspaceId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal String userId) {
        UUID callerId = UUID.fromString(userId);
        meetingService.markAgendaGenerating(workspaceId, meetingId, callerId); // sync: role check + GENERATING
        agendaService.executeGeneration(meetingId, callerId, workspaceId);     // @Async via Spring proxy
        return ResponseEntity.accepted().body(Map.of("message", "Agenda generation started"));
    }
}
