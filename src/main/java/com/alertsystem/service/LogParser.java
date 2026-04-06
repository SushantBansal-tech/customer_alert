package com.alertsystem.service;

import com.alertsystem.dto.LogEvent;
import com.alertsystem.entity.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogParser {
    
    private static final Logger logger = LoggerFactory.getLogger(LogParser.class);
    
    /**
     * Parse error log line and extract structured data
     * Example format:
     * [2026-04-05 10:15:35] ERROR [REQUEST_END] [USER_ID:user-123] [REQUEST_ID:abc-def-123]
     * [ENDPOINT:/api/payments] [STATUS:FAILED] [DURATION:3002ms] [ERROR_TYPE:PAYMENT_GATEWAY] [MESSAGE:Connection timeout]
     */
    public static LogEvent parseFailureLog(String logLine) {
        try {
            if (logLine == null || logLine.isEmpty()) {
                return null;
            }
            
            LogEvent event = new LogEvent();
            
            // Extract timestamp
            String timestamp = extractValue(logLine, "\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\]");
            if (timestamp != null) {
                event.setTimestamp(timestamp);
            }
            
            // Extract user_id
            String userId = extractValue(logLine, "\\[USER_ID:([^\\]]+)\\]");
            if (userId != null && !userId.isEmpty()) {
                event.setUserId(userId);
            }
            
            // Extract endpoint
            String endpoint = extractValue(logLine, "\\[ENDPOINT:([^\\]]+)\\]");
            if (endpoint != null) {
                event.setEndpoint(endpoint);
            }
            
            // Extract status
            String status = extractValue(logLine, "\\[STATUS:([^\\]]+)\\]");
            if (status != null) {
                event.setStatus(status);
            }
            
            // Extract duration (in ms)
            String durationStr = extractValue(logLine, "\\[DURATION:(\\d+)ms\\]");
            if (durationStr != null) {
                event.setResponseTime(Long.parseLong(durationStr));
            }
            
            // Extract error type
            String errorType = extractValue(logLine, "\\[ERROR_TYPE:([^\\]]+)\\]");
            if (errorType != null) {
                event.setErrorType(errorType);
            }
            
            // Extract error message
            String message = extractValue(logLine, "\\[MESSAGE:([^\\]]+)\\]");
            if (message != null) {
                event.setErrorMessage(message);
            }
            
            // Extract request ID
            String requestId = extractValue(logLine, "\\[REQUEST_ID:([^\\]]+)\\]");
            if (requestId != null) {
                event.setRequestId(requestId);
            }
            
            // Determine severity
            event.setSeverity(determineSeverity(errorType, event.getResponseTime()));
            
            return event;
            
        } catch (Exception e) {
            logger.error("Error parsing log line: {}", logLine, e);
            return null;
        }
    }
    
    private static String extractValue(String text, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            logger.error("Regex extraction error: {}", regex, e);
        }
        return null;
    }
    
    private static Severity determineSeverity(String errorType, Long responseTime) {
        if (errorType == null) {
            return Severity.MEDIUM;
        }
        
        if (errorType.contains("DB_ERROR") || errorType.contains("PAYMENT_GATEWAY") || 
            errorType.contains("CRITICAL")) {
            return Severity.CRITICAL;
        }
        
        if (errorType.contains("TIMEOUT")) {
            return responseTime != null && responseTime > 5000 ? Severity.CRITICAL : Severity.HIGH;
        }
        
        if (errorType.contains("ERROR")) {
            return Severity.HIGH;
        }
        
        return Severity.MEDIUM;
    }
}