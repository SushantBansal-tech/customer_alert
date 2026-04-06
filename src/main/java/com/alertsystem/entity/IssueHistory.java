
package com.alertsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "issue_history", indexes = {
    @Index(name = "idx_issue_id", columnList = "issue_id"),
    @Index(name = "idx_changed_at", columnList = "changed_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueHistory {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID issueId;
    
    @Column(nullable = true)
    private String oldStatus;
    
    @Column(nullable = false)
    private String newStatus;
    
    @Column(nullable = true)
    private UUID changedByUserId;
    
    @Column(nullable = false)
    private LocalDateTime changedAt;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }
}