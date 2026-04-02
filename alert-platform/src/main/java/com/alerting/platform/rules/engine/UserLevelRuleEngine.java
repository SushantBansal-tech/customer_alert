package com.alerting.platform.rules.engine;

import com.alerting.platform.alerts.service.AlertService;
import com.alerting.platform.processing.service.UserLevelAggregationService;
import com.alerting.platform.processing.service.UserLevelAggregationService.UserMetrics;
import com.alerting.platform.rules.model.UserAlertRule;
import com.alerting.platform.rules.repository.UserAlertRuleRepository;
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
public class UserLevelRuleEngine {

    private final UserAlertRuleRepository ruleRepository;
    private final UserLevelAggregationService aggregationService;
    private final AlertService alertService;

    @Value("${alerting.user-level.evaluation-interval-ms:5000}")
    private int evaluationIntervalMs;

    @Value("${alerting.user-level.enabled:true}")
    private boolean userLevelMonitoringEnabled;

    @Value("${alerting.user-level.max-users-per-cycle:1000}")
    private int maxUsersPerCycle;

    // State tracking
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalUsersChecked = new AtomicLong(0);
    private final AtomicLong totalAlertsTriggered = new AtomicLong(0);
    private final AtomicLong evaluationErrors = new AtomicLong(0);
    private Instant lastEvaluationTime;
    private Instant startTime;

    @PostConstruct
    public void init() {
        startTime = Instant.now();
        log.info("========================================");
        log.info("🚀 USER-LEVEL RULE ENGINE INITIALIZED");
        log.info("   Monitoring enabled: {}", userLevelMonitoringEnabled);
        log.info("   Evaluation interval: {} ms", evaluationIntervalMs);
        log.info("   Max users per cycle: {}", maxUsersPerCycle);
        log.info("   Started at: {}", startTime);
        log.info("========================================");
    }

    @Scheduled(fixedRateString = "${alerting.user-level.evaluation-interval-ms:5000}")
    public void evaluateUserLevelRules() {
        if (!userLevelMonitoringEnabled) {
            log.trace("User-level monitoring disabled, skipping evaluation");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Previous user-level evaluation still running, skipping");
            return;
        }

        try {
            long startMillis = System.currentTimeMillis();
            log.debug("🔍 Starting user-level rule evaluation at {}", Instant.now());

            int usersChecked = 0;
            int alertsTriggered = 0;

            List<UserAlertRule> rules = ruleRepository.findByEnabledTrue();

            for (UserAlertRule rule : rules) {
                try {
                    EvaluationResult result = evaluateRuleForAllUsers(rule);
                    usersChecked += result.usersChecked;
                    alertsTriggered += result.alertsTriggered;
                } catch (Exception e) {
                    evaluationErrors.incrementAndGet();
                    log.error("Error evaluating user-level rule {}: {}", rule.getId(), e.getMessage());
                }
            }

            // Update statistics
            totalEvaluations.incrementAndGet();
            totalUsersChecked.addAndGet(usersChecked);
            totalAlertsTriggered.addAndGet(alertsTriggered);
            lastEvaluationTime = Instant.now();

            long duration = System.currentTimeMillis() - startMillis;
            log.debug("✅ User-level evaluation completed: users={}, alerts={}, duration={}ms",
                usersChecked, alertsTriggered, duration);

        } catch (Exception e) {
            evaluationErrors.incrementAndGet();
            log.error("❌ Error in user-level rule evaluation", e);
        } finally {
            isRunning.set(false);
        }
    }

