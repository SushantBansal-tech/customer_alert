package com.alerting.platform.processing.consumer;

import com.alerting.platform.common.model.AlertEvent;
import com.alerting.platform.processing.service.AggregationService;
import com.alerting.platform.processing.service.UserLevelAggregationService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

    private final AggregationService aggregationService;
    private final UserLevelAggregationService userLevelAggregationService;

    // Statistics counters
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong successEventsProcessed = new AtomicLong(0);
    private final AtomicLong failureEventsProcessed = new AtomicLong(0);
    private final AtomicLong processingErrors = new AtomicLong(0);
    private Instant startTime;

    @PostConstruct
    public void init() {
        startTime = Instant.now();
        log.info("========================================");
        log.info("🚀 EVENT CONSUMER INITIALIZED");
        log.info("   Started at: {}", startTime);
        log.info("========================================");
    }

    @KafkaListener(
        topics = "${alerting.kafka.events-topic}",
        groupId = "alert-platform",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeEvent(AlertEvent event, Acknowledgment ack) {
        try {
            log.debug("📥 Received event: app={}, feature={}, user={}, outcome={}",
                event.getAppId(), 
                event.getFeature(), 
                event.getUserId(),
                event.getOutcome());

            // 1. Update aggregate metrics (for system-wide monitoring)
            aggregationService.aggregate(event);

            // 2. Update user-level metrics (for individual monitoring)
            if (event.getUserId() != null) {
                userLevelAggregationService.trackUserEvent(event);
            }

            // Update statistics
            totalEventsProcessed.incrementAndGet();
            if (event.getOutcome() == AlertEvent.EventOutcome.FAILURE) {
                failureEventsProcessed.incrementAndGet();
            } else {
                successEventsProcessed.incrementAndGet();
            }

            // Acknowledge the message
            ack.acknowledge();

        } catch (Exception e) {
            processingErrors.incrementAndGet();
            log.error("❌ Error processing event: {}", event, e);
            throw e; // Will trigger DLQ
        }
    }

    /**
     * Get consumer statistics
     */
    public ConsumerStats getStats() {
        long uptimeSeconds = java.time.Duration.between(startTime, Instant.now()).getSeconds();
        double eventsPerSecond = uptimeSeconds > 0 ? 
            (double) totalEventsProcessed.get() / uptimeSeconds : 0;

        return ConsumerStats.builder()
            .totalEventsProcessed(totalEventsProcessed.get())
            .successEventsProcessed(successEventsProcessed.get())
            .failureEventsProcessed(failureEventsProcessed.get())
            .processingErrors(processingErrors.get())
            .startTime(startTime)
            .uptimeSeconds(uptimeSeconds)
            .eventsPerSecond(eventsPerSecond)
            .build();
    }

    /**
     * Reset statistics (for testing purposes)
     */
    public void resetStats() {
        totalEventsProcessed.set(0);
        successEventsProcessed.set(0);
        failureEventsProcessed.set(0);
        processingErrors.set(0);
        startTime = Instant.now();
    }

    @Data
    @Builder
    public static class ConsumerStats {
        private long totalEventsProcessed;
        private long successEventsProcessed;
        private long failureEventsProcessed;
        private long processingErrors;
        private Instant startTime;
        private long uptimeSeconds;
        private double eventsPerSecond;
    }
}