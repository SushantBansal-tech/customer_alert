package com.alerting.platform.alerts.service;

import com.alerting.platform.alerts.model.*;
import com.alerting.platform.alerts.model.Alert.AlertType;
import com.alerting.platform.alerts.model.Alert.ResolutionType;
import com.alerting.platform.alerts.model.AlertTimeline.TimelineEventType;
import com.alerting.platform.alerts.repository.*;
import com.alerting.platform.notification.NotificationService;
import com.alerting.platform.processing.service.AggregationService.AggregatedMetrics;
import com.alerting.platform.processing.service.UserLevelAggregationService.UserMetrics;
import com.alerting.platform.rules.model.AlertRule;
import com.alerting.platform.rules.model.UserAlertRule;
import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import com.alerting.platform.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertTimelineRepository timelineRepository;
    private final AlertCommentRepository commentRepository;
    private final DeduplicationService deduplicationService;
    private final NotificationService notificationService;
    private final TeamService teamService;

    // ========== Alert Creation (Aggregate Level) ==========
    @Transactional
    public void createAlert(AlertRule rule, AggregatedMetrics metrics, double actualValue) {
        String dedupKey = buildAggregateDeduplicationKey(rule);
        
        if (deduplicationService.isDuplicate(dedupKey)) {
            log.debug("Suppressing duplicate aggregate alert for rule: {}", rule.getName());
            return;
        }

        Alert alert = Alert.builder()
            .appId(rule.getAppId())
            .feature(rule.getFeature())
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .alertType(AlertType.AGGREGATE)
            .affectedUserCount(estimateAffectedUsers(metrics))
            .severity(rule.getSeverity())
            .message(buildAggregateAlertMessage(rule, metrics, actualValue))
            .metricValue(actualValue)
            .threshold(rule.getThreshold())
            .sampleSize(metrics.getTotalCount())
            .status(AlertStatus.TRIGGERED)
            .triggeredAt(Instant.now())
            .notificationCount(0)
            .escalationLevel(0)
            .build();

        alert = alertRepository.save(alert);
        addTimelineEntry(alert, TimelineEventType.CREATED, "Alert created", "SYSTEM");

        // Auto-assign to responsible team
        autoAssignAlert(alert);

        // Send notifications
        sendNotifications(alert, rule.getNotificationChannels());

        // Mark for deduplication
        deduplicationService.markAlertCreated(dedupKey, alert.getId());

        log.info("Aggregate alert created: id={}, rule={}, severity={}", 
            alert.getId(), rule.getName(), rule.getSeverity());
    }

    // ========== Alert Creation (User Level) ==========
    @Transactional
    public void createUserLevelAlert(UserAlertRule rule, UserMetrics metrics) {
        String dedupKey = buildUserDeduplicationKey(rule, metrics.getUserId());
        
        if (deduplicationService.isDuplicate(dedupKey)) {
            log.debug("Suppressing duplicate user alert for user: {}", metrics.getUserId());
            return;
        }

        Alert alert = Alert.builder()
            .appId(rule.getAppId())
            .feature(rule.getFeature())
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .alertType(AlertType.USER_LEVEL)
            .affectedUserId(metrics.getUserId())
            .severity(rule.getSeverity())
            .message(buildUserAlertMessage(rule, metrics))
            .metricValue((double) metrics.getConsecutiveFailures())
            .threshold((double) rule.getMaxConsecutiveFailures())
            .sampleSize((long) metrics.getFailuresInWindow())
            .status(AlertStatus.TRIGGERED)
            .triggeredAt(Instant.now())
            .notificationCount(0)
            .escalationLevel(0)
            .build();

        alert = alertRepository.save(alert);
        addTimelineEntry(alert, TimelineEventType.CREATED, 
            "User-level alert created for user: " + metrics.getUserId(), "SYSTEM");

        // Auto-assign
        autoAssignAlert(alert);

        // Send notifications
        sendNotifications(alert, rule.getNotificationChannels());

        // Mark for deduplication
        deduplicationService.markAlertCreated(dedupKey, alert.getId());

        log.info("User-level alert created: id={}, user={}, feature={}", 
            alert.getId(), metrics.getUserId(), rule.getFeature());
    }

    // ========== Auto-Assignment ==========
    private void autoAssignAlert(Alert alert) {
        Team responsibleTeam = teamService.findResponsibleTeam(alert.getAppId(), alert.getFeature());
        
        if (responsibleTeam != null) {
            alert.setAssignedTeam(responsibleTeam);
            alert.setStatus(AlertStatus.ASSIGNED);
            alert.setAssignedAt(Instant.now());
            alert.setAssignedBy("SYSTEM");

            // Try to assign to on-call member
            TeamMember onCallMember = teamService.getOnCallMember(responsibleTeam);
            if (onCallMember != null) {
                alert.setAssignee(onCallMember);
                addTimelineEntry(alert, TimelineEventType.ASSIGNED,
                    "Auto-assigned to " + onCallMember.getName() + " (on-call)", "SYSTEM");
            } else {
                addTimelineEntry(alert, TimelineEventType.ASSIGNED,
                    "Assigned to team: " + responsibleTeam.getName(), "SYSTEM");
            }

            alertRepository.save(alert);
        } else {
            alert.setStatus(AlertStatus.UNASSIGNED);
            alertRepository.save(alert);
            log.warn("No responsible team found for alert: {}", alert.getId());
        }
    }

    // ========== Manual Assignment ==========
    @Transactional
    public Alert assignAlert(Long alertId, Long teamMemberId, String assignedBy) {
        Alert alert = getAlertById(alertId);
        TeamMember member = teamService.getMemberById(teamMemberId);

        TeamMember previousAssignee = alert.getAssignee();
        
        alert.setAssignee(member);
        alert.setAssignedTeam(member.getTeam());
        alert.setAssignedAt(Instant.now());
        alert.setAssignedBy(assignedBy);
        
        if (alert.getStatus() == AlertStatus.UNASSIGNED || 
            alert.getStatus() == AlertStatus.TRIGGERED) {
            alert.setStatus(AlertStatus.ASSIGNED);
        }

        String description = previousAssignee != null ?
            "Reassigned from " + previousAssignee.getName() + " to " + member.getName() :
            "Assigned to " + member.getName();

        addTimelineEntry(alert, 
            previousAssignee != null ? TimelineEventType.REASSIGNED : TimelineEventType.ASSIGNED,
            description, assignedBy);

        // Notify the assignee
        notificationService.notifyAssignment(alert, member);

        return alertRepository.save(alert);
    }

    // ========== Acknowledgment ==========
    @Transactional
    public Alert acknowledgeAlert(Long alertId, String acknowledgedBy) {
        Alert alert = getAlertById(alertId);
        
        if (alert.getStatus() == AlertStatus.RESOLVED || 
            alert.getStatus() == AlertStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Alert already " + alert.getStatus());
        }

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(Instant.now());
        alert.setAcknowledgedBy(acknowledgedBy);

        addTimelineEntry(alert, TimelineEventType.ACKNOWLEDGED,
            "Alert acknowledged", acknowledgedBy);

        return alertRepository.save(alert);
    }

    // ========== Status Updates ==========
    @Transactional
    public Alert updateStatus(Long alertId, AlertStatus newStatus, String updatedBy) {
        Alert alert = getAlertById(alertId);
        AlertStatus previousStatus = alert.getStatus();
        
        alert.setStatus(newStatus);
        
        addTimelineEntry(alert, TimelineEventType.STATUS_CHANGED,
            "Status changed from " + previousStatus + " to " + newStatus, updatedBy);

        return alertRepository.save(alert);
    }

    // ========== Resolution ==========
    @Transactional
    public Alert resolveAlert(Long alertId, String resolvedBy, 
                              ResolutionType resolutionType, String notes) {
        Alert alert = getAlertById(alertId);

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy(resolvedBy);
        alert.setResolutionType(resolutionType);
        alert.setResolutionNotes(notes);

        addTimelineEntry(alert, TimelineEventType.RESOLVED,
            "Resolved: " + resolutionType + (notes != null ? " - " + notes : ""), 
            resolvedBy);

        // Clear deduplication to allow new alerts
        deduplicationService.clearDeduplication(alert.getDeduplicationKey());

        return alertRepository.save(alert);
    }

    // ========== Comments ==========
    @Transactional
    public AlertComment addComment(Long alertId, String content, 
                                   String authorId, String authorName, boolean isInternal) {
        Alert alert = getAlertById(alertId);

        AlertComment comment = AlertComment.builder()
            .alert(alert)
            .content(content)
            .authorId(authorId)
            .authorName(authorName)
            .isInternal(isInternal)
            .build();

        comment = commentRepository.save(comment);

        addTimelineEntry(alert, TimelineEventType.COMMENT_ADDED,
            "Comment added by " + authorName, authorId);

        return comment;
    }

    // ========== Notifications ==========
    private void sendNotifications(Alert alert, String channels) {
        try {
            List<String> channelList = channels != null ? 
                Arrays.asList(channels.split(",")) : List.of("slack");

            // Notify operations team
            notificationService.notifyOperationsTeam(alert, channelList);

            // Notify assigned team/member
            if (alert.getAssignedTeam() != null) {
                notificationService.notifyTeam(alert, alert.getAssignedTeam(), channelList);
            }

            // For high severity, also notify leads/admins
            if (alert.getSeverity() == AlertRule.Severity.HIGH || 
                alert.getSeverity() == AlertRule.Severity.CRITICAL) {
                notificationService.notifyLeadership(alert, channelList);
            }

            alert.setStatus(AlertStatus.NOTIFIED);
            alert.setFirstNotifiedAt(Instant.now());
            alert.setLastNotifiedAt(Instant.now());
            alert.setNotificationCount(alert.getNotificationCount() + 1);

            addTimelineEntry(alert, TimelineEventType.NOTIFICATION_SENT,
                "Notifications sent to: " + String.join(", ", channelList), "SYSTEM");

            alertRepository.save(alert);

        } catch (Exception e) {
            log.error("Failed to send notifications for alert {}: {}", alert.getId(), e.getMessage());
            alert.setStatus(AlertStatus.NOTIFICATION_FAILED);
            alertRepository.save(alert);
        }
    }

    // ========== Helper Methods ==========
    private Alert getAlertById(Long alertId) {
        return alertRepository.findById(alertId)
            .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
    }

    private void addTimelineEntry(Alert alert, TimelineEventType eventType, 
                                  String description, String performedBy) {
        AlertTimeline timeline = AlertTimeline.builder()
            .alert(alert)
            .eventType(eventType)
            .description(description)
            .performedBy(performedBy)
            .build();
        
        timelineRepository.save(timeline);
    }

    private String buildAggregateDeduplicationKey(AlertRule rule) {
        return String.format("agg:%s:%s:%d", rule.getAppId(), rule.getFeature(), rule.getId());
    }

    private String buildUserDeduplicationKey(UserAlertRule rule, String userId) {
        return String.format("user:%s:%s:%d:%s", 
            rule.getAppId(), rule.getFeature(), rule.getId(), userId);
    }

    private int estimateAffectedUsers(AggregatedMetrics metrics) {
        // Estimate based on failure rate
        return (int) (metrics.getTotalCount() * (metrics.getFailureRate() / 100));
    }

    private String buildAggregateAlertMessage(AlertRule rule, AggregatedMetrics metrics, double value) {
        return String.format(
            "🚨 %s Alert: %s\n" +
            "Feature: %s\n" +
            "Metric: %s = %.2f%% (threshold: %.2f%%)\n" +
            "Affected users (est.): %d\n" +
            "Sample: %d events in %d seconds",
            rule.getSeverity(), rule.getName(), rule.getFeature(),
            rule.getMetricType(), value, rule.getThreshold(),
            estimateAffectedUsers(metrics),
            metrics.getTotalCount(), metrics.getWindowSeconds()
        );
    }

    private String buildUserAlertMessage(UserAlertRule rule, UserMetrics metrics) {
        return String.format(
            "🚨 User Alert: %s\n" +
            "User: %s\n" +
            "Feature: %s\n" +
            "Consecutive failures: %d (max: %d)\n" +
            "Failures in window: %d (max: %d in %ds)",
            rule.getName(), metrics.getUserId(), rule.getFeature(),
            metrics.getConsecutiveFailures(), rule.getMaxConsecutiveFailures(),
            metrics.getFailuresInWindow(), rule.getMaxFailuresInWindow(), 
            rule.getWindowSeconds()
        );
    }

    // ========== Query Methods ==========
    public List<Alert> getActiveAlerts(String appId) {
        return alertRepository.findByAppIdAndStatusIn(appId, 
            List.of(AlertStatus.TRIGGERED, AlertStatus.NOTIFIED, 
                    AlertStatus.ASSIGNED, AlertStatus.ACKNOWLEDGED, 
                    AlertStatus.IN_PROGRESS));
    }

    public List<Alert> getAlertsByAssignee(Long memberId) {
        return alertRepository.findByAssigneeIdAndStatusNot(memberId, AlertStatus.RESOLVED);
    }

    public List<Alert> getUnassignedAlerts() {
        return alertRepository.findByStatus(AlertStatus.UNASSIGNED);
    }
}