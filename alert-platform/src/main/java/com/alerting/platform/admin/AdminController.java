package com.alerting.platform.admin;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.service.AlertService;
import com.alerting.platform.app.model.RegisteredApp;
import com.alerting.platform.app.service.AppRegistrationService;
import com.alerting.platform.rules.model.AlertRule;
import com.alerting.platform.rules.model.UserAlertRule;
import com.alerting.platform.rules.repository.UserAlertRuleRepository;
import com.alerting.platform.rules.service.RuleService;
import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import com.alerting.platform.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AppRegistrationService appService;
    private final RuleService ruleService;
    private final AlertService alertService;
    private final TeamService teamService;
    private final UserAlertRuleRepository userAlertRuleRepository;

    // ========== App Management ==========

    @PostMapping("/apps")
    public ResponseEntity<RegisteredApp> registerApp(@RequestBody AppRegistrationRequest request) {
        RegisteredApp app = appService.registerApp(request.getName(), request.getDescription());
        return ResponseEntity.ok(app);
    }

    @GetMapping("/apps/{appId}")
    public ResponseEntity<RegisteredApp> getApp(@PathVariable String appId) {
        return appService.findByAppId(appId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ========== Aggregate Rule Management ==========

    @PostMapping("/rules")
    public ResponseEntity<AlertRule> createRule(@RequestBody AlertRule rule) {
        return ResponseEntity.ok(ruleService.createRule(rule));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<AlertRule>> getAllRules() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<AlertRule> getRule(@PathVariable Long ruleId) {
        return ResponseEntity.ok(ruleService.getRule(ruleId));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<AlertRule> updateRule(
            @PathVariable Long ruleId,
            @RequestBody AlertRule rule) {
        return ResponseEntity.ok(ruleService.updateRule(ruleId, rule));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long ruleId) {
        ruleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rules/{ruleId}/toggle")
    public ResponseEntity<AlertRule> toggleRule(
            @PathVariable Long ruleId,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(ruleService.toggleRule(ruleId, enabled));
    }

    // ========== User-Level Rule Management ==========

    @PostMapping("/user-rules")
    public ResponseEntity<UserAlertRule> createUserRule(@RequestBody UserAlertRule rule) {
        return ResponseEntity.ok(userAlertRuleRepository.save(rule));
    }

    @GetMapping("/user-rules")
    public ResponseEntity<List<UserAlertRule>> getAllUserRules() {
        return ResponseEntity.ok(userAlertRuleRepository.findAll());
    }

    @GetMapping("/user-rules/{ruleId}")
    public ResponseEntity<UserAlertRule> getUserRule(@PathVariable Long ruleId) {
        return userAlertRuleRepository.findById(ruleId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/user-rules/{ruleId}")
    public ResponseEntity<UserAlertRule> updateUserRule(
            @PathVariable Long ruleId,
            @RequestBody UserAlertRule updates) {
        UserAlertRule existing = userAlertRuleRepository.findById(ruleId)
            .orElseThrow(() -> new RuntimeException("User rule not found: " + ruleId));

        existing.setName(updates.getName());
        existing.setMaxConsecutiveFailures(updates.getMaxConsecutiveFailures());
        existing.setMaxFailuresInWindow(updates.getMaxFailuresInWindow());
        existing.setWindowSeconds(updates.getWindowSeconds());
        existing.setMaxLatencyMs(updates.getMaxLatencyMs());
        existing.setSeverity(updates.getSeverity());
        existing.setEnabled(updates.isEnabled());
        existing.setNotificationChannels(updates.getNotificationChannels());

        return ResponseEntity.ok(userAlertRuleRepository.save(existing));
    }

    @DeleteMapping("/user-rules/{ruleId}")
    public ResponseEntity<Void> deleteUserRule(@PathVariable Long ruleId) {
        userAlertRuleRepository.deleteById(ruleId);
        return ResponseEntity.noContent().build();
    }

    // ========== Team Management ==========

    @PostMapping("/teams")
    public ResponseEntity<Team> createTeam(@RequestBody TeamCreateRequest request) {
        Team team = Team.builder()
            .name(request.getName())
            .description(request.getDescription())
            .slackChannel(request.getSlackChannel())
            .emailDistribution(request.getEmailDistribution())
            .responsibleFeatures(request.getResponsibleFeatures())
            .responsibleApps(request.getResponsibleApps())
            .active(true)
            .build();
        return ResponseEntity.ok(teamService.createTeam(team));
    }

    @GetMapping("/teams")
    public ResponseEntity<List<Team>> getAllTeams() {
        return ResponseEntity.ok(teamService.getAllActiveTeams());
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<Team> getTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(teamService.getTeamById(teamId));
    }

    @PutMapping("/teams/{teamId}")
    public ResponseEntity<Team> updateTeam(
            @PathVariable Long teamId,
            @RequestBody Team updates) {
        return ResponseEntity.ok(teamService.updateTeam(teamId, updates));
    }

    @PostMapping("/teams/{teamId}/features")
    public ResponseEntity<Void> assignFeatureToTeam(
            @PathVariable Long teamId,
            @RequestBody Map<String, String> request) {
        teamService.assignFeatureToTeam(teamId, request.get("feature"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/teams/{teamId}/apps")
    public ResponseEntity<Void> assignAppToTeam(
            @PathVariable Long teamId,
            @RequestBody Map<String, String> request) {
        teamService.assignAppToTeam(teamId, request.get("appId"));
        return ResponseEntity.ok().build();
    }

    // ========== Team Member Management ==========

    @PostMapping("/teams/{teamId}/members")
    public ResponseEntity<TeamMember> addMember(
            @PathVariable Long teamId,
            @RequestBody TeamMemberRequest request) {
        TeamMember member = TeamMember.builder()
            .memberId(request.getMemberId())
            .name(request.getName())
            .email(request.getEmail())
            .slackUserId(request.getSlackUserId())
            .phoneNumber(request.getPhoneNumber())
            .role(request.getRole())
            .build();
        return ResponseEntity.ok(teamService.addMember(teamId, member));
    }

    @GetMapping("/teams/{teamId}/members")
    public ResponseEntity<List<TeamMember>> getTeamMembers(@PathVariable Long teamId) {
        Team team = teamService.getTeamById(teamId);
        return ResponseEntity.ok(teamService.getActiveTeamMembers(team));
    }

    @PutMapping("/members/{memberId}")
    public ResponseEntity<TeamMember> updateMember(
            @PathVariable Long memberId,
            @RequestBody TeamMemberRequest request) {
        TeamMember updates = TeamMember.builder()
            .name(request.getName())
            .email(request.getEmail())
            .slackUserId(request.getSlackUserId())
            .phoneNumber(request.getPhoneNumber())
            .role(request.getRole())
            .build();
        return ResponseEntity.ok(teamService.updateMember(memberId, updates));
    }

    @PostMapping("/members/{memberId}/on-call")
    public ResponseEntity<TeamMember> setOnCall(
            @PathVariable Long memberId,
            @RequestParam boolean onCall) {
        return ResponseEntity.ok(teamService.setOnCall(memberId, onCall));
    }

    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Void> deactivateMember(@PathVariable Long memberId) {
        teamService.deactivateMember(memberId);
        return ResponseEntity.noContent().build();
    }

    // ========== Alert Management ==========

    @GetMapping("/apps/{appId}/alerts")
    public ResponseEntity<List<Alert>> getActiveAlerts(@PathVariable String appId) {
        return ResponseEntity.ok(alertService.getActiveAlerts(appId));
    }

    @GetMapping("/alerts/recent")
    public ResponseEntity<List<Alert>> getRecentAlerts(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(alertService.getRecentAlerts(hours));
    }

    // ========== Request DTOs ==========

    @lombok.Data
    public static class AppRegistrationRequest {
        private String name;
        private String description;
    }

    @lombok.Data
    public static class TeamCreateRequest {
        private String name;
        private String description;
        private String slackChannel;
        private String emailDistribution;
        private Set<String> responsibleFeatures;
        private Set<String> responsibleApps;
    }

    @lombok.Data
    public static class TeamMemberRequest {
        private String memberId;
        private String name;
        private String email;
        private String slackUserId;
        private String phoneNumber;
        private com.alerting.platform.team.model.TeamRole role;
    }
}