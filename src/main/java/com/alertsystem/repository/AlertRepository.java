package com.alertsystem.repository;

import com.alertsystem.entity.Alert;
import com.alertsystem.entity.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    
    Optional<Alert> findByIssueId(UUID issueId);
    
    List<Alert> findByStatus(AlertStatus status);
    
    @Query("SELECT a FROM Alert a WHERE a.status = :status AND (a.nextRecheckAt IS NULL OR a.nextRecheckAt <= :now) ORDER BY a.createdAt ASC")
    List<Alert> findAlertsForRecheck(@Param("status") AlertStatus status, @Param("now") LocalDateTime now);
}