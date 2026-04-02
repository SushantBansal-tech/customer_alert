package com.alerting.platform.rules.engine;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.Alert.AlertType;
import com.alerting.platform.alerts.service.AlertService;
import com.alerting.platform.processing.service.UserLevelAggregationService;
import com.alerting.platform.processing.service.UserLevelAggregationService.UserMetrics;
import com.alerting.platform.rules.model.UserAlertRule;
import com.alerting.platform.rules.repository.UserAlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserLevelRuleEngine {

    private final UserAlertRuleRepository ruleRepository;
    private final UserLevelAggregationService aggregationService;
    private final AlertService alertService;

    @Scheduled(fixedRateString = "${alerting.user-level.evaluation-interval-ms:5000}")
    public void evaluateUserLevelRules() {
        log.debug("Starting user-level rule evaluation");

        List<UserAlertRule> rules = ruleRepository.findByEnabledTrue();

        for (UserAlertRule rule : rules) {
            try {
                evaluateRuleForAllUsers(rule);
            } catch (Exception e) {
                log.error("Error evaluating user-level rule {}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    private void evaluateRuleForAllUsers(UserAlertRule rule) {
        Set<String> activeUsers = aggregationService.getActiveUsers(rule.getAppId(), rule.getFeature());

        for (String userId : activeUsers) {
            UserMetrics metrics = aggregationService.getUserMetrics(
                rule.getAppId(), 
                rule.getFeature(), 
                userId
            );

            if (shouldTriggerAlert(rule, metrics)) {
                log.info("User-level rule triggered: {} for user {}", rule.getName(), userId);
                alertService.createUserLevelAlert(rule, metrics);
            }
        }
    }

    private boolean shouldTriggerAlert(UserAlertRule rule, UserMetrics metrics) {
        // Check consecutive failures
        if (metrics.getConsecutiveFailures() >= rule.getMaxConsecutiveFailures()) {
            return true;
        }

        // Check failures in window
        if (metrics.getFailuresInWindow() >= rule.getMaxFailuresInWindow()) {
            return true;
        }

        // Check latency
        if (metrics.getMaxLatencyMs() != null && 
            metrics.getMaxLatencyMs() > rule.getMaxLatencyMs()) {
            return true;
        }

        return false;
    }
}