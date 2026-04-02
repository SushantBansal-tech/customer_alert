package com.alerting.platform.dashboard.controller;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.AlertComment;
import com.alerting.platform.alerts.model.AlertStatus;
import com.alerting.platform.alerts.model.AlertTimeline;
import com.alerting.platform.alerts.repository.AlertCommentRepository;
import com.alerting.platform.alerts.repository.AlertRepository;
import com.alerting.platform.alerts.repository.AlertTimelineRepository;
import com.alerting.platform.alerts.service.AlertService;
import com.alerting.platform.dashboard.dto.*;
import com.alerting.platform.dashboard.service.DashboardService;
import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AlertService alertService;
    private final AlertRepository alertRepository;
    private final AlertTimelineRepository timelineRepository;
    private final AlertCommentRepository commentRepository;

    // ========== Overview ==========

    /**
     * Get alert summary for an application
     * Used by: Operations, Leads, Admins
     */
    @GetMapping("/summary/{appId}")
    public ResponseEntity<AlertSummaryDTO> getAlertSummary(@PathVariable String appId) {
        return ResponseEntity.ok(dashboardService.getAlertSummary(appId));
    }

    /**
     * Get all team workloads
     * Used by: Leads, Admins - to see who is handling what
     */
    @GetMapping("/teams/workload")
    public ResponseEntity<List<TeamWorkloadDTO>> getTeamWorkloads() {
        return ResponseEntity.ok(dashboardService.getTeamWorkloads());
    }

    /**
     * Get alerts grouped by assignee for a team
     * Used by: Team Leads - to see who is handling which alerts
     */
    @GetMapping("/teams/{teamId}/assignees")
    public ResponseEntity<List<AssigneeAlertsSummaryDTO>> getAlertsByAssignee(
            @PathVariable Long teamId) {
        return ResponseEntity.ok(dashboardService.getAlertsByAssignee(teamId));
    }

    // ========== Alert Details ==========

    /**
     * Get detailed alert information including timeline
     * Used by: Anyone tracking resolution progress
     */
    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<AlertDetailDTO> getAlertDetails(@PathVariable Long alertId) {
        Alert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        
        List<AlertTimeline> timeline = timelineRepository.findByAlertIdOrderByCreatedAtDesc(alertId);
        List<AlertComment> comments = commentRepository.findByAlertIdOrderByCreatedAtDesc(alertId);
        
        return ResponseEntity.ok(toAlertDetailDTO(alert, timeline, comments));
    }

    /**
     * Get resolution progress for an alert
     * Used by: Leads - to track how long resolution is taking
     */
    @GetMapping("/alerts/{alertId}/progress")
    public ResponseEntity<ResolutionProgressDTO> getResolutionProgress(@PathVariable Long alertId) {
        return ResponseEntity.ok(dashboardService.getResolutionProgress(alertId));
    }

    // ========== Alert Lists ==========

    /**
     * Get active alerts for an application
     */
    @GetMapping("/apps/{appId}/alerts/active")
    public ResponseEntity<List<AlertBriefDTO>> getActiveAlerts(@PathVariable String appId) {
        List<Alert> alerts = alertService.getActiveAlerts(appId);
        return ResponseEntity.ok(alerts.stream()
            .map(this::toAlertBriefDTO)
            .collect(Collectors.toList()));
    }

    /**
     * Get unassigned alerts - needs attention
     */
    @GetMapping("/alerts/unassigned")
    public ResponseEntity<List<AlertBriefDTO>> getUnassignedAlerts() {
        return ResponseEntity.ok(dashboardService.getUnassignedAlerts());
    }

    /**
     * Get escalated alerts - critical attention needed
     */
    @GetMapping("/alerts/escalated")
    public ResponseEntity<List<AlertBriefDTO>> getEscalatedAlerts() {
        List<Alert> alerts = alertRepository.findByStatus(AlertStatus.ESCALATED);
        return ResponseEntity.ok(alerts.stream()
            .map(this::toAlertBriefDTO)
            .collect(Collectors.toList()));
    }

    /**
     * Get alerts assigned to a specific member
     */
    @GetMapping("/members/{memberId}/alerts")
    public ResponseEntity<List<AlertBriefDTO>> getMemberAlerts(@PathVariable Long memberId) {
        List<Alert> alerts = alertRepository.findActiveAlertsByAssignee(memberId);
        return ResponseEntity.ok(alerts.stream()
            .map(this::toAlertBriefDTO)
            .collect(Collectors.toList()));
    }

    // ========== Metrics ==========

    /**
     * Get alert metrics (TTR, TTA, etc.)
     */
    @GetMapping("/metrics")
    public ResponseEntity<AlertMetricsDTO> getMetrics(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(dashboardService.getAlertMetrics(hours));
    }

    // ========== Alert Actions ==========

    /**
     * Assign alert to a team member
     */
    @PostMapping("/alerts/{alertId}/assign")
    public ResponseEntity<AlertDetailDTO> assignAlert(
            @PathVariable Long alertId,
            @RequestBody AssignmentRequest request) {
        Alert alert = alertService.assignAlert(alertId, request.getMemberId(), request.getAssignedBy());
        return ResponseEntity.ok(toAlertDetailDTO(alert, null, null));
    }

    /**
     * Acknowledge an alert
     */
    @PostMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<AlertDetailDTO> acknowledgeAlert(
            @PathVariable Long alertId,
            @RequestBody AcknowledgeRequest request) {
        Alert alert = alertService.acknowledgeAlert(alertId, request.getAcknowledgedBy());
        return ResponseEntity.ok(toAlertDetailDTO(alert, null, null));
    }

    /**
     * Update alert status
     */
    @PostMapping("/alerts/{alertId}/status")
    public ResponseEntity<AlertDetailDTO> updateStatus(
            @PathVariable Long alertId,
            @RequestBody StatusUpdateRequest request) {
        Alert alert = alertService.updateStatus(alertId, 
            AlertStatus.valueOf(request.getStatus()), request.getUpdatedBy());
        return ResponseEntity.ok(toAlertDetailDTO(alert, null, null));
    }

    /**
     * Resolve an alert
     */
    @PostMapping("/alerts/{alertId}/resolve")
    public ResponseEntity<AlertDetailDTO> resolveAlert(
            @PathVariable Long alertId,
            @RequestBody ResolveRequest request) {
        Alert alert = alertService.resolveAlert(
            alertId, 
            request.getResolvedBy(),
            Alert.ResolutionType.valueOf(request.getResolutionType()),
            request.getNotes()
        );
        return ResponseEntity.ok(toAlertDetailDTO(alert, null, null));
    }

    /**
     * Add a comment to an alert
     */
    @PostMapping("/alerts/{alertId}/comments")
    public ResponseEntity<AlertDetailDTO.CommentDTO> addComment(
            @PathVariable Long alertId,
            @RequestBody CommentRequest request) {
        AlertComment comment = alertService.addComment(
            alertId,
            request.getContent(),
            request.getAuthorId(),
            request.getAuthorName(),
            request.isInternal()
        );
        return ResponseEntity.ok(toCommentDTO(comment));
    }

    // ========== Request DTOs ==========

    @lombok.Data
    public static class AssignmentRequest {
        private Long memberId;
        private String assignedBy;
    }

    @lombok.Data
    public static class AcknowledgeRequest {
        private String acknowledgedBy;
    }

    @lombok.Data
    public static class StatusUpdateRequest {
        private String status;
        private String updatedBy;
    }

    @lombok.Data
    public static class ResolveRequest {
        private String resolvedBy;
        private String resolutionType;
        private String notes;
    }

    @lombok.Data
    public static class CommentRequest {
        private String content;
        private String authorId;
        private String authorName;
        private boolean internal;
    }

    // ========== Mapping Methods ==========

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
            .ageMinutes(java.time.Duration.between(
                alert.getTriggeredAt(), java.time.Instant.now()).toMinutes())
            .build();
    }

    private AlertDetailDTO toAlertDetailDTO(Alert alert, 
                                            List<AlertTimeline> timeline,
                                            List<AlertComment> comments) {
        return AlertDetailDTO.builder()
            .id(alert.getId())
            .appId(alert.getAppId())
            .feature(alert.getFeature())
            .ruleName(alert.getRuleName())
            .ruleId(alert.getRuleId())
            .alertType(alert.getAlertType().name())
            .affectedUserId(alert.getAffectedUserId())
            .affectedUserCount(alert.getAffectedUserCount())
            .severity(alert.getSeverity().name())
            .message(alert.getMessage())
            .metricValue(alert.getMetricValue())
            .threshold(alert.getThreshold())
            .sampleSize(alert.getSampleSize())
            .status(alert.getStatus().name())
            .triggeredAt(alert.getTriggeredAt())
            .assignedTeam(alert.getAssignedTeam() != null ? toTeamInfoDTO(alert.getAssignedTeam()) : null)
            .assignee(alert.getAssignee() != null ? toMemberInfoDTO(alert.getAssignee()) : null)
            .assignedAt(alert.getAssignedAt())
            .assignedBy(alert.getAssignedBy())
            .acknowledgedAt(alert.getAcknowledgedAt())
            .acknowledgedBy(alert.getAcknowledgedBy())
            .resolvedAt(alert.getResolvedAt())
            .resolvedBy(alert.getResolvedBy())
            .resolutionType(alert.getResolutionType() != null ? alert.getResolutionType().name() : null)
            .resolutionNotes(alert.getResolutionNotes())
            .escalationLevel(alert.getEscalationLevel())
            .lastEscalatedAt(alert.getLastEscalatedAt())
            .lastAutoCheckAt(alert.getLastAutoCheckAt())
            .autoCheckCount(alert.getAutoCheckCount())
            .autoResolved(alert.getAutoResolved())
            .timeline(timeline != null ? timeline.stream()
                .map(this::toTimelineDTO).collect(Collectors.toList()) : null)
            .comments(comments != null ? comments.stream()
                .map(this::toCommentDTO).collect(Collectors.toList()) : null)
            .build();
    }

    private AlertDetailDTO.TeamInfoDTO toTeamInfoDTO(Team team) {
        return AlertDetailDTO.TeamInfoDTO.builder()
            .id(team.getId())
            .name(team.getName())
            .slackChannel(team.getSlackChannel())
            .build();
    }

    private AlertDetailDTO.MemberInfoDTO toMemberInfoDTO(TeamMember member) {
        return AlertDetailDTO.MemberInfoDTO.builder()
            .id(member.getId())
            .name(member.getName())
            .email(member.getEmail())
            .role(member.getRole().name())
            .isOnCall(member.isOnCall())
            .build();
    }

    private AlertDetailDTO.TimelineEntryDTO toTimelineDTO(AlertTimeline entry) {
        return AlertDetailDTO.TimelineEntryDTO.builder()
            .id(entry.getId())
            .eventType(entry.getEventType().name())
            .description(entry.getDescription())
            .performedBy(entry.getPerformedBy())
            .createdAt(entry.getCreatedAt())
            .build();
    }

    private AlertDetailDTO.CommentDTO toCommentDTO(AlertComment comment) {
        return AlertDetailDTO.CommentDTO.builder()
            .id(comment.getId())
            .content(comment.getContent())
            .authorId(comment.getAuthorId())
            .authorName(comment.getAuthorName())
            .isInternal(comment.isInternal())
            .createdAt(comment.getCreatedAt())
            .build();
    }
}