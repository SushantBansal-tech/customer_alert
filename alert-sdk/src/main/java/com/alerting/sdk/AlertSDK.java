package com.alerting.sdk;

import com.alerting.sdk.client.AlertHttpClient;
import com.alerting.sdk.model.AlertEvent;
import com.alerting.sdk.model.AlertEvent.EventOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class AlertSDK {

    private final AlertHttpClient httpClient;
    private final AlertSDKProperties properties;

    /**
     * Track a successful operation
     */
    public CompletableFuture<Void> trackSuccess(String feature, String userId) {
        return trackSuccess(feature, userId, null);
    }

    public CompletableFuture<Void> trackSuccess(String feature, String userId, 
                                                 Map<String, String> metadata) {
        AlertEvent event = AlertEvent.builder()
            .appId(properties.getAppId())
            .userId(userId)
            .sessionId(getSessionId())
            .feature(feature)
            .eventType("SUCCESS")
            .outcome(EventOutcome.SUCCESS)
            .timestamp(Instant.now())
            .metadata(metadata)
            .build();

        return httpClient.sendEventAsync(event);
    }

    /**
     * Track a failed operation
     */
    public CompletableFuture<Void> trackFailure(String feature, String eventType, 
                                                 String userId) {
        return trackFailure(feature, eventType, userId, null);
    }

    public CompletableFuture<Void> trackFailure(String feature, String eventType,
                                                 String userId, Map<String, String> metadata) {
        AlertEvent event = AlertEvent.builder()
            .appId(properties.getAppId())
            .userId(userId)
            .sessionId(getSessionId())
            .feature(feature)
            .eventType(eventType)
            .outcome(EventOutcome.FAILURE)
            .timestamp(Instant.now())
            .metadata(metadata)
            .build();

        return httpClient.sendEventAsync(event);
    }

    /**
     * Track operation latency
     */
    public CompletableFuture<Void> trackLatency(String feature, long durationMs, 
                                                 String userId) {
        return trackLatency(feature, durationMs, userId, null);
    }

    public CompletableFuture<Void> trackLatency(String feature, long durationMs,
                                                 String userId, Map<String, String> metadata) {
        AlertEvent event = AlertEvent.builder()
            .appId(properties.getAppId())
            .userId(userId)
            .sessionId(getSessionId())
            .feature(feature)
            .eventType("LATENCY")
            .outcome(EventOutcome.SUCCESS)
            .latencyMs(durationMs)
            .timestamp(Instant.now())
            .metadata(metadata)
            .build();

        return httpClient.sendEventAsync(event);
    }

    /**
     * Track with timing - measures execution time automatically
     */
    public <T> T trackWithTiming(String feature, String userId, 
                                  ThrowingSupplier<T> operation) throws Exception {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = operation.get();
            long duration = System.currentTimeMillis() - startTime;
            
            trackSuccess(feature, userId, Map.of("duration_ms", String.valueOf(duration)));
            trackLatency(feature, duration, userId);
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            trackFailure(feature, e.getClass().getSimpleName(), userId,
                Map.of("duration_ms", String.valueOf(duration), 
                       "error", e.getMessage() != null ? e.getMessage() : "Unknown"));
            
            throw e;
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private String getSessionId() {
        // In real implementation, get from ThreadLocal or context
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

