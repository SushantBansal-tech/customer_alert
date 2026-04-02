package com.alerting.platform.team.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "teams")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String slackChannel;

    @Column(nullable = false)
    private String emailDistribution;

    // Features this team is responsible for
    @ElementCollection
    @CollectionTable(name = "team_features", joinColumns = @JoinColumn(name = "team_id"))
    @Column(name = "feature")
    private Set<String> responsibleFeatures = new HashSet<>();

    // Apps this team is responsible for
    @ElementCollection
    @CollectionTable(name = "team_apps", joinColumns = @JoinColumn(name = "team_id"))
    @Column(name = "app_id")
    private Set<String> responsibleApps = new HashSet<>();

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL)
    private Set<TeamMember> members = new HashSet<>();

    private boolean active = true;

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