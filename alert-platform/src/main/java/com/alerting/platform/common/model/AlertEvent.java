package com.alerting.platform.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {
    
    private String appId;
    private String userId;
    private String sessionId;
    private String feature;
    private String eventType;
    private EventOutcome outcome;
    private Long latencyMs;
    private Instant timestamp;
    private Map<String, String> metadata;
    
    public enum EventOutcome {
        SUCCESS,
        FAILURE
    }
}

