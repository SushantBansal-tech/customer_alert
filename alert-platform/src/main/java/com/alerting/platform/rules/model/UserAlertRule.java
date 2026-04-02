package com.alerting.platform.rules.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user_alert_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private String name;

    // User-level specific thresholds
    @Column(nullable = false)
    private Integer maxConsecutiveFailures;  // e.g., 3 failures in a row

    @Column(nullable = false)
    private Integer maxFailuresInWindow;     // e.g., 5 failures in 10 minutes

    @Column(nullable = false)
    private Integer windowSeconds;           // Time window

    @Column(nullable = false)
    private Long maxLatencyMs;               // e.g., > 5000ms response time

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertRule.Severity severity;

    @Column(nullable = false)
    private boolean enabled = true;

    private String notificationChannels;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}