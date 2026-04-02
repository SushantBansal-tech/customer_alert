package com.alerting.platform.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AlertSummaryDTO {
    private String appId;
    private long totalActive;
    private long triggered;
    private long notified;
    private long assigned;
    private long acknowledged;
    private long inProgress;
    private long escalated;
    private long resolved;
    private long autoResolved;
}