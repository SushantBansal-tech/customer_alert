package com.alertsystem.entity;

/**
 * Status of an alert notification
 * Tracks whether an alert has been sent and acknowledged
 */
public enum AlertStatus {
    PENDING,      // Alert created but notification not yet sent
    SENT,         // Alert notification has been sent to team
    ACKNOWLEDGED  // Alert received and acknowledged by team / Issue resolved
}