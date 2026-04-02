package com.alerting.platform.processing.service;

import com.alerting.platform.common.model.AlertEvent;
import com.alerting.platform.common.model.AlertEvent.EventOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AggregationService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${alerting.aggregation.window-size-seconds}")
    private int windowSizeSeconds;

    @Value("${alerting.aggregation.bucket-size-seconds}")
    private int bucketSizeSeconds;

    private static final String TOTAL_SUFFIX = ":total";
    private static final String FAIL_SUFFIX = ":fail";
    private static final String LATENCY_SUFFIX = ":latency";

    public void aggregate(AlertEvent event) {
        String baseKey = buildBaseKey(event);
        long bucketTimestamp = getBucketTimestamp(event.getTimestamp());
        String bucketKey = baseKey + ":" + bucketTimestamp;

        // Increment total counter
        redisTemplate.opsForValue().increment(bucketKey + TOTAL_SUFFIX);
        redisTemplate.expire(bucketKey + TOTAL_SUFFIX, Duration.ofSeconds(windowSizeSeconds * 2L));

        // Increment failure counter if applicable
        if (event.getOutcome() == EventOutcome.FAILURE) {
            redisTemplate.opsForValue().increment(bucketKey + FAIL_SUFFIX);
            redisTemplate.expire(bucketKey + FAIL_SUFFIX, Duration.ofSeconds(windowSizeSeconds * 2L));
            
            // Track by error type
            String errorKey = bucketKey + ":error:" + event.getEventType();
            redisTemplate.opsForValue().increment(errorKey);
            redisTemplate.expire(errorKey, Duration.ofSeconds(windowSizeSeconds * 2L));
        }

        // Track latency if present
        if (event.getLatencyMs() != null) {
            String latencyKey = bucketKey + LATENCY_SUFFIX;
            redisTemplate.opsForList().rightPush(latencyKey, event.getLatencyMs());
            redisTemplate.expire(latencyKey, Duration.ofSeconds(windowSizeSeconds * 2L));
        }

        // Track active feature keys for rule evaluation
        trackActiveFeature(event.getAppId(), event.getFeature());
    }

    public AggregatedMetrics getMetrics(String appId, String feature) {
        String baseKey = buildBaseKey(appId, feature);
        long currentBucket = getBucketTimestamp(Instant.now());
        int bucketsToCheck = windowSizeSeconds / bucketSizeSeconds;

        long totalCount = 0;
        long failCount = 0;
        Map<String, Long> errorCounts = new HashMap<>();

        for (int i = 0; i < bucketsToCheck; i++) {
            long bucketTs = currentBucket - (i * bucketSizeSeconds);
            String bucketKey = baseKey + ":" + bucketTs;

            Long total = getLongValue(bucketKey + TOTAL_SUFFIX);
            Long fail = getLongValue(bucketKey + FAIL_SUFFIX);

            totalCount += total;
            failCount += fail;
        }

        double failureRate = totalCount > 0 ? (double) failCount / totalCount * 100 : 0;

        return AggregatedMetrics.builder()
            .appId(appId)
            .feature(feature)
            .windowSeconds(windowSizeSeconds)
            .totalCount(totalCount)
            .failureCount(failCount)
            .failureRate(failureRate)
            .errorBreakdown(errorCounts)
            .timestamp(Instant.now())
            .build();
    }

    public Set<String> getActiveFeatures(String appId) {
        String key = "active:features:" + appId;
        return redisTemplate.opsForSet().members(key) != null ?
            (Set<String>) (Set<?>) redisTemplate.opsForSet().members(key) :
            Set.of();
    }

    private void trackActiveFeature(String appId, String feature) {
        String key = "active:features:" + appId;
        redisTemplate.opsForSet().add(key, feature);
        redisTemplate.expire(key, Duration.ofSeconds(windowSizeSeconds * 2L));
    }

    private String buildBaseKey(AlertEvent event) {
        return buildBaseKey(event.getAppId(), event.getFeature());
    }

    private String buildBaseKey(String appId, String feature) {
        return "metrics:" + appId + ":" + feature;
    }

    private long getBucketTimestamp(Instant instant) {
        long epochSeconds = instant.getEpochSecond();
        return epochSeconds - (epochSeconds % bucketSizeSeconds);
    }

    private Long getLongValue(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return 0L;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Long) return (Long) value;
        return Long.parseLong(value.toString());
    }

    @lombok.Builder
    @lombok.Data
    public static class AggregatedMetrics {
        private String appId;
        private String feature;
        private int windowSeconds;
        private long totalCount;
        private long failureCount;
        private double failureRate;
        private Map<String, Long> errorBreakdown;
        private Double p50Latency;
        private Double p99Latency;
        private Instant timestamp;
    }
}

