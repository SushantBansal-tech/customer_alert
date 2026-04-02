package com.alerting.platform.alerts.model;

public enum AlertStatus {
    // Initial States
    TRIGGERED,          // Just detected, not yet processed
    
    // Notification States
    NOTIFYING,          // Sending notifications
    NOTIFIED,           // Notifications sent successfully
    NOTIFICATION_FAILED,// Failed to send notifications
    
    // Assignment States
    UNASSIGNED,         // Awaiting assignment
    ASSIGNED,           // Assigned to team/person
    
    // Work States
    ACKNOWLEDGED,       // Someone is looking at it
    IN_PROGRESS,        // Actively being worked on
    PENDING_VERIFICATION,// Fix applied, awaiting confirmation
    
    // Terminal States
    RESOLVED,           // Issue fixed
    AUTO_RESOLVED,      // System detected fix automatically
    SUPPRESSED,         // Deduplicated / suppressed
    CLOSED,             // Closed without resolution
    
    // Escalation
    ESCALATED           // Escalated to higher level
}