package com.alertsystem.repository;

import com.alertsystem.entity.Issue;
import com.alertsystem.entity.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {
    
    @Query("SELECT i FROM Issue i WHERE i.userId = :userId AND i.issueType = :issueType AND i.status = 'OPEN' AND i.detectedAt > :since ORDER BY i.detectedAt DESC LIMIT 1")
    Optional<Issue> findRecentOpenIssue(@Param("userId") UUID userId, 
                                       @Param("issueType") String issueType,
                                       @Param("since") LocalDateTime since);
    
    List<Issue> findByUserIdAndStatus(UUID userId, IssueStatus status);
    
    List<Issue> findByStatus(IssueStatus status);
    
    @Query("SELECT i FROM Issue i WHERE i.userId IS NULL AND i.status = 'OPEN' AND i.detectedAt > :since")
    List<Issue> findRecentMultiUserIssues(@Param("since") LocalDateTime since);
    
    @Query("SELECT i FROM Issue i WHERE i.status = :status ORDER BY i.detectedAt DESC")
    List<Issue> findByStatusOrderByDetectedAtDesc(@Param("status") IssueStatus status);
}