package com.alerting.platform.rules.engine;

import com.alerting.platform.alerts.service.AlertService;
import com.alerting.platform.app.model.RegisteredApp;
import com.alerting.platform.app.repository.RegisteredAppRepository;
import com.alerting.platform.processing.service.AggregationService;
import com.alerting.platform.processing.service.AggregationService.AggregatedMetrics;
import com.alerting.platform.rules.model.AlertRule;
import com.alerting.platform.rules.model.AlertRule.MetricType;
import com.alerting.platform.rules.model.AlertRule.Operator;
import com.alerting.platform.rules.repository.AlertRuleRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleEngine {

    private final AlertRuleRepository ruleRepository;
    private final RegisteredAppRepository appRepository;
    private final AggregationService aggregationService;
    private final AlertService alertService;

    @Value("${alerting.rules.evaluation-interval-seconds:10}")
    private int evaluationIntervalSeconds;

    @Value("${alerting.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    // State tracking
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalRulesEvaluated = new AtomicLong(0);
    private final AtomicLong totalAlertsTriggered = new AtomicLong(0);
    private final AtomicLong evaluationErrors = new AtomicLong(0);
    private Instant lastEvaluationTime;
    private Instant startTime;

    @PostConstruct
    public void init() {
        startTime = Instant.now();
        log.info("========================================");
        log.info("🚀 RULE ENGINE INITIALIZED");
        log.info("   Monitoring enabled: {}", monitoringEnabled);
        log.info("   Evaluation interval: {} seconds", evaluationIntervalSeconds);
        log.info("   Started at: {}", startTime);
        log.info("========================================");
    }

    @Scheduled(fixedRateString = "${alerting.rules.evaluation-interval-seconds:10}000")
    public void evaluateRules() {
        if (!monitoringEnabled) {
            log.trace("Monitoring disabled, skipping evaluation");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Previous evaluation still running, skipping this cycle");
            return;
        }

        try {
            long startMillis = System.currentTimeMillis();
            log.debug("🔍 Starting rule evaluation cycle at {}", Instant.now());

            int appsChecked = 0;
            int rulesEvaluated = 0;
            int alertsTriggered = 0;

            List<RegisteredApp> activeApps = appRepository.findAll().stream()
                .filter(RegisteredApp::isActive)
                .toList();

            for (RegisteredApp app : activeApps) {
                EvaluationResult result = evaluateAppRules(app.getAppId());
                rulesEvaluated += result.rulesEvaluated;
                alertsTriggered += result.alertsTriggered;
                appsChecked++;
            }

            // Update statistics
            totalEvaluations.incrementAndGet();
            totalRulesEvaluated.addAndGet(rulesEvaluated);
            totalAlertsTriggered.addAndGet(alertsTriggered);
            lastEvaluationTime = Instant.now();

            long duration = System.currentTimeMillis() - startMillis;
            log.debug("✅ Rule evaluation completed: apps={}, rules={}, alerts={}, duration={}ms",
                appsChecked, rulesEvaluated, alertsTriggered, duration);

        } catch (Exception e) {
            evaluationErrors.incrementAndGet();
            log.error("❌ Error in rule evaluation cycle", e);
        } finally {
            isRunning.set(false);
        }
    }

    private EvaluationResult evaluateAppRules(String appId) {
        int rulesEvaluated = 0;
        int alertsTriggered = 0;

        Set<Object> activeFeatures = aggregationService.getActiveFeatures(appId)
            .stream()
            .map(o -> (Object) o)
            .collect(java.util.stream.Collectors.toSet());

        if (activeFeatures.isEmpty()) {
            log.trace("No active features for app: {}", appId);
            return new EvaluationResult(0, 0);
        }

        List<AlertRule> rules = ruleRepository.findByAppIdAndEnabledTrue(appId);

        for (AlertRule rule : rules) {
            if (!activeFeatures.contains(rule.getFeature())) {
                continue;
            }

            try {
                boolean triggered = evaluateRule(rule);
                rulesEvaluated++;
                if (triggered) {
                    alertsTriggered++;
                }
            } catch (Exception e) {
                evaluationErrors.incrementAndGet();
                log.error("Error evaluating rule {} for app {}: {}", 
                    rule.getId(), appId, e.getMessage());
            }
        }

        return new EvaluationResult(rulesEvaluated, alertsTriggered);
    }

    private boolean evaluateRule(AlertRule rule) {
        AggregatedMetrics metrics = aggregationService.getMetrics(
            rule.getAppId(), 
            rule.getFeature()
        );

        // Check minimum sample size
        if (metrics.getTotalCount() < rule.getMinSampleSize()) {
            log.trace("Skipping rule {} - insufficient samples: {} < {}",
                rule.getId(), metrics.getTotalCount(), rule.getMinSampleSize());
            return false;
        }

        double metricValue = extractMetricValue(metrics, rule.getMetricType());
        boolean triggered = evaluateCondition(metricValue, rule.getOperator(), rule.getThreshold());

        if (triggered) {
            log.info("🚨 Rule TRIGGERED: {} | {} {} {} | Actual: {} | App: {} | Feature: {}",
                rule.getName(),
                rule.getMetricType(),
                rule.getOperator(),
                rule.getThreshold(),
                metricValue,
                rule.getAppId(),
                rule.getFeature());

            alertService.createAlert(rule, metrics, metricValue);
            return true;
        }

        return false;
    }

    private double extractMetricValue(AggregatedMetrics metrics, MetricType metricType) {
        return switch (metricType) {
            case FAILURE_RATE -> metrics.getFailureRate();
            case FAILURE_COUNT -> metrics.getFailureCount();
            case LATENCY_P50 -> metrics.getP50Latency() != null ? metrics.getP50Latency() : 0;
            case LATENCY_P99 -> metrics.getP99Latency() != null ? metrics.getP99Latency() : 0;
            case ERROR_RATE -> metrics.getFailureRate();
        };
    }

    private boolean evaluateCondition(double value, Operator operator, double threshold) {
        return switch (operator) {
            case GREATER_THAN -> value > threshold;
            case LESS_THAN -> value < threshold;
            case EQUALS -> Math.abs(value - threshold) < 0.0001;
            case NOT_EQUALS -> Math.abs(value - threshold) >= 0.0001;
        };
    }

    // ========== Status & Health Methods ==========

    /**
     * Check if the rule engine is healthy
     */
    public boolean isHealthy() {
        // Healthy if monitoring is enabled and not stuck in running state
        if (!monitoringEnabled) {
            return true; // Disabled is considered healthy
        }
        
        // Check if we're stuck (running for too long)
        if (isRunning.get() && lastEvaluationTime != null) {
            long secondsSinceLastEvaluation = java.time.Duration
                .between(lastEvaluationTime, Instant.now())
                .getSeconds();
            
            // If running for more than 5x the evaluation interval, consider unhealthy
            if (secondsSinceLastEvaluation > evaluationIntervalSeconds * 5) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Get current status of the rule engine
     */
    public RuleEngineStatus getStatus() {
        long uptimeSeconds = java.time.Duration.between(startTime, Instant.now()).getSeconds();
        
        return RuleEngineStatus.builder()
            .enabled(monitoringEnabled)
            .running(isRunning.get())
            .healthy(isHealthy())
            .evaluationIntervalSeconds(evaluationIntervalSeconds)
            .totalEvaluations(totalEvaluations.get())
            .totalRulesEvaluated(totalRulesEvaluated.get())
            .totalAlertsTriggered(totalAlertsTriggered.get())
            .evaluationErrors(evaluationErrors.get())
            .lastEvaluationTime(lastEvaluationTime)
            .startTime(startTime)
            .uptimeSeconds(uptimeSeconds)
            .build();
    }

    // ========== Helper Classes ==========

    private record EvaluationResult(int rulesEvaluated, int alertsTriggered) {}

    @Data
    @Builder
    public static class RuleEngineStatus {
        private boolean enabled;
        private boolean running;
        private boolean healthy;
        private int evaluationIntervalSeconds;
        private long totalEvaluations;
        private long totalRulesEvaluated;
        private long totalAlertsTriggered;
        private long evaluationErrors;
        private Instant lastEvaluationTime;
        private Instant startTime;
        private long uptimeSeconds;
    }
}