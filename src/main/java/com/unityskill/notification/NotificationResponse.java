package com.unityskill.notification;

import com.unityskill.notification.entity.Notification;

public record NotificationResponse(
    String id,
    String workspaceId,
    String type,
    String payload,
    boolean read,
    String createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
            n.getId().toString(),
            n.getWorkspaceId().toString(),
            n.getType(),
            n.getPayloadJson(),
            n.isRead(),
            n.getCreatedAt().toString()
        );
    }
}
