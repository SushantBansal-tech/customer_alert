package com.alertsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_activity", indexes = {
    @Index(name = "idx_user_timestamp", columnList = "user_id,timestamp"),
    @Index(name = "idx_status_timestamp", columnList = "status,timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivity {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private String activityType; // API_CALL, DB_QUERY, etc.
    
    @Column(nullable = false)
    private String status; // SUCCESS, FAILED
    
    @Column(nullable = false)
    private Long responseTime; // milliseconds
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode details;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}

enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}