    private EvaluationResult evaluateRuleForAllUsers(UserAlertRule rule) {
        int usersChecked = 0;
        int alertsTriggered = 0;

        Set<String> activeUsers = aggregationService.getActiveUsers(
            rule.getAppId(), 
            rule.getFeature()
        );

        for (String userId : activeUsers) {
            if (usersChecked >= maxUsersPerCycle) {
                log.warn("Reached max users per cycle limit: {}", maxUsersPerCycle);
                break;
            }

            try {
                UserMetrics metrics = aggregationService.getUserMetrics(
                    rule.getAppId(),
                    rule.getFeature(),
                    userId
                );

                if (shouldTriggerAlert(rule, metrics)) {
                    log.info("🚨 User-level rule TRIGGERED: {} | User: {} | Feature: {}",
                        rule.getName(), userId, rule.getFeature());

                    alertService.createUserLevelAlert(rule, metrics);
                    alertsTriggered++;
                }

                usersChecked++;
            } catch (Exception e) {
                evaluationErrors.incrementAndGet();
                log.error("Error evaluating rule {} for user {}: {}", 
                    rule.getId(), userId, e.getMessage());
            }
        }

        return new EvaluationResult(usersChecked, alertsTriggered);
    }

    private boolean shouldTriggerAlert(UserAlertRule rule, UserMetrics metrics) {
        // Check consecutive failures threshold
        if (metrics.getConsecutiveFailures() >= rule.getMaxConsecutiveFailures()) {
            log.debug("User {} exceeded consecutive failures: {} >= {}",
                metrics.getUserId(), 
                metrics.getConsecutiveFailures(), 
                rule.getMaxConsecutiveFailures());
            return true;
        }

        // Check failures in window threshold
        if (metrics.getFailuresInWindow() >= rule.getMaxFailuresInWindow()) {
            log.debug("User {} exceeded failures in window: {} >= {}",
                metrics.getUserId(), 
                metrics.getFailuresInWindow(), 
                rule.getMaxFailuresInWindow());
            return true;
        }

        // Check latency threshold
        if (metrics.getMaxLatencyMs() != null && 
            metrics.getMaxLatencyMs() > rule.getMaxLatencyMs()) {
            log.debug("User {} exceeded latency threshold: {} > {}",
                metrics.getUserId(), 
                metrics.getMaxLatencyMs(), 
                rule.getMaxLatencyMs());
            return true;
        }

        return false;
    }

    // ========== Status & Health Methods ==========

    /**
     * Check if the user-level rule engine is healthy
     */
    public boolean isHealthy() {
        if (!userLevelMonitoringEnabled) {
            return true;
        }

        // Check if we're stuck
        if (isRunning.get() && lastEvaluationTime != null) {
            long msSinceLastEvaluation = java.time.Duration
                .between(lastEvaluationTime, Instant.now())
                .toMillis();

            // If running for more than 5x the evaluation interval, consider unhealthy
            if (msSinceLastEvaluation > evaluationIntervalMs * 5) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get statistics for monitoring
     */
    public UserLevelRuleEngineStats getStats() {
        long uptimeSeconds = java.time.Duration.between(startTime, Instant.now()).getSeconds();

        return UserLevelRuleEngineStats.builder()
            .enabled(userLevelMonitoringEnabled)
            .running(isRunning.get())
            .healthy(isHealthy())
            .evaluationIntervalMs(evaluationIntervalMs)
            .maxUsersPerCycle(maxUsersPerCycle)
            .totalEvaluations(totalEvaluations.get())
            .totalUsersChecked(totalUsersChecked.get())
            .totalAlertsTriggered(totalAlertsTriggered.get())
            .evaluationErrors(evaluationErrors.get())
            .lastEvaluationTime(lastEvaluationTime)
            .startTime(startTime)
            .uptimeSeconds(uptimeSeconds)
            .build();
    }

    // ========== Helper Classes ==========

    private record EvaluationResult(int usersChecked, int alertsTriggered) {}

    @Data
    @Builder
    public static class UserLevelRuleEngineStats {
        private boolean enabled;
        private boolean running;
        private boolean healthy;
        private int evaluationIntervalMs;
        private int maxUsersPerCycle;
        private long totalEvaluations;
        private long totalUsersChecked;
        private long totalAlertsTriggered;
        private long evaluationErrors;
        private Instant lastEvaluationTime;
        private Instant startTime;
        private long uptimeSeconds;
    }
}