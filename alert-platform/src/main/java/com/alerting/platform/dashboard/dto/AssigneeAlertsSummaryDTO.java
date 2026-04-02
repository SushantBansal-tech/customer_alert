package com.alerting.platform.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AssigneeAlertsSummaryDTO {
    private Long memberId;
    private String memberName;
    private String email;
    private String role;
    private boolean isOnCall;
    private List<AlertBriefDTO> alerts;
}