package com.alerting.platform.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ResolutionProgressDTO {
    private Long alertId;
    private String ruleName;
    private String severity;
    private String status;
    
    // Timeline
    private Instant triggeredAt;
    private String assignedTeam;
    private String assignee;
    private Instant assignedAt;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private String resolvedBy;
    private Instant resolvedAt;
    private String resolutionType;
    private String resolutionNotes;
    
    // Metrics
    private long timeSinceTriggeredMinutes;
    private Long timeToAcknowledgeMinutes;
    private Long timeToResolveMinutes;
    
    // Auto-resolution tracking
    private int escalationLevel;
    private int autoCheckCount;
    private Boolean isAutoResolved;
    
    // Activity
    private int commentCount;
}