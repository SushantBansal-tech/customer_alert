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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleEngine {

    private final AlertRuleRepository ruleRepository;
    private final RegisteredAppRepository appRepository;
    private final AggregationService aggregationService;
    private final AlertService alertService;

    @Scheduled(fixedRateString = "${alerting.rules.evaluation-interval-seconds}000")
    public void evaluateRules() {
        log.debug("Starting rule evaluation cycle");

        List<RegisteredApp> activeApps = appRepository.findAll().stream()
            .filter(RegisteredApp::isActive)
            .toList();

        for (RegisteredApp app : activeApps) {
            evaluateAppRules(app.getAppId());
        }
    }

    private void evaluateAppRules(String appId) {
        Set<Object> activeFeatures = aggregationService.getActiveFeatures(appId)
            .stream().map(o -> (Object) o).collect(java.util.stream.Collectors.toSet());
        
        List<AlertRule> rules = ruleRepository.findByAppIdAndEnabledTrue(appId);

        for (AlertRule rule : rules) {
            if (!activeFeatures.contains(rule.getFeature())) {
                continue;
            }

            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.error("Error evaluating rule {}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    private void evaluateRule(AlertRule rule) {
        AggregatedMetrics metrics = aggregationService.getMetrics(
            rule.getAppId(), 
            rule.getFeature()
        );

        // Check minimum sample size
        if (metrics.getTotalCount() < rule.getMinSampleSize()) {
            log.debug("Skipping rule {} - insufficient samples: {} < {}",
                rule.getId(), metrics.getTotalCount(), rule.getMinSampleSize());
            return;
        }

        double metricValue = extractMetricValue(metrics, rule.getMetricType());
        boolean triggered = evaluateCondition(metricValue, rule.getOperator(), rule.getThreshold());

        if (triggered) {
            log.info("Rule triggered: {} - {} {} {} (actual: {})",
                rule.getName(),
                rule.getMetricType(),
                rule.getOperator(),
                rule.getThreshold(),
                metricValue);

            alertService.createAlert(rule, metrics, metricValue);
        }
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
}

