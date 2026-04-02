package com.alerting.platform.monitoring;

import com.alerting.platform.processing.consumer.EventConsumer;
import com.alerting.platform.rules.engine.RuleEngine;
import com.alerting.platform.rules.engine.UserLevelRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringStatusController {

    private final RuleEngine ruleEngine;
    private final UserLevelRuleEngine userLevelRuleEngine;
    private final EventConsumer eventConsumer;

    /**
     * Get comprehensive monitoring status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        
        status.put("timestamp", Instant.now());
        status.put("healthy", isOverallHealthy());
        
        // Component statuses
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("aggregateRuleEngine", ruleEngine.getStatus());
        components.put("userLevelRuleEngine", userLevelRuleEngine.getStats());
        components.put("eventConsumer", eventConsumer.getStats());
        status.put("components", components);
        
        return ResponseEntity.ok(status);
    }

    /**
     * Simple health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        
        boolean healthy = isOverallHealthy();
        health.put("status", healthy ? "UP" : "DOWN");
        health.put("timestamp", Instant.now());
        
        // Component health
        Map<String, Object> components = new LinkedHashMap<>();
        
        components.put("aggregateRuleEngine", Map.of(
            "status", ruleEngine.isHealthy() ? "UP" : "DOWN",
            "enabled", ruleEngine.getStatus().isEnabled()
        ));
        
        components.put("userLevelRuleEngine", Map.of(
            "status", userLevelRuleEngine.isHealthy() ? "UP" : "DOWN",
            "enabled", userLevelRuleEngine.getStats().isEnabled()
        ));
        
        components.put("eventConsumer", Map.of(
            "status", "UP",
            "eventsProcessed", eventConsumer.getStats().getTotalEventsProcessed()
        ));
        
        health.put("components", components);
        
        return healthy ? 
            ResponseEntity.ok(health) : 
            ResponseEntity.status(503).body(health);
    }

    /**
     * Get detailed statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDetailedStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        stats.put("timestamp", Instant.now());
        
        // Rule Engine Stats
        RuleEngine.RuleEngineStatus ruleEngineStatus = ruleEngine.getStatus();
        stats.put("aggregateRuleEngine", Map.of(
            "totalEvaluations", ruleEngineStatus.getTotalEvaluations(),
            "totalRulesEvaluated", ruleEngineStatus.getTotalRulesEvaluated(),
            "totalAlertsTriggered", ruleEngineStatus.getTotalAlertsTriggered(),
            "evaluationErrors", ruleEngineStatus.getEvaluationErrors(),
            "uptimeSeconds", ruleEngineStatus.getUptimeSeconds()
        ));
        
        // User Level Rule Engine Stats
        UserLevelRuleEngine.UserLevelRuleEngineStats userLevelStats = userLevelRuleEngine.getStats();
        stats.put("userLevelRuleEngine", Map.of(
            "totalEvaluations", userLevelStats.getTotalEvaluations(),
            "totalUsersChecked", userLevelStats.getTotalUsersChecked(),
            "totalAlertsTriggered", userLevelStats.getTotalAlertsTriggered(),
            "evaluationErrors", userLevelStats.getEvaluationErrors(),
            "uptimeSeconds", userLevelStats.getUptimeSeconds()
        ));
        
        // Event Consumer Stats
        EventConsumer.ConsumerStats consumerStats = eventConsumer.getStats();
        stats.put("eventConsumer", Map.of(
            "totalEventsProcessed", consumerStats.getTotalEventsProcessed(),
            "successEvents", consumerStats.getSuccessEventsProcessed(),
            "failureEvents", consumerStats.getFailureEventsProcessed(),
            "processingErrors", consumerStats.getProcessingErrors(),
            "eventsPerSecond", String.format("%.2f", consumerStats.getEventsPerSecond()),
            "uptimeSeconds", consumerStats.getUptimeSeconds()
        ));
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Check overall system health
     */
    private boolean isOverallHealthy() {
        return ruleEngine.isHealthy() && userLevelRuleEngine.isHealthy();
    }
}