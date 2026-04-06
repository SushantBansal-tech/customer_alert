package com.alertsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_issue_id", columnList = "issue_id"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    
    @Id
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private UUID issueId;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = true)
    private LocalDateTime lastNotifiedAt;
    
    @Column(nullable = true)
    private LocalDateTime nextRecheckAt;
    
    @Column(nullable = false)
    private Integer notificationCount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (notificationCount == null) {
            notificationCount = 0;
        }
        if (status == null) {
            status = AlertStatus.PENDING;
        }
    }
}

enum AlertStatus {
    PENDING, SENT, ACKNOWLEDGED
}