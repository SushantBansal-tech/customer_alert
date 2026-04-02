package com.alerting.platform.processing.service;

import com.alerting.platform.common.model.AlertEvent;
import com.alerting.platform.common.model.AlertEvent.EventOutcome;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserLevelAggregationService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${alerting.user-level.window-seconds:300}")
    private int windowSeconds;

    @Value("${alerting.user-level.max-tracked-users:10000}")
    private int maxTrackedUsers;

    private static final String USER_FAILURES_KEY = "user:failures:";
    private static final String USER_CONSECUTIVE_KEY = "user:consecutive:";
    private static final String USER_LATENCY_KEY = "user:latency:";
    private static final String ACTIVE_USERS_KEY = "active:users:";

    public void trackUserEvent(AlertEvent event) {
        String userKey = buildUserKey(event.getAppId(), event.getFeature(), event.getUserId());

        if (event.getOutcome() == EventOutcome.FAILURE) {
            // Track failure in time window
            trackFailure(userKey, event);
            
            // Track consecutive failures
            incrementConsecutiveFailures(userKey);
            
            // Add to active users set for this feature
            trackActiveUser(event.getAppId(), event.getFeature(), event.getUserId());
        } else {
            // Reset consecutive failures on success
            resetConsecutiveFailures(userKey);
        }

        // Track latency if present
        if (event.getLatencyMs() != null) {
            trackLatency(userKey, event.getLatencyMs());
        }
    }

    private void trackFailure(String userKey, AlertEvent event) {
        String failuresKey = USER_FAILURES_KEY + userKey;
        long timestamp = event.getTimestamp().toEpochMilli();
        
        // Add timestamp to sorted set (for windowed counting)
        redisTemplate.opsForZSet().add(failuresKey, String.valueOf(timestamp), timestamp);
        
        // Remove old entries outside window
        long windowStart = Instant.now().minusSeconds(windowSeconds).toEpochMilli();
        redisTemplate.opsForZSet().removeRangeByScore(failuresKey, 0, windowStart);
        
        // Set expiry
        redisTemplate.expire(failuresKey, Duration.ofSeconds(windowSeconds * 2L));
    }

    private void incrementConsecutiveFailures(String userKey) {
        String consecutiveKey = USER_CONSECUTIVE_KEY + userKey;
        redisTemplate.opsForValue().increment(consecutiveKey);
        redisTemplate.expire(consecutiveKey, Duration.ofSeconds(windowSeconds * 2L));
    }

    private void resetConsecutiveFailures(String userKey) {
        String consecutiveKey = USER_CONSECUTIVE_KEY + userKey;
        redisTemplate.delete(consecutiveKey);
    }

    private void trackLatency(String userKey, Long latencyMs) {
        String latencyKey = USER_LATENCY_KEY + userKey;
        redisTemplate.opsForList().rightPush(latencyKey, latencyMs);
        redisTemplate.opsForList().trim(latencyKey, -100, -1);  // Keep last 100
        redisTemplate.expire(latencyKey, Duration.ofSeconds(windowSeconds * 2L));
    }

    private void trackActiveUser(String appId, String feature, String userId) {
        String activeUsersKey = ACTIVE_USERS_KEY + appId + ":" + feature;
        redisTemplate.opsForSet().add(activeUsersKey, userId);
        redisTemplate.expire(activeUsersKey, Duration.ofSeconds(windowSeconds * 2L));
    }

    public UserMetrics getUserMetrics(String appId, String feature, String userId) {
        String userKey = buildUserKey(appId, feature, userId);
        
        // Get failures in window
        String failuresKey = USER_FAILURES_KEY + userKey;
        long windowStart = Instant.now().minusSeconds(windowSeconds).toEpochMilli();
        Long failuresInWindow = redisTemplate.opsForZSet().count(failuresKey, windowStart, Double.MAX_VALUE);
        
        // Get consecutive failures
        String consecutiveKey = USER_CONSECUTIVE_KEY + userKey;
        Object consecutiveValue = redisTemplate.opsForValue().get(consecutiveKey);
        int consecutiveFailures = consecutiveValue != null ? 
            Integer.parseInt(consecutiveValue.toString()) : 0;
        
        // Get latency metrics
        String latencyKey = USER_LATENCY_KEY + userKey;
        List<Object> latencies = redisTemplate.opsForList().range(latencyKey, 0, -1);
        Double avgLatency = null;
        Double maxLatency = null;
        
        if (latencies != null && !latencies.isEmpty()) {
            List<Long> latencyList = latencies.stream()
                .map(o -> Long.parseLong(o.toString()))
                .toList();
            avgLatency = latencyList.stream().mapToLong(l -> l).average().orElse(0);
            maxLatency = (double) latencyList.stream().mapToLong(l -> l).max().orElse(0);
        }

        return UserMetrics.builder()
            .appId(appId)
            .feature(feature)
            .userId(userId)
            .failuresInWindow(failuresInWindow != null ? failuresInWindow.intValue() : 0)
            .consecutiveFailures(consecutiveFailures)
            .windowSeconds(windowSeconds)
            .avgLatencyMs(avgLatency)
            .maxLatencyMs(maxLatency)
            .timestamp(Instant.now())
            .build();
    }

    public Set<String> getActiveUsers(String appId, String feature) {
        String activeUsersKey = ACTIVE_USERS_KEY + appId + ":" + feature;
        Set<Object> users = redisTemplate.opsForSet().members(activeUsersKey);
        if (users == null) return Set.of();
        
        Set<String> result = new HashSet<>();
        for (Object user : users) {
            result.add(user.toString());
        }
        return result;
    }

    private String buildUserKey(String appId, String feature, String userId) {
        return appId + ":" + feature + ":" + userId;
    }

    @Data
    @Builder
    public static class UserMetrics {
        private String appId;
        private String feature;
        private String userId;
        private int failuresInWindow;
        private int consecutiveFailures;
        private int windowSeconds;
        private Double avgLatencyMs;
        private Double maxLatencyMs;
        private Instant timestamp;
    }
}