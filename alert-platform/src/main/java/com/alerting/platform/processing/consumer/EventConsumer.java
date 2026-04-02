package com.alerting.platform.processing.consumer;

import com.alerting.platform.common.model.AlertEvent;
import com.alerting.platform.processing.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

    private final AggregationService aggregationService;

    @KafkaListener(
        topics = "${alerting.kafka.events-topic}",
        groupId = "alert-platform",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeEvent(AlertEvent event, Acknowledgment ack) {
        try {
            log.debug("Received event: app={}, feature={}, outcome={}",
                event.getAppId(), event.getFeature(), event.getOutcome());
            
            aggregationService.aggregate(event);
            
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing event: {}", event, e);
            throw e; // Will trigger DLQ
        }
    }
}

