package com.alerting.platform.notification;

import com.alerting.platform.alerts.model.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SlackNotifier slackNotifier;
    private final EmailNotifier emailNotifier;

    public String sendNotifications(Alert alert, List<String> channels) {
        List<String> sentChannels = new ArrayList<>();

        for (String channel : channels) {
            try {
                boolean sent = switch (channel.toLowerCase().trim()) {
                    case "slack" -> slackNotifier.send(alert);
                    case "email" -> emailNotifier.send(alert);
                    default -> {
                        log.warn("Unknown notification channel: {}", channel);
                        yield false;
                    }
                };

                if (sent) {
                    sentChannels.add(channel);
                }
            } catch (Exception e) {
                log.error("Failed to send {} notification for alert {}: {}", 
                    channel, alert.getId(), e.getMessage());
            }
        }

        return String.join(",", sentChannels);
    }
}

