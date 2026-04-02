package com.alerting.platform.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class AlertBriefDTO {
    private Long id;
    private String ruleName;
    private String appId;
    private String feature;
    private String severity;
    private String status;
    private Instant triggeredAt;
    private Instant acknowledgedAt;
    private long ageMinutes;
}