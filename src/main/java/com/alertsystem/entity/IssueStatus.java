package com.alertsystem.entity;

/**
 * Status lifecycle of an issue
 * Tracks the state of an issue from detection to resolution
 */
public enum IssueStatus {
    OPEN,         // Issue just detected, waiting for assignment
    ASSIGNED,     // Issue assigned to a developer/team, not started yet
    IN_PROGRESS,  // Someone is actively working on fixing the issue
    RESOLVED      // Issue has been fixed and confirmed
}