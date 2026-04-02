package com.alerting.platform.alerts.model;

import com.alerting.platform.rules.model.AlertRule.Severity;
import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_app_feature", columnList = "appId, feature"),
    @Index(name = "idx_alert_status", columnList = "status"),
    @Index(name = "idx_alert_assignee", columnList = "assignee_id"),
    @Index(name = "idx_alert_triggered", columnList = "triggeredAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Alert Identification ==========
    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private Long ruleId;

    @Column(nullable = false)
    private String ruleName;

    // ========== Alert Type ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType alertType;  // USER_LEVEL or AGGREGATE

    // For user-level alerts
    private String affectedUserId;
    private String affectedSessionId;

    // For aggregate alerts
    private Integer affectedUserCount;

    // ========== Alert Details ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false)
    private Double metricValue;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    private Long sampleSize;

    // ========== Status & Workflow ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false)
    private Instant triggeredAt;

    private Instant firstNotifiedAt;
    private Instant lastNotifiedAt;
    private Integer notificationCount;

    // ========== Assignment ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_team_id")
    private Team assignedTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private TeamMember assignee;

    private Instant assignedAt;
    private String assignedBy;

    // ========== Acknowledgment ==========
    private Instant acknowledgedAt;
    private String acknowledgedBy;

    // ========== Resolution ==========
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionNotes;

    @Enumerated(EnumType.STRING)
    private ResolutionType resolutionType;

    // ========== Auto-Resolution Check ==========
    private Instant lastAutoCheckAt;
    private Integer autoCheckCount;
    private Boolean autoResolved;

    // ========== Escalation ==========
    private Integer escalationLevel;
    private Instant lastEscalatedAt;

    // ========== Timeline & Comments ==========
    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<AlertTimeline> timeline = new ArrayList<>();

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<AlertComment> comments = new ArrayList<>();

    // ========== Deduplication ==========
    @Column(nullable = false)
    private String deduplicationKey;

    private Instant suppressedUntil;

    // ========== Metrics ==========
    public Duration getTimeToAcknowledge() {
        if (acknowledgedAt == null) return null;
        return Duration.between(triggeredAt, acknowledgedAt);
    }

    public Duration getTimeToResolve() {
        if (resolvedAt == null) return null;
        return Duration.between(triggeredAt, resolvedAt);
    }

    @PrePersist
    protected void onCreate() {
        if (triggeredAt == null) triggeredAt = Instant.now();
        if (status == null) status = AlertStatus.TRIGGERED;
        if (notificationCount == null) notificationCount = 0;
        if (escalationLevel == null) escalationLevel = 0;
        if (autoCheckCount == null) autoCheckCount = 0;
        if (autoResolved == null) autoResolved = false;
        generateDeduplicationKey();
    }

    private void generateDeduplicationKey() {
        if (alertType == AlertType.USER_LEVEL) {
            this.deduplicationKey = String.format("%s:%s:%s:%d:%s",
                appId, feature, alertType, ruleId, affectedUserId);
        } else {
            this.deduplicationKey = String.format("%s:%s:%s:%d",
                appId, feature, alertType, ruleId);
        }
    }

    public enum AlertType {
        USER_LEVEL,    // Single user experiencing issues
        AGGREGATE      // Multiple users / system-wide
    }

    public enum ResolutionType {
        MANUAL,        // Resolved by team member
        AUTO_RESOLVED, // System detected issue fixed
        FALSE_POSITIVE,// Marked as not a real issue
        DUPLICATE,     // Merged with another alert
        TIMEOUT        // Auto-closed after SLA
    }
}