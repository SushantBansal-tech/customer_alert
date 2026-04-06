package com.alertsystem.controller;

import com.alertsystem.entity.Issue;
import com.alertsystem.entity.IssueHistory;
import com.alertsystem.entity.IssueStatus;
import com.alertsystem.service.IssueService;
import com.alertsystem.repository.IssueHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/issues")
@CrossOrigin(origins = "*")
public class IssueController {
    
    @Autowired
    private IssueService issueService;
    
    @Autowired
    private IssueHistoryRepository historyRepository;
    
    @GetMapping
    public ResponseEntity<List<Issue>> getOpenIssues() {
        List<Issue> issues = issueService.getOpenIssues();
        return ResponseEntity.ok(issues);
    }
    
    @GetMapping("/{issueId}")
    public ResponseEntity<Issue> getIssueDetails(@PathVariable UUID issueId) {
        Issue issue = issueService.getIssueDetails(issueId);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(issue);
    }
    
    @GetMapping("/{issueId}/history")
    public ResponseEntity<List<IssueHistory>> getIssueHistory(@PathVariable UUID issueId) {
        List<IssueHistory> history = historyRepository.findByIssueIdOrderByChangedAtDesc(issueId);
        return ResponseEntity.ok(history);
    }
    
    @PutMapping("/{issueId}/status")
    public ResponseEntity<?> updateIssueStatus(
            @PathVariable UUID issueId,
            @RequestParam IssueStatus status,
            @RequestParam(required = false) String notes) {
        
        issueService.updateIssueStatus(issueId, status, null, notes);
        return ResponseEntity.ok("Issue status updated");
    }
}