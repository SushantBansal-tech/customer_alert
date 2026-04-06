package com.alertsystem.repository;

import com.alertsystem.entity.IssueHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssueHistoryRepository extends JpaRepository<IssueHistory, UUID> {
    
    List<IssueHistory> findByIssueIdOrderByChangedAtDesc(UUID issueId);
}