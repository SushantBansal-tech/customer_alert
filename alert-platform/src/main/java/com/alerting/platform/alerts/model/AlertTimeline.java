package com.alerting.platform.alerts.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "alert_timeline")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimelineEventType eventType;

    @Column(nullable = false)
    private String description;

    private String performedBy;  // User or "SYSTEM"

    @Column(nullable = false)
    private Instant createdAt;

    // Additional context as JSON
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum TimelineEventType {
        CREATED,
        STATUS_CHANGED,
        ASSIGNED,
        REASSIGNED,
        ACKNOWLEDGED,
        ESCALATED,
        NOTIFICATION_SENT,
        COMMENT_ADDED,
        AUTO_CHECK_PERFORMED,
        RESOLVED,
        REOPENED
    }
}