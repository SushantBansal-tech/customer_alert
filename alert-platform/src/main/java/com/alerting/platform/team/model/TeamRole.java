package com.alerting.platform.team.model;

public enum TeamRole {
    DEVELOPER,      // Regular team member
    SENIOR,         // Senior developer
    LEAD,           // Team lead - gets escalations
    MANAGER,        // Manager - gets critical alerts
    ADMIN           // Admin - full access
}