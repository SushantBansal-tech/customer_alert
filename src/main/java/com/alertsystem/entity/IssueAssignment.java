package com.alertsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "issue_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueAssignment {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID issueId;
    
    @Column(nullable = false)
    private UUID assignedToUserId;
    
    @Column(nullable = false)
    private LocalDateTime assignedAt;
    
    @Column(nullable = false)
    private String assignmentType; // DEVELOPER, OPERATIONS, ADMIN
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }
}