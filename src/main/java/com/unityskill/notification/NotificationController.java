package com.unityskill.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var result = notificationService.getNotifications(UUID.fromString(userId), page, size);
        return ResponseEntity.ok(Map.of(
            "data", result.getContent(),
            "pagination", Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements()
            )
        ));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {
        notificationService.markRead(id, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
