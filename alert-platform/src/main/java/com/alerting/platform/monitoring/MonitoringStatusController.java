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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringStatusController {

    private final RuleEngine ruleEngine;
    private final UserLevelRuleEngine userLevelRuleEngine;
    private final EventConsumer eventConsumer;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("timestamp", Instant.now());
        status.put("aggregateRuleEngine", ruleEngine.getStatus());
        status.put("userLevelRuleEngine", userLevelRuleEngine.getStats());
        status.put("eventConsumer", eventConsumer.getStats());
        status.put("healthy", isHealthy());
        
        return ResponseEntity.ok(status);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        boolean healthy = isHealthy();
        health.put("status", healthy ? "UP" : "DOWN");
        health.put("timestamp", Instant.now());
        
        Map<String, String> components = new HashMap<>();
        components.put("ruleEngine", ruleEngine.isHealthy() ? "UP" : "DOWN");
        health.put("components", components);
        
        return healthy ? 
            ResponseEntity.ok(health) : 
            ResponseEntity.status(503).body(health);
    }

    private boolean isHealthy() {
        return ruleEngine.isHealthy();
    }
}