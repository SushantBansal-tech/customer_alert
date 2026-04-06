package com.alertsystem.service;

import com.alertsystem.entity.*;
import com.alertsystem.repository.IssueAssignmentRepository;
import com.alertsystem.repository.IssueHistoryRepository;
import com.alertsystem.repository.IssueRepository;
import com.alertsystem.repository.UserActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class IssueService {
    
    private static final Logger logger = LoggerFactory.getLogger(IssueService.class);
    
    @Autowired
    private IssueRepository issueRepository;
    
    @Autowired
    private IssueHistoryRepository historyRepository;
    
    @Autowired
    private IssueAssignmentRepository assignmentRepository;
    
    @Autowired
    private AlertManagementService alertService;
    
    @Value("${app.activity.window:5}")
    private int activityWindow;
    
    /**
     * Create or update issue for single user
     */
    public void createOrUpdateIssue(UUID userId, String issueType, String description,
                                   Severity severity, List<UserActivity> relatedActivities) {
        
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(activityWindow);
            
            // Check if issue already exists (no spam)
            Optional<Issue> existingIssue = issueRepository.findRecentOpenIssue(userId, issueType, since);
            
            if (existingIssue.isPresent()) {
                logger.debug("Issue already exists for user {} type {}, skipping creation", userId, issueType);
                return;
            }
            
            // Create new issue
            Issue issue = Issue.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .issueType(issueType)
                .severity(severity)
                .description(description)
                .status(IssueStatus.OPEN)
                .affectedUsersCount(1)
                .build();
            
            Issue savedIssue = issueRepository.save(issue);
            logger.info("Created issue {} for user {} with type {}", savedIssue.getId(), userId, issueType);
            
            // Create alert for this issue
            alertService.createAlert(savedIssue);
            
            // Auto-assign to operations team
            autoAssignIssue(savedIssue);
            
        } catch (Exception e) {
            logger.error("Error creating issue for user {}", userId, e);
        }
    }
    
    /**
     * Create multi-user issue (system-wide)
     */
    public void createOrUpdateMultiUserIssue(String issueType, String description, int affectedUsers) {
        
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(activityWindow);
            
            // Check if multi-user issue exists
            List<Issue> existingIssues = issueRepository.findRecentMultiUserIssues(since);
            
            for (Issue existing : existingIssues) {
                if (existing.getIssueType().equals(issueType) && existing.getStatus() == IssueStatus.OPEN) {
                    logger.debug("Multi-user issue already exists for type {}, skipping creation", issueType);
                    return;
                }
            }
            
            // Create new multi-user issue
            Issue issue = Issue.builder()
                .id(UUID.randomUUID())
                .userId(null) // NULL for multi-user
                .issueType(issueType)
                .severity(Severity.CRITICAL)
                .description(description)
                .status(IssueStatus.OPEN)
                .affectedUsersCount(affectedUsers)
                .build();
            
            Issue savedIssue = issueRepository.save(issue);
            logger.warn("Created SYSTEM-WIDE issue {} affecting {} users", savedIssue.getId(), affectedUsers);
            
            // Create alert
            alertService.createAlert(savedIssue);
            
            // Auto-assign to admin
            autoAssignMultiUserIssue(savedIssue);
            
        } catch (Exception e) {
            logger.error("Error creating multi-user issue", e);
        }
    }
    
    /**
     * Check if recent issue exists for same type (deduplication)
     */
    public boolean checkRecentIssue(UUID userId, String issueType) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(activityWindow);
        Optional<Issue> issue = issueRepository.findRecentOpenIssue(userId, issueType, since);
        return issue.isPresent();
    }
    
    /**
     * Auto-assign issue to operations team
     */
    private void autoAssignIssue(Issue issue) {
        try {
            // In real implementation, fetch actual operations user from DB
            UUID operationsUserId = UUID.randomUUID(); // Placeholder
            
            IssueAssignment assignment = IssueAssignment.builder()
                .id(UUID.randomUUID())
                .issueId(issue.getId())
                .assignedToUserId(operationsUserId)
                .assignmentType("OPERATIONS")
                .build();
            
            assignmentRepository.save(assignment);
            
            // Update issue status
            issue.setStatus(IssueStatus.ASSIGNED);
            issueRepository.save(issue);
            
            recordHistory(issue, IssueStatus.OPEN, IssueStatus.ASSIGNED, null, "Auto-assigned to operations");
            
        } catch (Exception e) {
            logger.error("Error auto-assigning issue", e);
        }
    }
    
    /**
     * Auto-assign multi-user issue to admin
     */
    private void autoAssignMultiUserIssue(Issue issue) {
        try {
            UUID adminUserId = UUID.randomUUID(); // Placeholder
            
            IssueAssignment assignment = IssueAssignment.builder()
                .id(UUID.randomUUID())
                .issueId(issue.getId())
                .assignedToUserId(adminUserId)
                .assignmentType("ADMIN")
                .build();
            
            assignmentRepository.save(assignment);
            
            issue.setStatus(IssueStatus.ASSIGNED);
            issueRepository.save(issue);
            
            recordHistory(issue, IssueStatus.OPEN, IssueStatus.ASSIGNED, null, "Auto-assigned to admin");
            
        } catch (Exception e) {
            logger.error("Error auto-assigning multi-user issue", e);
        }
    }
    
    /**
     * Update issue status and record history
     */
    public void updateIssueStatus(UUID issueId, IssueStatus newStatus, UUID changedByUserId, String notes) {
        try {
            Optional<Issue> issueOpt = issueRepository.findById(issueId);
            
            if (issueOpt.isEmpty()) {
                logger.warn("Issue {} not found", issueId);
                return;
            }
            
            Issue issue = issueOpt.get();
            IssueStatus oldStatus = issue.getStatus();
            
            issue.setStatus(newStatus);
            
            if (newStatus == IssueStatus.RESOLVED) {
                issue.setResolvedAt(LocalDateTime.now());
            }
            
            issueRepository.save(issue);
            recordHistory(issue, oldStatus, newStatus, changedByUserId, notes);
            
            logger.info("Issue {} status updated from {} to {}", issueId, oldStatus, newStatus);
            
        } catch (Exception e) {
            logger.error("Error updating issue status", e);
        }
    }
    
    /**
     * Record status change in history
     */
    private void recordHistory(Issue issue, IssueStatus oldStatus, IssueStatus newStatus, 
                              UUID changedByUserId, String notes) {
        try {
            IssueHistory history = IssueHistory.builder()
                .id(UUID.randomUUID())
                .issueId(issue.getId())
                .oldStatus(oldStatus.toString())
                .newStatus(newStatus.toString())
                .changedByUserId(changedByUserId)
                .notes(notes)
                .build();
            
            historyRepository.save(history);
        } catch (Exception e) {
            logger.error("Error recording issue history", e);
        }
    }
    
    public Issue getIssueDetails(UUID issueId) {
        return issueRepository.findById(issueId).orElse(null);
    }
    
    public List<Issue> getOpenIssues() {
        return issueRepository.findByStatus(IssueStatus.OPEN);
    }
}