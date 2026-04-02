package com.alerting.platform.alerts.model;

import com.alerting.platform.rules.model.AlertRule.Severity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private Long ruleId;

    @Column(nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private Double metricValue;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    private Long sampleSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false)
    private Instant triggeredAt;

    private Instant acknowledgedAt;
    private String acknowledgedBy;
    
    private Instant resolvedAt;
    private String resolvedBy;

    private String notificationsSent; // JSON array of channels
}

