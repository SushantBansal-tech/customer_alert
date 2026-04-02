package com.alerting.platform.app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "registered_apps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredApp {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String appId;
    
    @Column(unique = true, nullable = false)
    private String apiKey;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Column(nullable = false)
    private boolean active = true;
    
    @ElementCollection
    @CollectionTable(name = "app_features", joinColumns = @JoinColumn(name = "app_id"))
    @Column(name = "feature")
    private Set<String> features;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

