package com.alerting.platform.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AlertDetailDTO {
    private Long id;
    private String appId;
    private String feature;
    private String ruleName;
    private Long ruleId;
    
    // Type
    private String alertType;
    private String affectedUserId;
    private Integer affectedUserCount;
    
    // Details
    private String severity;
    private String message;
    private Double metricValue;
    private Double threshold;
    private Long sampleSize;
    
    // Status
    private String status;
    private Instant triggeredAt;
    
    // Assignment
    private TeamInfoDTO assignedTeam;
    private MemberInfoDTO assignee;
    private Instant assignedAt;
    private String assignedBy;
    
    // Acknowledgment
    private Instant acknowledgedAt;
    private String acknowledgedBy;
    
    // Resolution
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionType;
    private String resolutionNotes;
    
    // Escalation
    private int escalationLevel;
    private Instant lastEscalatedAt;
    
    // Auto-resolution
    private Instant lastAutoCheckAt;
    private Integer autoCheckCount;
    private Boolean autoResolved;
    
    // Timeline and Comments
    private List<TimelineEntryDTO> timeline;
    private List<CommentDTO> comments;
    
    @Data
    @Builder
    public static class TeamInfoDTO {
        private Long id;
        private String name;
        private String slackChannel;
    }
    
    @Data
    @Builder
    public static class MemberInfoDTO {
        private Long id;
        private String name;
        private String email;
        private String role;
        private boolean isOnCall;
    }
    
    @Data
    @Builder
    public static class TimelineEntryDTO {
        private Long id;
        private String eventType;
        private String description;
        private String performedBy;
        private Instant createdAt;
    }
    
    @Data
    @Builder
    public static class CommentDTO {
        private Long id;
        private String content;
        private String authorId;
        private String authorName;
        private boolean isInternal;
        private Instant createdAt;
    }
}