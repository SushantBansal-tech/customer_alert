package com.alerting.platform.dashboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberWorkloadDTO {
    private Long memberId;
    private String memberName;
    private String role;
    private boolean isOnCall;
    private int activeAlerts;
    private int acknowledgedAlerts;
    private long oldestAlertAge; // in minutes
}