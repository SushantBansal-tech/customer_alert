package com.alerting.platform.alerts.service;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final AlertRepository alertRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${alerting.deduplication.window-minutes}")
    private int deduplicationWindowMinutes;

    public boolean isDuplicate(String appId, String feature, Long ruleId) {
        String dedupKey = buildDedupKey(appId, feature, ruleId);
        
        // Check Redis first (faster)
        Boolean exists = redisTemplate.hasKey(dedupKey);
        if (Boolean.TRUE.equals(exists)) {
            log.debug("Duplicate alert suppressed (Redis): {}", dedupKey);
            return true;
        }

        // Check database for recent unresolved alerts
        Instant since = Instant.now().minus(Duration.ofMinutes(deduplicationWindowMinutes));
        boolean dbDuplicate = alertRepository.findRecentAlert(appId, feature, ruleId, since).isPresent();
        
        if (dbDuplicate) {
            log.debug("Duplicate alert suppressed (DB): {}", dedupKey);
            return true;
        }

        return false;
    }

    public void markAlertSent(Alert alert) {
        String dedupKey = buildDedupKey(alert.getAppId(), alert.getFeature(), alert.getRuleId());
        redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofMinutes(deduplicationWindowMinutes));
    }

    private String buildDedupKey(String appId, String feature, Long ruleId) {
        return "dedup:alert:" + appId + ":" + feature + ":" + ruleId;
    }
}

