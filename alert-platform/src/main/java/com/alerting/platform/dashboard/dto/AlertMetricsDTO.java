package com.alerting.platform.dashboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertMetricsDTO {
    private int periodHours;
    private Double avgTimeToAcknowledgeMinutes;
    private Double avgTimeToResolveMinutes;
    private Long totalAlerts;
    private Long resolvedAlerts;
    private Long autoResolvedAlerts;
    private Double resolutionRate;
}