package com.alertsystem.repository;

import com.alertsystem.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, UUID> {
    
    List<UserActivity> findByUserIdAndTimestampAfter(UUID userId, LocalDateTime timestamp);
    
    List<UserActivity> findByTimestampAfter(LocalDateTime timestamp);
    
    @Query("SELECT DISTINCT ua.userId FROM UserActivity ua WHERE ua.timestamp > :since AND ua.status = 'FAILED'")
    List<UUID> findUsersWithFailedActivities(@Param("since") LocalDateTime since);
    
    @Query("SELECT ua FROM UserActivity ua WHERE ua.userId = :userId AND ua.status = 'FAILED' AND ua.timestamp > :since ORDER BY ua.timestamp DESC")
    List<UserActivity> findFailedActivitiesByUser(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
    
    @Query("SELECT ua FROM UserActivity ua WHERE ua.status = 'FAILED' AND ua.timestamp > :since ORDER BY ua.timestamp DESC")
    List<UserActivity> findFailedActivities(@Param("since") LocalDateTime since);
    
    @Query("SELECT DISTINCT ua.userId FROM UserActivity ua WHERE ua.timestamp > :since")
    List<UUID> findActiveUsers(@Param("since") LocalDateTime since);
}