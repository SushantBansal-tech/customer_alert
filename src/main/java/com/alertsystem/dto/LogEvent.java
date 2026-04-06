package com.alertsystem.dto;

import com.alertsystem.entity.Severity;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent {
    private String timestamp;           // 2026-04-05 10:15:35
    private String userId;              // user-123
    private String endpoint;            // /api/payments
    private String status;              // FAILED
    private Long responseTime;          // 3002 (milliseconds)
    private String errorType;           // PAYMENT_GATEWAY, DB_ERROR, etc.
    private String errorMessage;        // Connection timeout
    private Severity severity;          // CRITICAL, HIGH, MEDIUM
    private String requestId;           // Request tracking ID
}