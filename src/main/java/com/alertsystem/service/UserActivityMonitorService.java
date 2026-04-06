package com.alertsystem.service;

import com.alertsystem.entity.*;
import com.alertsystem.repository.IssueRepository;
import com.alertsystem.repository.UserActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserActivityMonitorService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserActivityMonitorService.class);
    
    @Autowired
    private UserActivityRepository activityRepository;
    
    @Autowired
    private IssueService issueService;
    
    @Value("${app.error.threshold:20}")
    private int errorThreshold; // 20% errors
    
    @Value("${app.critical.error.count:5}")
    private int criticalErrorCount; // 5 critical errors = alert
    
    @Value("${app.activity.window:5}")
    private int activityWindow; // Last 5 minutes
    
    @Scheduled(fixedRateString = "${app.monitoring.interval:30000}") // Every 30 seconds
    public void analyzeLogEvents() {
        
        logger.debug("Starting activity analysis cycle");
        
        LocalDateTime since = LocalDateTime.now().minusMinutes(activityWindow);
        
        // 1. Single-user analysis
        List<UUID> usersWithFailures = activityRepository.findUsersWithFailedActivities(since);
        logger.debug("Found {} users with failures in last {} minutes", usersWithFailures.size(), activityWindow);
        
        for (UUID userId : usersWithFailures) {
            analyzeUserFailures(userId, since);
        }
        
        // 2. Multi-user aggregate analysis
        analyzeSystemWideFailures(since);
    }
    
    private void analyzeUserFailures(UUID userId, LocalDateTime since) {
        
        // Get failed activities for this user
        List<UserActivity> failedActivities = activityRepository
            .findFailedActivitiesByUser(userId, since);
        
        if (failedActivities.isEmpty()) {
            return;
        }
        
        logger.debug("User {} has {} failed activities", userId, failedActivities.size());
        
        // Count CRITICAL failures
        long criticalCount = failedActivities.stream()
            .filter(a -> a.getSeverity() == Severity.CRITICAL)
            .count();
        
        if (criticalCount >= criticalErrorCount) {
            logger.warn("User {} has {} CRITICAL errors", userId, criticalCount);
            issueService.createOrUpdateIssue(
                userId,
                "HIGH_CRITICAL_ERRORS",
                String.format("%d critical errors in last %d minutes", criticalCount, activityWindow),
                Severity.CRITICAL,
                failedActivities
            );
            return;
        }
        
        // Calculate error rate
        List<UserActivity> allActivities = activityRepository
            .findByUserIdAndTimestampAfter(userId, since);
        
        if (allActivities.isEmpty()) {
            return;
        }
        
        double errorRate = (double) failedActivities.size() / allActivities.size() * 100;
        
        logger.debug("User {} error rate: {:.1f}%", userId, errorRate);
        
        if (errorRate > errorThreshold) {
            logger.warn("User {} exceeds error threshold: {:.1f}%", userId, errorRate);
            issueService.createOrUpdateIssue(
                userId,
                "HIGH_ERROR_RATE",
                String.format("Error rate: %.1f%% (%d/%d failures)", 
                             errorRate, failedActivities.size(), allActivities.size()),
                Severity.HIGH,
                failedActivities
            );
        }
    }
    
    private void analyzeSystemWideFailures(LocalDateTime since) {
        
        // Get all failed activities system-wide
        List<UserActivity> allFailures = activityRepository.findFailedActivities(since);
        
        if (allFailures.isEmpty()) {
            return;
        }
        
        // Count unique affected users
        long affectedUsers = allFailures.stream()
            .map(UserActivity::getUserId)
            .distinct()
            .count();
        
        // Count CRITICAL failures
        long criticalFailures = allFailures.stream()
            .filter(a -> a.getSeverity() == Severity.CRITICAL)
            .count();
        
        logger.debug("System-wide: {} users affected, {} critical errors", affectedUsers, criticalFailures);
        
        // If > 50% of users are affected OR critical errors spike
        if (affectedUsers > 10 && criticalFailures > 20) {
            logger.warn("SYSTEM-WIDE FAILURE DETECTED: {} users, {} critical errors", affectedUsers, criticalFailures);
            issueService.createOrUpdateMultiUserIssue(
                "SYSTEM_WIDE_FAILURES",
                String.format("%d users affected, %d critical errors", affectedUsers, criticalFailures),
                (int) affectedUsers
            );
        }
    }
}