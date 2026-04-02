package com.alerting.platform.admin;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.service.AlertService;
import com.alerting.platform.app.model.RegisteredApp;
import com.alerting.platform.app.service.AppRegistrationService;
import com.alerting.platform.rules.model.AlertRule;
import com.alerting.platform.rules.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AppRegistrationService appService;
    private final RuleService ruleService;
    private final AlertService alertService;

    // ========== App Management ==========

    @PostMapping("/apps")
    public ResponseEntity<RegisteredApp> registerApp(@RequestBody Map<String, String> request) {
        RegisteredApp app = appService.registerApp(
            request.get("name"),
            request.get("description")
        );
        return ResponseEntity.ok(app);
    }

    @GetMapping("/apps/{appId}")
    public ResponseEntity<RegisteredApp> getApp(@PathVariable String appId) {
        return appService.findByAppId(appId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ========== Rule Management ==========

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

    @GetMapping("/apps/{appId}/rules")
    public ResponseEntity<List<AlertRule>> getAppRules(@PathVariable String appId) {
        return ResponseEntity.ok(ruleService.getRulesForApp(appId));
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

    @PostMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<Alert> acknowledgeAlert(
            @PathVariable Long alertId,
            @RequestParam String acknowledgedBy) {
        return ResponseEntity.ok(alertService.acknowledgeAlert(alertId, acknowledgedBy));
    }

    @PostMapping("/alerts/{alertId}/resolve")
    public ResponseEntity<Alert> resolveAlert(
            @PathVariable Long alertId,
            @RequestParam String resolvedBy) {
        return ResponseEntity.ok(alertService.resolveAlert(alertId, resolvedBy));
    }
}

