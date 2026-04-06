package com.alertsystem.entity;

/**
 * Severity levels for issues and activities
 * Used to categorize the impact and urgency of problems
 */
public enum Severity {
    LOW,      // Minor issues, informational only
    MEDIUM,   // Moderate issues, should be addressed soon
    HIGH,     // Serious issues, need immediate attention
    CRITICAL  // Critical issues, system down or major functionality broken
}