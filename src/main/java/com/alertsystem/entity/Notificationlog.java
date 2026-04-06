package com.alertsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {
    
    @Id
    private UUID id;
    
    @Column(nullable = true)
    private UUID alertId;
    
    @Column(nullable = false)
    private UUID recipientUserId;
    
    @Column(nullable = false)
    private String notificationType; // EMAIL, IN_APP, SMS
    
    @Column(nullable = false)
    private LocalDateTime sentAt;
    
    @Column(nullable = false)
    private String status; // SUCCESS, FAILED
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}