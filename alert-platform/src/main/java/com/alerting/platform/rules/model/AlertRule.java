package com.alerting.platform.rules.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "alert_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetricType metricType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operator operator;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    private Integer windowSeconds;

    @Column(nullable = false)
    private Integer minSampleSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled = true;

    private String notificationChannels; // comma-separated: slack,email

    private Instant createdAt;
    private Instant updatedAt;

    public enum MetricType {
        FAILURE_RATE,
        FAILURE_COUNT,
        LATENCY_P50,
        LATENCY_P99,
        ERROR_RATE
    }

    public enum Operator {
        GREATER_THAN,
        LESS_THAN,
        EQUALS,
        NOT_EQUALS
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

