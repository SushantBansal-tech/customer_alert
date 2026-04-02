package com.alerting.platform.notification;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.rules.model.AlertRule.Severity;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Attachment;
import com.slack.api.model.Field;
import com.slack.api.webhook.Payload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Component
@Slf4j
public class SlackNotifier {

    @Value("${alerting.notifications.slack.enabled:true}")
    private boolean enabled;

    @Value("${alerting.notifications.slack.webhook-url:}")
    private String webhookUrl;

    @Value("${alerting.notifications.slack.bot-token:}")
    private String botToken;

    @Value("${alerting.notifications.slack.channel:#alerts}")
    private String defaultChannel;

    private final Slack slack = Slack.getInstance();
    private MethodsClient methodsClient;

    @PostConstruct
    public void init() {
        if (enabled && botToken != null && !botToken.isBlank()) {
            methodsClient = slack.methods(botToken);
            log.info("Slack notifier initialized with bot token");
        } else if (enabled && webhookUrl != null && !webhookUrl.isBlank()) {
            log.info("Slack notifier initialized with webhook");
        } else {
            log.warn("Slack notifier not fully configured");
        }
    }

    // ========== Main Send Method ==========

    public boolean send(Alert alert) {
        return sendToChannel(defaultChannel, buildPayload(alert));
    }

    // ========== Channel Message ==========

    public boolean sendToChannel(String channel, String message) {
        if (!enabled) {
            log.debug("Slack notifications disabled");
            return false;
        }

        try {
            if (methodsClient != null) {
                return sendViaApi(channel, message);
            } else if (webhookUrl != null && !webhookUrl.isBlank()) {
                return sendViaWebhook(message);
            } else {
                log.warn("No Slack configuration available");
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending Slack message: {}", e.getMessage());
            return false;
        }
    }

    public boolean sendToChannel(String channel, Payload payload) {
        if (!enabled || (webhookUrl == null || webhookUrl.isBlank())) {
            return false;
        }

        try {
            payload = Payload.builder()
                .channel(channel)
                .username(payload.getUsername())
                .iconEmoji(payload.getIconEmoji())
                .attachments(payload.getAttachments())
                .text(payload.getText())
                .build();

            var response = slack.send(webhookUrl, payload);
            return response.getCode() == 200;
        } catch (Exception e) {
            log.error("Error sending Slack payload: {}", e.getMessage());
            return false;
        }
    }

    // ========== Direct Message ==========

    public boolean sendDirectMessage(String slackUserId, String message) {
        if (!enabled || methodsClient == null) {
            log.debug("Slack DM not available (no bot token)");
            return false;
        }

        try {
            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(slackUserId)
                .text(message)
                .build();

            ChatPostMessageResponse response = methodsClient.chatPostMessage(request);
            
            if (response.isOk()) {
                log.debug("Slack DM sent to user {}", slackUserId);
                return true;
            } else {
                log.error("Slack DM failed: {}", response.getError());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending Slack DM: {}", e.getMessage());
            return false;
        }
    }

    // ========== Internal Methods ==========

    private boolean sendViaApi(String channel, String message) throws Exception {
        ChatPostMessageRequest request = ChatPostMessageRequest.builder()
            .channel(channel)
            .text(message)
            .build();

        ChatPostMessageResponse response = methodsClient.chatPostMessage(request);
        
        if (response.isOk()) {
            log.debug("Slack message sent to channel {}", channel);
            return true;
        } else {
            log.error("Slack API error: {}", response.getError());
            return false;
        }
    }

    private boolean sendViaWebhook(String message) throws Exception {
        Payload payload = Payload.builder()
            .channel(defaultChannel)
            .username("Alert Bot")
            .iconEmoji(":rotating_light:")
            .text(message)
            .build();

        var response = slack.send(webhookUrl, payload);
        return response.getCode() == 200;
    }

    // ========== Payload Builder ==========

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
                    .title("Status")
                    .value(alert.getStatus().name())
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
                    .title("Assigned To")
                    .value(alert.getAssignee() != null ? 
                        alert.getAssignee().getName() : "Unassigned")
                    .valueShortEnough(true)
                    .build()
            ))
            .footer("Alert Platform | ID: " + alert.getId())
            .ts(String.valueOf(alert.getTriggeredAt().getEpochSecond()))
            .build();

        return Payload.builder()
            .channel(defaultChannel)
            .username("Alert Bot")
            .iconEmoji(":rotating_light:")
            .attachments(List.of(attachment))
            .build();
    }

    private String getSeverityColor(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "#FF0000";
            case HIGH -> "#FF6600";
            case MEDIUM -> "#FFCC00";
            case LOW -> "#00CC00";
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