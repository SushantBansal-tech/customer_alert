package com.alerting.sdk.client;

import com.alerting.sdk.AlertSDKProperties;
import com.alerting.sdk.model.AlertEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class AlertHttpClient {

    private final RestTemplate restTemplate;
    private final AlertSDKProperties properties;
    private final ConcurrentLinkedQueue<AlertEvent> eventBuffer;

    public AlertHttpClient(AlertSDKProperties properties) {
        this.properties = properties;
        this.restTemplate = createRestTemplate();
        this.eventBuffer = new ConcurrentLinkedQueue<>();
    }

    private RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        // Configure timeouts via ClientHttpRequestFactory if needed
        return template;
    }

    @Async("alertSdkExecutor")
    @CircuitBreaker(name = "alertPlatform", fallbackMethod = "sendEventFallback")
    @Retry(name = "alertPlatform")
    public CompletableFuture<Void> sendEventAsync(AlertEvent event) {
        if (!properties.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        if (properties.isAsync()) {
            eventBuffer.offer(event);
            
            // Flush if buffer is getting full
            if (eventBuffer.size() >= properties.getBufferSize()) {
                flushEvents();
            }
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> sendEvent(event));
    }

    public void sendEvent(AlertEvent event) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", properties.getApiKey());
        headers.set("X-App-Id", properties.getAppId());

        HttpEntity<AlertEvent> request = new HttpEntity<>(event, headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                properties.getApiUrl(),
                HttpMethod.POST,
                request,
                Void.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Failed to send event: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending event to alerting platform", e);
            throw e;
        }
    }

    @Scheduled(fixedRateString = "${alert.sdk.flush-interval-ms:5000}")
    public void flushEvents() {
        if (eventBuffer.isEmpty()) {
            return;
        }

        List<AlertEvent> batch = new ArrayList<>();
        AlertEvent event;
        
        while ((event = eventBuffer.poll()) != null && batch.size() < 100) {
            batch.add(event);
        }

        if (!batch.isEmpty()) {
            sendBatch(batch);
        }
    }

    @CircuitBreaker(name = "alertPlatform", fallbackMethod = "sendBatchFallback")
    @Retry(name = "alertPlatform")
    public void sendBatch(List<AlertEvent> events) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", properties.getApiKey());
        headers.set("X-App-Id", properties.getAppId());

        HttpEntity<List<AlertEvent>> request = new HttpEntity<>(events, headers);

        try {
            restTemplate.exchange(
                properties.getApiUrl() + "/batch",
                HttpMethod.POST,
                request,
                Void.class
            );
            log.debug("Sent batch of {} events", events.size());
        } catch (Exception e) {
            log.error("Failed to send batch, re-queuing events", e);
            events.forEach(eventBuffer::offer);
            throw e;
        }
    }

    // Fallback methods
    private CompletableFuture<Void> sendEventFallback(AlertEvent event, Throwable t) {
        log.warn("Circuit breaker open, buffering event: {}", event.getFeature());
        eventBuffer.offer(event);
        return CompletableFuture.completedFuture(null);
    }

    private void sendBatchFallback(List<AlertEvent> events, Throwable t) {
        log.warn("Circuit breaker open for batch, re-queuing {} events", events.size());
        events.forEach(eventBuffer::offer);
    }
}

