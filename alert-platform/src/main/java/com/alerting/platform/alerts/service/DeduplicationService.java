package com.alerting.platform.alerts.service;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.AlertStatus;
import com.alerting.platform.alerts.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final AlertRepository alertRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${alerting.deduplication.window-minutes:30}")
    private int deduplicationWindowMinutes;

    @Value("${alerting.deduplication.suppression-minutes:5}")
    private int suppressionMinutes;

    private static final String DEDUP_PREFIX = "dedup:";
    private static final String ACTIVE_ALERT_PREFIX = "active:alert:";

    /**
     * Check if an alert with this key already exists and is not resolved
     */
    public boolean isDuplicate(String deduplicationKey) {
        // Quick check in Redis
        String redisKey = DEDUP_PREFIX + deduplicationKey;
        Boolean exists = redisTemplate.hasKey(redisKey);
        
        if (Boolean.TRUE.equals(exists)) {
            log.debug("Duplicate detected in Redis: {}", deduplicationKey);
            return true;
        }

        // Check for active alert in database
        Optional<Alert> activeAlert = alertRepository.findActiveAlertByDeduplicationKey(
            deduplicationKey,
            List.of(AlertStatus.TRIGGERED, AlertStatus.NOTIFIED, AlertStatus.ASSIGNED,
                    AlertStatus.ACKNOWLEDGED, AlertStatus.IN_PROGRESS, AlertStatus.ESCALATED)
        );

        if (activeAlert.isPresent()) {
            // Cache in Redis for faster future checks
            redisTemplate.opsForValue().set(redisKey, activeAlert.get().getId().toString(),
                Duration.ofMinutes(suppressionMinutes));
            log.debug("Duplicate detected in DB: {}", deduplicationKey);
            return true;
        }

        return false;
    }

    /**
     * Mark that an alert has been created for this key
     */
    public void markAlertCreated(String deduplicationKey, Long alertId) {
        String redisKey = DEDUP_PREFIX + deduplicationKey;
        redisTemplate.opsForValue().set(redisKey, alertId.toString(),
            Duration.ofMinutes(deduplicationWindowMinutes));

        String activeKey = ACTIVE_ALERT_PREFIX + deduplicationKey;
        redisTemplate.opsForValue().set(activeKey, alertId.toString(),
            Duration.ofHours(24));  // Keep for 24 hours

        log.debug("Marked alert {} for deduplication key: {}", alertId, deduplicationKey);
    }

    /**
     * Clear deduplication when alert is resolved
     */
    public void clearDeduplication(String deduplicationKey) {
        String redisKey = DEDUP_PREFIX + deduplicationKey;
        String activeKey = ACTIVE_ALERT_PREFIX + deduplicationKey;
        
        redisTemplate.delete(redisKey);
        redisTemplate.delete(activeKey);

        log.debug("Cleared deduplication for: {}", deduplicationKey);
    }

    /**
     * Get existing active alert ID for this key (if any)
     */
    public Optional<Long> getExistingAlertId(String deduplicationKey) {
        String activeKey = ACTIVE_ALERT_PREFIX + deduplicationKey;
        Object value = redisTemplate.opsForValue().get(activeKey);
        
        if (value != null) {
            return Optional.of(Long.parseLong(value.toString()));
        }
        return Optional.empty();
    }
}