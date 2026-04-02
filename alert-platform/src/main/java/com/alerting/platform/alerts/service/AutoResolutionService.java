package com.alerting.platform.alerts.service;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.Alert.ResolutionType;
import com.alerting.platform.alerts.model.AlertStatus;
import com.alerting.platform.alerts.model.AlertTimeline.TimelineEventType;
import com.alerting.platform.alerts.repository.AlertRepository;
import com.alerting.platform.alerts.repository.AlertTimelineRepository;
import com.alerting.platform.processing.service.AggregationService;
import com.alerting.platform.processing.service.UserLevelAggregationService;
import com.alerting.platform.rules.model.AlertRule;
import com.alerting.platform.rules.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoResolutionService {

    private final AlertRepository alertRepository;
    private final AlertTimelineRepository timelineRepository;
    private final AlertRuleRepository ruleRepository;
    private final AggregationService aggregationService;
    private final UserLevelAggregationService userAggregationService;
    private final DeduplicationService deduplicationService;

    @Value("${alerting.auto-resolution.check-interval-minutes:5}")
    private int checkIntervalMinutes;

    @Value("${alerting.auto-resolution.consecutive-healthy-checks:3}")
    private int consecutiveHealthyChecks;

    @Value("${alerting.auto-resolution.max-age-hours:24}")
    private int maxAgeHours;

    /**
     * Periodically check if active alerts can be auto-resolved
     */
    @Scheduled(fixedRateString = "${alerting.auto-resolution.check-interval-minutes:5}000")
    @Transactional
    public void checkForAutoResolution() {
        log.debug("Starting auto-resolution check");

        List<Alert> activeAlerts = alertRepository.findActiveAlertsForAutoCheck(
            List.of(AlertStatus.TRIGGERED, AlertStatus.NOTIFIED, AlertStatus.ASSIGNED,
                    AlertStatus.ACKNOWLEDGED, AlertStatus.IN_PROGRESS)
        );

        for (Alert alert : activeAlerts) {
            try {
                checkAlert(alert);
            } catch (Exception e) {
                log.error("Error checking alert {} for auto-resolution: {}", 
                    alert.getId(), e.getMessage());
            }
        }
    }

    private void checkAlert(Alert alert) {
        // Check if issue is still occurring
        boolean issueResolved = isIssueResolved(alert);

        alert.setLastAutoCheckAt(Instant.now());
        alert.setAutoCheckCount(alert.getAutoCheckCount() + 1);

        if (issueResolved) {
            // Issue seems resolved
            handlePotentialResolution(alert);
        } else {
            // Issue still ongoing
            handleOngoingIssue(alert);
        }

        alertRepository.save(alert);
    }

    private boolean isIssueResolved(Alert alert) {
        if (alert.getAlertType() == Alert.AlertType.AGGREGATE) {
            return isAggregateIssueResolved(alert);
        } else {
            return isUserLevelIssueResolved(alert);
        }
    }

    private boolean isAggregateIssueResolved(Alert alert) {
        AlertRule rule = ruleRepository.findById(alert.getRuleId()).orElse(null);
        if (rule == null) {
            log.warn("Rule not found for alert: {}", alert.getId());
            return false;
        }

        var metrics = aggregationService.getMetrics(alert.getAppId(), alert.getFeature());

        // Check if current metrics are below threshold
        double currentValue = switch (rule.getMetricType()) {
            case FAILURE_RATE -> metrics.getFailureRate();
            case FAILURE_COUNT -> metrics.getFailureCount();
            default -> 0;
        };

        // Consider resolved if current value is significantly below threshold
        double safetyMargin = 0.7;  // Must be 30% below threshold
        return currentValue < (rule.getThreshold() * safetyMargin);
    }

    private boolean isUserLevelIssueResolved(Alert alert) {
        if (alert.getAffectedUserId() == null) return false;

        var metrics = userAggregationService.getUserMetrics(
            alert.getAppId(), 
            alert.getFeature(), 
            alert.getAffectedUserId()
        );

        // User level resolved if no recent failures
        return metrics.getConsecutiveFailures() == 0 && 
               metrics.getFailuresInWindow() == 0;
    }

    private void handlePotentialResolution(Alert alert) {
        // Track consecutive healthy checks in metadata or separate field
        Integer healthyChecks = getHealthyCheckCount(alert);
        healthyChecks++;

        if (healthyChecks >= consecutiveHealthyChecks) {
            // Auto-resolve
            autoResolve(alert);
        } else {
            // Update pending verification
            if (alert.getStatus() != AlertStatus.PENDING_VERIFICATION) {
                alert.setStatus(AlertStatus.PENDING_VERIFICATION);
                addTimelineEntry(alert, TimelineEventType.AUTO_CHECK_PERFORMED,
                    "Issue appears resolved. Verifying... (" + healthyChecks + "/" + 
                    consecutiveHealthyChecks + ")", "SYSTEM");
            }
            setHealthyCheckCount(alert, healthyChecks);
        }
    }

    private void autoResolve(Alert alert) {
        alert.setStatus(AlertStatus.AUTO_RESOLVED);
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy("SYSTEM");
        alert.setResolutionType(ResolutionType.AUTO_RESOLVED);
        alert.setAutoResolved(true);
        alert.setResolutionNotes("Automatically resolved: metrics returned to healthy levels");

        addTimelineEntry(alert, TimelineEventType.RESOLVED,
            "Auto-resolved: Issue no longer detected after " + consecutiveHealthyChecks + 
            " consecutive checks", "SYSTEM");

        deduplicationService.clearDeduplication(alert.getDeduplicationKey());

        log.info("Alert {} auto-resolved", alert.getId());
    }

    private void handleOngoingIssue(Alert alert) {
        // Reset healthy check count
        setHealthyCheckCount(alert, 0);

        // Check if we need to escalate
        checkForEscalation(alert);

        // Check if alert is too old
        checkForTimeout(alert);

        addTimelineEntry(alert, TimelineEventType.AUTO_CHECK_PERFORMED,
            "Issue still ongoing", "SYSTEM");
    }

    private void checkForEscalation(Alert alert) {
        // Escalation rules based on time without acknowledgment
        Duration sinceTriggered = Duration.between(alert.getTriggeredAt(), Instant.now());

        if (alert.getAcknowledgedAt() == null) {
            if (sinceTriggered.toMinutes() > 30 && alert.getEscalationLevel() < 1) {
                escalate(alert, 1, "No acknowledgment after 30 minutes");
            } else if (sinceTriggered.toMinutes() > 60 && alert.getEscalationLevel() < 2) {
                escalate(alert, 2, "No acknowledgment after 1 hour");
            }
        }
    }

    private void escalate(Alert alert, int level, String reason) {
        alert.setEscalationLevel(level);
        alert.setLastEscalatedAt(Instant.now());
        alert.setStatus(AlertStatus.ESCALATED);

        addTimelineEntry(alert, TimelineEventType.ESCALATED,
            "Escalated to level " + level + ": " + reason, "SYSTEM");

        // TODO: Trigger escalation notifications
        log.info("Alert {} escalated to level {}", alert.getId(), level);
    }

    private void checkForTimeout(Alert alert) {
        Duration age = Duration.between(alert.getTriggeredAt(), Instant.now());
        
        if (age.toHours() > maxAgeHours && 
            alert.getStatus() != AlertStatus.RESOLVED && 
            alert.getStatus() != AlertStatus.AUTO_RESOLVED) {
            
            alert.setStatus(AlertStatus.CLOSED);
            alert.setResolvedAt(Instant.now());
            alert.setResolvedBy("SYSTEM");
            alert.setResolutionType(ResolutionType.TIMEOUT);
            alert.setResolutionNotes("Auto-closed after " + maxAgeHours + " hours without resolution");

            addTimelineEntry(alert, TimelineEventType.RESOLVED,
                "Timed out: Alert closed after " + maxAgeHours + " hours", "SYSTEM");

            deduplicationService.clearDeduplication(alert.getDeduplicationKey());
        }
    }

    // Helper methods for tracking healthy check count (stored in Redis)
    private Integer getHealthyCheckCount(Alert alert) {
        // Implementation using Redis or alert metadata
        return 0;
    }

    private void setHealthyCheckCount(Alert alert, Integer count) {
        // Implementation using Redis or alert metadata
    }

    private void addTimelineEntry(Alert alert, TimelineEventType eventType, 
                                  String description, String performedBy) {
        var timeline = com.alerting.platform.alerts.model.AlertTimeline.builder()
            .alert(alert)
            .eventType(eventType)
            .description(description)
            .performedBy(performedBy)
            .build();
        timelineRepository.save(timeline);
    }
}