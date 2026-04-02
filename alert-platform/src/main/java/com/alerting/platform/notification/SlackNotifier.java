package com.alerting.platform.notification;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.rules.model.AlertRule.Severity;
import com.slack.api.Slack;
import com.slack.api.webhook.Payload;
import com.slack.api.model.Attachment;
import com.slack.api.model.Field;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SlackNotifier {

    @Value("${alerting.notifications.slack.enabled}")
    private boolean enabled;

    @Value("${alerting.notifications.slack.webhook-url:}")
    private String webhookUrl;

    @Value("${alerting.notifications.slack.channel}")
    private String channel;

    private final Slack slack = Slack.getInstance();

    public boolean send(Alert alert) {
        if (!enabled || webhookUrl.isBlank()) {
            log.debug("Slack notifications disabled");
            return false;
        }

        try {
            Payload payload = buildPayload(alert);
            var response = slack.send(webhookUrl, payload);

            if (response.getCode() == 200) {
                log.info("Slack notification sent for alert {}", alert.getId());
                return true;
            } else {
                log.error("Slack notification failed: {} - {}", response.getCode(), response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending Slack notification: {}", e.getMessage());
            return false;
        }
    }

    private Payload buildPayload(Alert alert) {
        String color = getSeverityColor(alert.getSeverity());
        String emoji = getSeverityEmoji(alert.getSeverity());

        Attachment attachment = Attachment.builder()
            .color(color)
            .title(emoji + " " + alert.getRuleName())
            .text(alert.getMessage())
            .fields(List.of(
                Field.builder()
                    .title("App")
                    .value(alert.getAppId())
                    .valueShortEnough(true)
                    .build(),
                Field.builder()
                    .title("Feature")
                    .value(alert.getFeature())
                    .valueShortEnough(true)
                    .build(),
                Field.builder()
                    .title("Severity")
                    .value(alert.getSeverity().name())
                    .valueShortEnough(true)
                    .build(),
                Field.builder()
                    .title("Metric Value")
                    .value(String.format("%.2f", alert.getMetricValue()))
                    .valueShortEnough(true)
                    .build(),
                Field.builder()
                    .title("Threshold")
                    .value(String.format("%.2f", alert.getThreshold()))
                    .valueShortEnough(true)
                    .build(),
                Field.builder()
                    .title("Sample Size")
                    .value(String.valueOf(alert.getSampleSize()))
                    .valueShortEnough(true)
                    .build()
            ))
            .footer("Alert Platform")
            .ts(String.valueOf(alert.getTriggeredAt().getEpochSecond()))
            .build();

        return Payload.builder()
            .channel(channel)
            .username("Alert Bot")
            .iconEmoji(":rotating_light:")
            .attachments(List.of(attachment))
            .build();
    }

    private String getSeverityColor(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "#FF0000"; // Red
            case HIGH -> "#FF6600";     // Orange
            case MEDIUM -> "#FFCC00";   // Yellow
            case LOW -> "#00CC00";      // Green
        };
    }

    private String getSeverityEmoji(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "🔴";
            case HIGH -> "🟠";
            case MEDIUM -> "🟡";
            case LOW -> "🟢";
        };
    }
}

