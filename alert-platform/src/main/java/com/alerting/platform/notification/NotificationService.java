package com.alerting.platform.notification;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import com.alerting.platform.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SlackNotifier slackNotifier;
    private final EmailNotifier emailNotifier;
    private final TeamService teamService;

    @Value("${alerting.notifications.slack.ops-channel:#ops-alerts}")
    private String opsSlackChannel;

    @Value("${alerting.notifications.slack.leadership-channel:#leadership-alerts}")
    private String leadershipSlackChannel;

    @Value("${alerting.notifications.email.ops-recipients:}")
    private String opsEmailRecipients;

    @Value("${alerting.notifications.email.leadership-recipients:}")
    private String leadershipEmailRecipients;

    // ========== Main Notification Methods ==========

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

    // ========== Operations Team Notification ==========

    public void notifyOperationsTeam(Alert alert, List<String> channels) {
        log.info("Notifying operations team for alert {}", alert.getId());

        if (channels.contains("slack")) {
            slackNotifier.sendToChannel(opsSlackChannel, buildOpsMessage(alert));
        }

        if (channels.contains("email") && !opsEmailRecipients.isBlank()) {
            emailNotifier.sendToRecipients(
                opsEmailRecipients.split(","),
                buildEmailSubject(alert, "OPS"),
                buildOpsMessage(alert)
            );
        }
    }

    // ========== Team Notification ==========

    public void notifyTeam(Alert alert, Team team, List<String> channels) {
        log.info("Notifying team {} for alert {}", team.getName(), alert.getId());

        if (channels.contains("slack") && team.getSlackChannel() != null) {
            slackNotifier.sendToChannel(team.getSlackChannel(), buildTeamMessage(alert, team));
        }

        if (channels.contains("email") && team.getEmailDistribution() != null) {
            emailNotifier.sendToRecipients(
                team.getEmailDistribution().split(","),
                buildEmailSubject(alert, team.getName()),
                buildTeamMessage(alert, team)
            );
        }
    }

    // ========== Leadership Notification (High/Critical Severity) ==========

    public void notifyLeadership(Alert alert, List<String> channels) {
        log.info("Notifying leadership for alert {} (severity: {})", 
            alert.getId(), alert.getSeverity());

        String message = buildLeadershipMessage(alert);

        if (channels.contains("slack")) {
            slackNotifier.sendToChannel(leadershipSlackChannel, message);
        }

        if (channels.contains("email") && !leadershipEmailRecipients.isBlank()) {
            emailNotifier.sendToRecipients(
                leadershipEmailRecipients.split(","),
                buildEmailSubject(alert, "LEADERSHIP"),
                message
            );
        }

        // Also notify team leads directly
        if (alert.getAssignedTeam() != null) {
            List<TeamMember> leads = teamService.getTeamLeads(alert.getAssignedTeam());
            for (TeamMember lead : leads) {
                notifyMemberDirectly(lead, alert, "Leadership Alert");
            }
        }
    }

    // ========== Individual Assignment Notification ==========

    public void notifyAssignment(Alert alert, TeamMember assignee) {
        log.info("Notifying {} about assignment of alert {}", assignee.getName(), alert.getId());

        String message = buildAssignmentMessage(alert, assignee);

        // Slack DM
        if (assignee.getSlackUserId() != null) {
            slackNotifier.sendDirectMessage(assignee.getSlackUserId(), message);
        }

        // Email
        if (assignee.getEmail() != null) {
            emailNotifier.sendToRecipients(
                new String[]{assignee.getEmail()},
                "Alert Assigned: " + alert.getRuleName(),
                message
            );
        }
    }

    // ========== Direct Member Notification ==========

    public void notifyMemberDirectly(TeamMember member, Alert alert, String context) {
        String message = String.format("[%s] %s\n\n%s", 
            context, alert.getRuleName(), alert.getMessage());

        if (member.getSlackUserId() != null) {
            slackNotifier.sendDirectMessage(member.getSlackUserId(), message);
        }

        if (member.getEmail() != null) {
            emailNotifier.sendToRecipients(
                new String[]{member.getEmail()},
                "[" + context + "] " + alert.getRuleName(),
                message
            );
        }
    }

    // ========== Message Builders ==========

    private String buildOpsMessage(Alert alert) {
        return String.format(
            "🚨 *%s Alert* - %s\n\n" +
            "*App:* %s\n" +
            "*Feature:* %s\n" +
            "*Type:* %s\n" +
            "*Metric:* %.2f (threshold: %.2f)\n" +
            "*Status:* %s\n" +
            "*Triggered:* %s\n" +
            "%s\n\n" +
            "_Alert ID: %d_",
            alert.getSeverity(),
            alert.getRuleName(),
            alert.getAppId(),
            alert.getFeature(),
            alert.getAlertType(),
            alert.getMetricValue(),
            alert.getThreshold(),
            alert.getStatus(),
            alert.getTriggeredAt(),
            alert.getAffectedUserId() != null ? 
                "*Affected User:* " + alert.getAffectedUserId() : 
                "*Affected Users (est):* " + alert.getAffectedUserCount(),
            alert.getId()
        );
    }

    private String buildTeamMessage(Alert alert, Team team) {
        return String.format(
            "📢 *Alert Assigned to %s*\n\n" +
            "*Alert:* %s\n" +
            "*Severity:* %s\n" +
            "*App:* %s\n" +
            "*Feature:* %s\n" +
            "*Status:* %s\n\n" +
            "%s\n\n" +
            "_Please acknowledge and investigate._",
            team.getName(),
            alert.getRuleName(),
            alert.getSeverity(),
            alert.getAppId(),
            alert.getFeature(),
            alert.getStatus(),
            alert.getMessage()
        );
    }

    private String buildLeadershipMessage(Alert alert) {
        return String.format(
            "🔴 *LEADERSHIP ALERT* 🔴\n\n" +
            "*Severity:* %s\n" +
            "*Alert:* %s\n" +
            "*App:* %s\n" +
            "*Feature:* %s\n" +
            "*Assigned Team:* %s\n" +
            "*Assigned To:* %s\n" +
            "*Status:* %s\n" +
            "*Time Since Triggered:* %s\n\n" +
            "%s",
            alert.getSeverity(),
            alert.getRuleName(),
            alert.getAppId(),
            alert.getFeature(),
            alert.getAssignedTeam() != null ? alert.getAssignedTeam().getName() : "Unassigned",
            alert.getAssignee() != null ? alert.getAssignee().getName() : "Unassigned",
            alert.getStatus(),
            formatDuration(alert.getTriggeredAt()),
            alert.getMessage()
        );
    }

    private String buildAssignmentMessage(Alert alert, TeamMember assignee) {
        return String.format(
            "👋 Hi %s,\n\n" +
            "You've been assigned to handle the following alert:\n\n" +
            "*Alert:* %s\n" +
            "*Severity:* %s\n" +
            "*App:* %s\n" +
            "*Feature:* %s\n\n" +
            "*Details:*\n%s\n\n" +
            "_Please acknowledge this alert and begin investigation._",
            assignee.getName(),
            alert.getRuleName(),
            alert.getSeverity(),
            alert.getAppId(),
            alert.getFeature(),
            alert.getMessage()
        );
    }

    private String buildEmailSubject(Alert alert, String prefix) {
        return String.format("[%s][%s] %s - %s",
            prefix,
            alert.getSeverity(),
            alert.getAppId(),
            alert.getRuleName()
        );
    }

    private String formatDuration(java.time.Instant since) {
        java.time.Duration duration = java.time.Duration.between(since, java.time.Instant.now());
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + " minutes";
    }
}