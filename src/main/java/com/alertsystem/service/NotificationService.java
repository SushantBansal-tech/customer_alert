package com.alertsystem.service;

import com.alertsystem.entity.Alert;
import com.alertsystem.entity.Issue;
import com.alertsystem.entity.NotificationLog;
import com.alertsystem.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    @Autowired
    private NotificationLogRepository notificationLogRepository;
    
    @Autowired
    private EmailService emailService;
    
    /**
     * Send alert notification (Async)
     */
    @Async
    public void sendAlert(Alert alert, Issue issue) {
        try {
            logger.info("Sending alert {} for issue {}", alert.getId(), issue.getId());
            
            // 1. Notify Operations Team
            notifyOperations(alert, issue);
            
            // 2. Notify Admin/Lead
            notifyAdmin(alert, issue);
            
            // 3. In-app notification (dashboard update)
            notifyInApp(alert, issue);
            
        } catch (Exception e) {
            logger.error("Error sending alert notification", e);
        }
    }
    
    /**
     * Notify Operations Team
     */
    private void notifyOperations(Alert alert, Issue issue) {
        try {
            String subject = String.format("🚨 ALERT: %s - Issue #%s", issue.getIssueType(), issue.getId().toString().substring(0, 8));
            
            String body = buildAlertEmailBody(issue);
            
            // Send email to operations team
            emailService.sendEmail("ops-team@company.com", subject, body);
            
            // Log notification
            logNotification(alert, UUID.randomUUID(), "EMAIL", "SUCCESS", 
                          "Notification sent to Operations Team");
            
            logger.info("Alert notification sent to Operations Team");
            
        } catch (Exception e) {
            logger.error("Error notifying operations team", e);
            logNotification(alert, UUID.randomUUID(), "EMAIL", "FAILED", 
                          "Error: " + e.getMessage());
        }
    }
    
    /**
     * Notify Admin/Lead
     */
    private void notifyAdmin(Alert alert, Issue issue) {
        try {
            String subject = String.format("⚠️ CRITICAL ISSUE: %s", issue.getIssueType());
            
            String body = buildAlertEmailBody(issue);
            body += "\n\n[ADMIN ACTION REQUIRED] Please review and assign to appropriate team.";
            
            // Send email to admin
            emailService.sendEmail("admin@company.com", subject, body);
            
            // Log notification
            logNotification(alert, UUID.randomUUID(), "EMAIL", "SUCCESS", 
                          "Notification sent to Admin");
            
            logger.info("Alert notification sent to Admin");
            
        } catch (Exception e) {
            logger.error("Error notifying admin", e);
            logNotification(alert, UUID.randomUUID(), "EMAIL", "FAILED", 
                          "Error: " + e.getMessage());
        }
    }
    
    /**
     * In-app notification (broadcast to dashboard)
     */
    private void notifyInApp(Alert alert, Issue issue) {
        try {
            // In real implementation, use WebSocket to broadcast to connected clients
            logger.info("Broadcasting in-app notification for alert {}", alert.getId());
            
            logNotification(alert, UUID.randomUUID(), "IN_APP", "SUCCESS", 
                          "In-app notification broadcasted");
            
        } catch (Exception e) {
            logger.error("Error sending in-app notification", e);
        }
    }
    
    /**
     * Build email body for alert
     */
    private String buildAlertEmailBody(Issue issue) {
        return String.format(
            "Alert Details:\n" +
            "==============\n" +
            "Issue ID: %s\n" +
            "Type: %s\n" +
            "Severity: %s\n" +
            "Description: %s\n" +
            "Detected At: %s\n" +
            "Status: %s\n" +
            "Affected Users: %d\n\n" +
            "Please check the dashboard for more details and take action.",
            issue.getId(),
            issue.getIssueType(),
            issue.getSeverity(),
            issue.getDescription(),
            issue.getDetectedAt(),
            issue.getStatus(),
            issue.getAffectedUsersCount()
        );
    }
    
    /**
     * Log notification
     */
    private void logNotification(Alert alert, UUID recipientUserId, String type, 
                                String status, String message) {
        try {
            NotificationLog log = NotificationLog.builder()
                .id(UUID.randomUUID())
                .alertId(alert.getId())
                .recipientUserId(recipientUserId)
                .notificationType(type)
                .status(status)
                .message(message)
                .build();
            
            notificationLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Error logging notification", e);
        }
    }
}