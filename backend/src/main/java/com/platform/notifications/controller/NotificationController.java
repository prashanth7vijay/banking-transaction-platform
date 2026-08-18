package com.platform.notifications.controller;

import com.platform.auth.security.CurrentUserProvider;
import com.platform.notifications.domain.Notification;
import com.platform.notifications.dto.NotificationResponse;
import com.platform.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<NotificationResponse> mine() {
        UUID userId = currentUserProvider.get().userId();
        return notificationService.listMine(userId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        UUID userId = currentUserProvider.get().userId();
        return Map.of("count", notificationService.unreadCount(userId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        UUID userId = currentUserProvider.get().userId();
        notificationService.markAsRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
