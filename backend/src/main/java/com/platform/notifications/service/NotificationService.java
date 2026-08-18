package com.platform.notifications.service;

import com.platform.notifications.domain.Notification;
import com.platform.notifications.repository.NotificationRepository;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Every notification-worthy event produces both a DB record (for the in-app bell)
 * and an email via MailHog - no real email ever leaves the machine, but the flow
 * is end-to-end real rather than a stub.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserLookupPort userLookupPort;
    private final JavaMailSender mailSender;

    @Transactional
    public void notify(UUID recipientUserId, String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(recipientUserId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notificationRepository.save(notification);

        sendEmail(recipientUserId, title, message);
    }

    @Transactional(readOnly = true)
    public List<Notification> listMine(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private void sendEmail(UUID recipientUserId, String title, String message) {
        try {
            UserSummary recipient = userLookupPort.getById(recipientUserId);
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(recipient.email());
            mail.setSubject(title);
            mail.setText(message);
            mailSender.send(mail);
        } catch (Exception e) {
            // Email delivery is best-effort - the in-app notification record above
            // already succeeded, so a MailHog/SMTP hiccup shouldn't fail the request
            // that triggered this notification.
            log.warn("Failed to send notification email to user {}", recipientUserId, e);
        }
    }
}
