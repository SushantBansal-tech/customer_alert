package com.alerting.platform.alerts.repository;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByAppIdAndStatusIn(String appId, List<AlertStatus> statuses);

    @Query("SELECT a FROM Alert a WHERE a.appId = :appId AND a.feature = :feature " +
           "AND a.ruleId = :ruleId AND a.triggeredAt > :since AND a.status != 'RESOLVED'")
    Optional<Alert> findRecentAlert(String appId, String feature, Long ruleId, Instant since);

    List<Alert> findByStatusAndTriggeredAtAfter(AlertStatus status, Instant since);
}

