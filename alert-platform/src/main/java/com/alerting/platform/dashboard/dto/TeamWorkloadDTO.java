package com.alerting.platform.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TeamWorkloadDTO {
    private Long teamId;
    private String teamName;
    private int totalActiveAlerts;
    private int unacknowledgedAlerts;
    private int unassignedAlerts;
    private int escalatedAlerts;
    private List<MemberWorkloadDTO> memberWorkloads;
}