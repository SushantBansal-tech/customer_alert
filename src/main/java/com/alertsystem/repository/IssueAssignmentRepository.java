package com.alertsystem.repository;

import com.alertsystem.entity.IssueAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueAssignmentRepository extends JpaRepository<IssueAssignment, UUID> {
    
    Optional<IssueAssignment> findByIssueId(UUID issueId);
    
    List<IssueAssignment> findByAssignedToUserId(UUID userId);
}