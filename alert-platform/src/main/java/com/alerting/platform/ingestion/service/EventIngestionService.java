package com.alerting.platform.ingestion.service;

import com.alerting.platform.common.model.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventIngestionService {

    private final KafkaTemplate<String, AlertEvent> kafkaTemplate;

    @Value("${alerting.kafka.events-topic}")
    private String eventsTopic;

    public void ingestEvent(AlertEvent event) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }

        String key = generatePartitionKey(event);
        
        CompletableFuture<SendResult<String, AlertEvent>> future = 
            kafkaTemplate.send(eventsTopic, key, event);
        
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send event to Kafka: {}", event, ex);
            } else {
                log.debug("Event sent to partition {} with offset {}",
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }

    public int ingestBatch(List<AlertEvent> events) {
        events.forEach(this::ingestEvent);
        return events.size();
    }

    private String generatePartitionKey(AlertEvent event) {
        // Partition by app + feature for locality
        return event.getAppId() + ":" + event.getFeature();
    }
}

