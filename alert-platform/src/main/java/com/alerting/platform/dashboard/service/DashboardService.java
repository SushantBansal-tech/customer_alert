package com.alerting.platform.dashboard.service;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.AlertStatus;
import com.alerting.platform.alerts.repository.AlertRepository;
import com.alerting.platform.dashboard.dto.*;
import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import com.alerting.platform.team.repository.TeamMemberRepository;
import com.alerting.platform.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final AlertRepository alertRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;

    // ========== Alert Summary ==========

    public AlertSummaryDTO getAlertSummary(String appId) {
        List<Object[]> statusCounts = alertRepository.countByAppIdGroupByStatus(appId);
        
        Map<AlertStatus, Long> countByStatus = new HashMap<>();
        for (Object[] row : statusCounts) {
            countByStatus.put((AlertStatus) row[0], (Long) row[1]);
        }

        long activeCount = countByStatus.entrySet().stream()
            .filter(e -> !isTerminalStatus(e.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();

        return AlertSummaryDTO.builder()
            .appId(appId)
            .totalActive(activeCount)
            .triggered(countByStatus.getOrDefault(AlertStatus.TRIGGERED, 0L))
            .notified(countByStatus.getOrDefault(AlertStatus.NOTIFIED, 0L))
            .assigned(countByStatus.getOrDefault(AlertStatus.ASSIGNED, 0L))
            .acknowledged(countByStatus.getOrDefault(AlertStatus.ACKNOWLEDGED, 0L))
            .inProgress(countByStatus.getOrDefault(AlertStatus.IN_PROGRESS, 0L))
            .escalated(countByStatus.getOrDefault(AlertStatus.ESCALATED, 0L))
            .resolved(countByStatus.getOrDefault(AlertStatus.RESOLVED, 0L))
            .autoResolved(countByStatus.getOrDefault(AlertStatus.AUTO_RESOLVED, 0L))
            .build();
    }

    // ========== Team Workload ==========

    public List<TeamWorkloadDTO> getTeamWorkloads() {
        List<Team> teams = teamRepository.findByActiveTrue();
        List<TeamWorkloadDTO> workloads = new ArrayList<>();

        List<AlertStatus> activeStatuses = List.of(
            AlertStatus.TRIGGERED, AlertStatus.NOTIFIED, AlertStatus.ASSIGNED,
            AlertStatus.ACKNOWLEDGED, AlertStatus.IN_PROGRESS, AlertStatus.ESCALATED
        );

        for (Team team : teams) {
            List<Alert> teamAlerts = alertRepository.findByAssignedTeamAndStatusIn(team, activeStatuses);
            
            Map<TeamMember, List<Alert>> alertsByMember = teamAlerts.stream()
                .filter(a -> a.getAssignee() != null)
                .collect(Collectors.groupingBy(Alert::getAssignee));

            List<MemberWorkloadDTO> memberWorkloads = new ArrayList<>();
            for (TeamMember member : memberRepository.findByTeamAndActiveTrue(team)) {
                List<Alert> memberAlerts = alertsByMember.getOrDefault(member, List.of());
                
                memberWorkloads.add(MemberWorkloadDTO.builder()
                    .memberId(member.getId())
                    .memberName(member.getName())
                    .role(member.getRole().name())
                    .isOnCall(member.isOnCall())
                    .activeAlerts(memberAlerts.size())
                    .acknowledgedAlerts((int) memberAlerts.stream()
                        .filter(a -> a.getAcknowledgedAt() != null).count())
                    .oldestAlertAge(memberAlerts.stream()
                        .map(Alert::getTriggeredAt)
                        .min(Instant::compareTo)
                        .map(t -> Duration.between(t, Instant.now()).toMinutes())
                        .orElse(0L))
                    .build());
            }

            long unacknowledged = teamAlerts.stream()
                .filter(a -> a.getAcknowledgedAt() == null)
                .count();

            workloads.add(TeamWorkloadDTO.builder()
                .teamId(team.getId())
                .teamName(team.getName())
                .totalActiveAlerts(teamAlerts.size())
                .unacknowledgedAlerts((int) unacknowledged)
                .unassignedAlerts((int) teamAlerts.stream()
                    .filter(a -> a.getAssignee() == null).count())
                .escalatedAlerts((int) teamAlerts.stream()
                    .filter(a -> a.getStatus() == AlertStatus.ESCALATED).count())
                .memberWorkloads(memberWorkloads)
                .build());
        }

        return workloads;
    }

    // ========== Resolution Progress ==========

    public ResolutionProgressDTO getResolutionProgress(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));

        Duration timeSinceTriggered = Duration.between(alert.getTriggeredAt(), Instant.now());
        Duration timeToAcknowledge = alert.getAcknowledgedAt() != null ?
            Duration.between(alert.getTriggeredAt(), alert.getAcknowledgedAt()) : null;
        Duration timeToResolve = alert.getResolvedAt() != null ?
            Duration.between(alert.getTriggeredAt(), alert.getResolvedAt()) : null;

        return ResolutionProgressDTO.builder()
            .alertId(alert.getId())
            .ruleName(alert.getRuleName())
            .severity(alert.getSeverity().name())
            .status(alert.getStatus().name())
            .triggeredAt(alert.getTriggeredAt())
            .assignedTeam(alert.getAssignedTeam() != null ? alert.getAssignedTeam().getName() : null)
            .assignee(alert.getAssignee() != null ? alert.getAssignee().getName() : null)
            .assignedAt(alert.getAssignedAt())
            .acknowledgedBy(alert.getAcknowledgedBy())
            .acknowledgedAt(alert.getAcknowledgedAt())
            .resolvedBy(alert.getResolvedBy())
            .resolvedAt(alert.getResolvedAt())
            .resolutionType(alert.getResolutionType() != null ? alert.getResolutionType().name() : null)
            .resolutionNotes(alert.getResolutionNotes())
            .timeSinceTriggeredMinutes(timeSinceTriggered.toMinutes())
            .timeToAcknowledgeMinutes(timeToAcknowledge != null ? timeToAcknowledge.toMinutes() : null)
            .timeToResolveMinutes(timeToResolve != null ? timeToResolve.toMinutes() : null)
            .escalationLevel(alert.getEscalationLevel())
            .autoCheckCount(alert.getAutoCheckCount())
            .isAutoResolved(alert.getAutoResolved())
            .commentCount(alert.getComments() != null ? alert.getComments().size() : 0)
            .build();
    }

    // ========== Metrics ==========

    public AlertMetricsDTO getAlertMetrics(int hoursBack) {
        Instant since = Instant.now().minusSeconds(hoursBack * 3600L);
        
        Double avgTTA = alertRepository.avgTimeToAcknowledge(since);
        Double avgTTR = alertRepository.avgTimeToResolve(since);

        return AlertMetricsDTO.builder()
            .periodHours(hoursBack)
            .avgTimeToAcknowledgeMinutes(avgTTA != null ? avgTTA / 60 : null)
            .avgTimeToResolveMinutes(avgTTR != null ? avgTTR / 60 : null)
            .build();
    }

    // ========== Active Alerts by Assignee (for Leads) ==========

    public List<AssigneeAlertsSummaryDTO> getAlertsByAssignee(Long teamId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));

        List<TeamMember> members = memberRepository.findByTeamAndActiveTrue(team);
        List<AssigneeAlertsSummaryDTO> summaries = new ArrayList<>();

        for (TeamMember member : members) {
            List<Alert> memberAlerts = alertRepository.findActiveAlertsByAssignee(member.getId());
            
            summaries.add(AssigneeAlertsSummaryDTO.builder()
                .memberId(member.getId())
                .memberName(member.getName())
                .email(member.getEmail())
                .role(member.getRole().name())
                .isOnCall(member.isOnCall())
                .alerts(memberAlerts.stream()
                    .map(this::toAlertBriefDTO)
                    .collect(Collectors.toList()))
                .build());
        }

        return summaries;
    }

    // ========== Unassigned Alerts ==========

    public List<AlertBriefDTO> getUnassignedAlerts() {
        return alertRepository.findByStatusAndAssigneeIsNull(AlertStatus.UNASSIGNED)
            .stream()
            .map(this::toAlertBriefDTO)
            .collect(Collectors.toList());
    }

    // ========== Helper Methods ==========

    private boolean isTerminalStatus(AlertStatus status) {
        return status == AlertStatus.RESOLVED || 
               status == AlertStatus.AUTO_RESOLVED || 
               status == AlertStatus.CLOSED ||
               status == AlertStatus.SUPPRESSED;
    }

    private AlertBriefDTO toAlertBriefDTO(Alert alert) {
        return AlertBriefDTO.builder()
            .id(alert.getId())
            .ruleName(alert.getRuleName())
            .appId(alert.getAppId())
            .feature(alert.getFeature())
            .severity(alert.getSeverity().name())
            .status(alert.getStatus().name())
            .triggeredAt(alert.getTriggeredAt())
            .acknowledgedAt(alert.getAcknowledgedAt())
            .ageMinutes(Duration.between(alert.getTriggeredAt(), Instant.now()).toMinutes())
            .build();
    }
}