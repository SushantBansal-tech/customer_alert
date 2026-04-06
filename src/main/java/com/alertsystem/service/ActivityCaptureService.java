package com.alertsystem.service;

import com.alertsystem.dto.LogEvent;
import com.alertsystem.entity.UserActivity;
import com.alertsystem.repository.UserActivityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class ActivityCaptureService {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityCaptureService.class);
    
    @Autowired
    private UserActivityRepository activityRepository;
    
    @Value("${app.batch.size:100}")
    private int batchSize;
    
    private final Queue<UserActivity> activityQueue = new ConcurrentLinkedQueue<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Capture activity from parsed log event
     */
    public void captureActivity(LogEvent logEvent) {
        
        if (logEvent.getUserId() == null || logEvent.getUserId().isEmpty()) {
            return; // Skip if no user_id
        }
        
        try {
            UserActivity activity = UserActivity.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(logEvent.getUserId()))
                .activityType("API_CALL")
                .status(logEvent.getStatus())
                .responseTime(logEvent.getResponseTime() != null ? logEvent.getResponseTime() : 0L)
                .severity(logEvent.getSeverity())
                .timestamp(parseTimestamp(logEvent.getTimestamp()))
                .details(buildDetails(logEvent))
                .build();
            
            activityQueue.offer(activity);
            
        } catch (Exception e) {
            logger.error("Error capturing activity: {}", logEvent, e);
        }
    }
    
    // Batch save every 5 seconds
    @Scheduled(fixedRate = 5000)
    public void processBatch() {
        List<UserActivity> batch = new ArrayList<>();
        UserActivity activity;
        
        while ((activity = activityQueue.poll()) != null) {
            batch.add(activity);
            if (batch.size() >= batchSize) break;
        }
        
        if (!batch.isEmpty()) {
            try {
                activityRepository.saveAll(batch);
                logger.debug("Saved {} activities to database", batch.size());
            } catch (Exception e) {
                logger.error("Error saving activity batch", e);
                // Re-add to queue
                batch.forEach(activityQueue::offer);
            }
        }
    }
    
    private JsonNode buildDetails(LogEvent event) {
        try {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("endpoint", event.getEndpoint());
            details.put("errorType", event.getErrorType());
            details.put("errorMessage", event.getErrorMessage());
            details.put("responseTime", event.getResponseTime());
            details.put("requestId", event.getRequestId());
            
            return details;
        } catch (Exception e) {
            logger.error("Error building details", e);
            return objectMapper.createObjectNode();
        }
    }
    
    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            if (timestamp == null || timestamp.isEmpty()) {
                return LocalDateTime.now();
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(timestamp, formatter);
        } catch (Exception e) {
            logger.error("Error parsing timestamp: {}", timestamp, e);
            return LocalDateTime.now();
        }
    }
    
    public int getQueueSize() {
        return activityQueue.size();
    }
}