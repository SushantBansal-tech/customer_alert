package com.alertsystem.service;

import com.alertsystem.entity.Alert;
import com.alertsystem.entity.AlertStatus;
import com.alertsystem.entity.Issue;
import com.alertsystem.entity.IssueStatus;
import com.alertsystem.repository.AlertRepository;
import com.alertsystem.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AlertManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertManagementService.class);
    
    private static final int RECHECK_INTERVAL_MINUTES = 10;
    private static final int MAX_NOTIFICATIONS = 3;
    
    @Autowired
    private AlertRepository alertRepository;
    
    @Autowired
    private IssueRepository issueRepository;
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * Create alert for issue (prevent duplicates)
     */
    public void createAlert(Issue issue) {
        try {
            // Check if alert already exists for this issue
            Optional<Alert> existingAlert = alertRepository.findByIssueId(issue.getId());
            
            if (existingAlert.isPresent()) {
                logger.debug("Alert already exists for issue {}", issue.getId());
                return;
            }
            
            // Create new alert
            Alert alert = Alert.builder()
                .id(UUID.randomUUID())
                .issueId(issue.getId())
                .status(AlertStatus.PENDING)
                .notificationCount(0)
                .nextRecheckAt(LocalDateTime.now().plusMinutes(RECHECK_INTERVAL_MINUTES))
                .build();
            
            Alert savedAlert = alertRepository.save(alert);
            logger.info("Created alert {} for issue {}", savedAlert.getId(), issue.getId());
            
            // Send notification
            notifyAboutAlert(savedAlert, issue);
            
        } catch (Exception e) {
            logger.error("Error creating alert for issue {}", issue.getId(), e);
        }
    }
    
    /**
     * Notify team about the alert
     */
    private void notifyAboutAlert(Alert alert, Issue issue) {
        try {
            // Mark as sent
            alert.setStatus(AlertStatus.SENT);
            alert.setLastNotifiedAt(LocalDateTime.now());
            alert.setNotificationCount(alert.getNotificationCount() + 1);
            
            alertRepository.save(alert);
            
            // Send actual notification
            notificationService.sendAlert(alert, issue);
            
        } catch (Exception e) {
            logger.error("Error notifying about alert", e);
        }
    }
    
    /**
     * Re-check alerts and resend if issue still exists
     */
    public void reCheckAlerts() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Alert> alertsToRecheck = alertRepository.findAlertsForRecheck(AlertStatus.SENT, now);
            
            logger.debug("Found {} alerts to recheck", alertsToRecheck.size());
            
            for (Alert alert : alertsToRecheck) {
                reCheckAlert(alert);
            }
            
        } catch (Exception e) {
            logger.error("Error rechecking alerts", e);
        }
    }
    
    /**
     * Recheck single alert
     */
    private void reCheckAlert(Alert alert) {
        try {
            Optional<Issue> issue = issueRepository.findById(alert.getIssueId());
            
            if (issue.isEmpty()) {
                logger.warn("Issue {} not found for alert {}", alert.getIssueId(), alert.getId());
                return;
            }
            
            Issue issueData = issue.get();
            
            // If issue is resolved, acknowledge the alert
            if (issueData.getStatus() == IssueStatus.RESOLVED) {
                alert.setStatus(AlertStatus.ACKNOWLEDGED);
                alertRepository.save(alert);
                logger.info("Alert {} marked as ACKNOWLEDGED (issue resolved)", alert.getId());
                return;
            }
            
            // If notification count < max, resend
            if (alert.getNotificationCount() < MAX_NOTIFICATIONS) {
                notifyAboutAlert(alert, issueData);
                alert.setNextRecheckAt(LocalDateTime.now().plusMinutes(RECHECK_INTERVAL_MINUTES));
                alertRepository.save(alert);
                logger.info("Alert {} re-checked and notification resent (count: {})", 
                           alert.getId(), alert.getNotificationCount());
            } else {
                // Max notifications reached
                alert.setStatus(AlertStatus.ACKNOWLEDGED);
                alertRepository.save(alert);
                logger.info("Alert {} max notifications reached, marked as ACKNOWLEDGED", alert.getId());
            }
            
        } catch (Exception e) {
            logger.error("Error rechecking alert", e);
        }
    }
    
    /**
     * Scheduled re-check (every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void scheduledReCheck() {
        reCheckAlerts();
    }
    
    public Alert getAlertDetails(UUID alertId) {
        return alertRepository.findById(alertId).orElse(null);
    }
    
    public List<Alert> getPendingAlerts() {
        return alertRepository.findByStatus(AlertStatus.PENDING);
    }
}