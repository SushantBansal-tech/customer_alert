package com.alerting.platform.alerts.service;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.AlertStatus;
import com.alerting.platform.alerts.repository.AlertRepository;
import com.alerting.platform.notification.NotificationService;
import com.alerting.platform.processing.service.AggregationService.AggregatedMetrics;
import com.alerting.platform.rules.model.AlertRule;
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
    private final DeduplicationService deduplicationService;
    private final NotificationService notificationService;

    @Transactional
    public void createAlert(AlertRule rule, AggregatedMetrics metrics, double actualValue) {
        // Check for duplicate
        if (deduplicationService.isDuplicate(rule.getAppId(), rule.getFeature(), rule.getId())) {
            log.debug("Suppressing duplicate alert for rule: {}", rule.getName());
            return;
        }

        // Build alert message
        String message = buildAlertMessage(rule, metrics, actualValue);

        // Create alert entity
        Alert alert = Alert.builder()
            .appId(rule.getAppId())
            .feature(rule.getFeature())
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .severity(rule.getSeverity())
            .message(message)
            .metricValue(actualValue)
            .threshold(rule.getThreshold())
            .sampleSize(metrics.getTotalCount())
            .status(AlertStatus.TRIGGERED)
            .triggeredAt(Instant.now())
            .build();

        alert = alertRepository.save(alert);
        log.info("Alert created: id={}, rule={}, severity={}", 
            alert.getId(), rule.getName(), rule.getSeverity());

        // Send notifications
        sendNotifications(alert, rule);

        // Mark as sent for deduplication
        deduplicationService.markAlertSent(alert);
    }

    private void sendNotifications(Alert alert, AlertRule rule) {
        try {
            List<String> channels = parseNotificationChannels(rule.getNotificationChannels());
            
            String sentChannels = notificationService.sendNotifications(alert, channels);
            
            alert.setStatus(AlertStatus.NOTIFIED);
            alert.setNotificationsSent(sentChannels);
            alertRepository.save(alert);
            
        } catch (Exception e) {
            log.error("Failed to send notifications for alert {}: {}", alert.getId(), e.getMessage());
        }
    }

    private List<String> parseNotificationChannels(String channels) {
        if (channels == null || channels.isBlank()) {
            return List.of("slack"); // Default channel
        }
        return Arrays.asList(channels.split(","));
    }

    private String buildAlertMessage(AlertRule rule, AggregatedMetrics metrics, double actualValue) {
        return String.format(
            "🚨 %s Alert: %s\\n" +
            "Feature: %s\\n" +
            "Metric: %s\\n" +
            "Threshold: %s %.2f\\n" +
            "Actual: %.2f\\n" +
            "Sample Size: %d events in %d seconds",
            rule.getSeverity(),
            rule.getName(),
            rule.getFeature(),
            rule.getMetricType(),
            rule.getOperator(),
            rule.getThreshold(),
            actualValue,
            metrics.getTotalCount(),
            metrics.getWindowSeconds()
        );
    }

    @Transactional
    public Alert acknowledgeAlert(Long alertId, String acknowledgedBy) {
        Alert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(Instant.now());
        alert.setAcknowledgedBy(acknowledgedBy);

        return alertRepository.save(alert);
    }

    @Transactional
    public Alert resolveAlert(Long alertId, String resolvedBy) {
        Alert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy(resolvedBy);

        return alertRepository.save(alert);
    }

    public List<Alert> getActiveAlerts(String appId) {
        return alertRepository.findByAppIdAndStatusIn(
            appId, 
            List.of(AlertStatus.TRIGGERED, AlertStatus.NOTIFIED, AlertStatus.ACKNOWLEDGED)
        );
    }

    public List<Alert> getRecentAlerts(int hours) {
        Instant since = Instant.now().minusSeconds(hours * 3600L);
        return alertRepository.findByStatusAndTriggeredAtAfter(AlertStatus.NOTIFIED, since);
    }
}

