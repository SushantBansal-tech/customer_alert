package com.alerting.platform.rules.service;

import com.alerting.platform.rules.model.AlertRule;
import com.alerting.platform.rules.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleService {

    private final AlertRuleRepository ruleRepository;

    @Transactional
    public AlertRule createRule(AlertRule rule) {
        log.info("Creating rule: {} for app: {}", rule.getName(), rule.getAppId());
        return ruleRepository.save(rule);
    }

    @Transactional
    public AlertRule updateRule(Long ruleId, AlertRule updates) {
        AlertRule existing = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));

        existing.setName(updates.getName());
        existing.setMetricType(updates.getMetricType());
        existing.setOperator(updates.getOperator());
        existing.setThreshold(updates.getThreshold());
        existing.setWindowSeconds(updates.getWindowSeconds());
        existing.setMinSampleSize(updates.getMinSampleSize());
        existing.setSeverity(updates.getSeverity());
        existing.setNotificationChannels(updates.getNotificationChannels());
        existing.setEnabled(updates.isEnabled());

        return ruleRepository.save(existing);
    }

    @Transactional
    public void deleteRule(Long ruleId) {
        ruleRepository.deleteById(ruleId);
    }

    @Transactional
    public AlertRule toggleRule(Long ruleId, boolean enabled) {
        AlertRule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));
        rule.setEnabled(enabled);
        return ruleRepository.save(rule);
    }

    public List<AlertRule> getRulesForApp(String appId) {
        return ruleRepository.findByAppIdAndEnabledTrue(appId);
    }

    public List<AlertRule> getAllRules() {
        return ruleRepository.findAll();
    }

    public AlertRule getRule(Long ruleId) {
        return ruleRepository.findById(ruleId)
            .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));
    }
}

