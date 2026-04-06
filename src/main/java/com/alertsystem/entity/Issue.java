package com.alertsystem.entity;

import com.alertsystem.entity.IssueStatus;
import com.alertsystem.entity.Severity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "issues", indexes = {
    @Index(name = "idx_user_status", columnList = "user_id,status"),
    @Index(name = "idx_detected_at", columnList = "detected_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {
    
    @Id
    private UUID id;
    
    @Column(nullable = true)
    private UUID userId; // NULL for multi-user issues
    
    @Column(nullable = false)
    private String issueType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;
    
    @Column(nullable = false)
    private LocalDateTime detectedAt;
    
    @Column(nullable = true)
    private LocalDateTime resolvedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private Integer affectedUsersCount;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = IssueStatus.OPEN;
        }
        if (affectedUsersCount == null) {
            affectedUsersCount = 1;
        }
    }
}

