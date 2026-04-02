package com.alerting.platform.alerts.repository;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.AlertTimeline;
import com.alerting.platform.alerts.model.AlertTimeline.TimelineEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AlertTimelineRepository extends JpaRepository<AlertTimeline, Long> {

    List<AlertTimeline> findByAlertOrderByCreatedAtDesc(Alert alert);

    List<AlertTimeline> findByAlertIdOrderByCreatedAtDesc(Long alertId);

    List<AlertTimeline> findByAlertAndEventType(Alert alert, TimelineEventType eventType);

    @Query("SELECT t FROM AlertTimeline t WHERE t.alert.id = :alertId ORDER BY t.createdAt ASC")
    List<AlertTimeline> findTimelineByAlertId(@Param("alertId") Long alertId);

    @Query("SELECT t FROM AlertTimeline t WHERE t.performedBy = :performedBy AND t.createdAt > :since ORDER BY t.createdAt DESC")
    List<AlertTimeline> findRecentActivityByUser(@Param("performedBy") String performedBy, @Param("since") Instant since);
}