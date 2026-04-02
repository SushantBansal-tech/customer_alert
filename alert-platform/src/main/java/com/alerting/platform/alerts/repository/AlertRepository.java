package com.alerting.platform.alerts.repository;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.AlertStatus;
import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // ========== Active Alerts ==========

    List<Alert> findByAppIdAndStatusIn(String appId, List<AlertStatus> statuses);

    List<Alert> findByStatus(AlertStatus status);

    List<Alert> findByStatusInOrderByTriggeredAtDesc(List<AlertStatus> statuses);

    @Query("SELECT a FROM Alert a WHERE a.status IN :statuses ORDER BY a.triggeredAt DESC")
    List<Alert> findActiveAlertsForAutoCheck(@Param("statuses") List<AlertStatus> statuses);

    // ========== Time-based Queries (THESE WERE MISSING) ==========

    /**
     * Find alerts triggered after a specific time
     */
    List<Alert> findByTriggeredAtAfterOrderByTriggeredAtDesc(Instant since);

    /**
     * Find alerts triggered after a time with specific statuses
     */
    List<Alert> findByTriggeredAtAfterAndStatusInOrderByTriggeredAtDesc(
        Instant since, 
        List<AlertStatus> statuses
    );

    /**
     * Find alerts in a date range
     */
    @Query("SELECT a FROM Alert a WHERE a.triggeredAt BETWEEN :start AND :end ORDER BY a.triggeredAt DESC")
    List<Alert> findByTriggeredAtBetween(@Param("start") Instant start, @Param("end") Instant end);

    // ========== Deduplication ==========

    @Query("SELECT a FROM Alert a WHERE a.deduplicationKey = :dedupKey AND a.status IN :statuses")
    Optional<Alert> findActiveAlertByDeduplicationKey(
        @Param("dedupKey") String deduplicationKey,
        @Param("statuses") List<AlertStatus> statuses
    );

    @Query("SELECT a FROM Alert a WHERE a.appId = :appId AND a.feature = :feature " +
           "AND a.ruleId = :ruleId AND a.triggeredAt > :since AND a.status NOT IN ('RESOLVED', 'AUTO_RESOLVED', 'CLOSED')")
    Optional<Alert> findRecentActiveAlert(
        @Param("appId") String appId,
        @Param("feature") String feature,
        @Param("ruleId") Long ruleId,
        @Param("since") Instant since
    );

    // ========== Assignment Queries ==========

    List<Alert> findByAssigneeAndStatusNot(TeamMember assignee, AlertStatus status);

    @Query("SELECT a FROM Alert a WHERE a.assignee.id = :memberId AND a.status NOT IN ('RESOLVED', 'AUTO_RESOLVED', 'CLOSED')")
    List<Alert> findActiveAlertsByAssignee(@Param("memberId") Long memberId);

    List<Alert> findByAssignedTeamAndStatusIn(Team team, List<AlertStatus> statuses);

    @Query("SELECT a FROM Alert a WHERE a.assignedTeam.id = :teamId AND a.status NOT IN ('RESOLVED', 'AUTO_RESOLVED', 'CLOSED')")
    List<Alert> findActiveAlertsByTeam(@Param("teamId") Long teamId);

    List<Alert> findByStatusAndAssigneeIsNull(AlertStatus status);

    // ========== Search ==========

    List<Alert> findByRuleNameContainingIgnoreCase(String ruleName);

    // ========== Statistics Queries ==========

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.appId = :appId AND a.status IN :statuses")
    long countByAppIdAndStatusIn(@Param("appId") String appId, @Param("statuses") List<AlertStatus> statuses);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.assignedTeam = :team AND a.status IN :statuses")
    long countByTeamAndStatusIn(@Param("team") Team team, @Param("statuses") List<AlertStatus> statuses);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.assignee = :member AND a.status IN :statuses")
    long countByAssigneeAndStatusIn(@Param("member") TeamMember member, @Param("statuses") List<AlertStatus> statuses);

    @Query("SELECT a.status, COUNT(a) FROM Alert a WHERE a.appId = :appId GROUP BY a.status")
    List<Object[]> countByAppIdGroupByStatus(@Param("appId") String appId);

    // ========== Escalation Queries ==========

    @Query("SELECT a FROM Alert a WHERE a.acknowledgedAt IS NULL AND a.triggeredAt < :threshold " +
           "AND a.escalationLevel < :maxLevel AND a.status NOT IN ('RESOLVED', 'AUTO_RESOLVED', 'CLOSED', 'SUPPRESSED')")
    List<Alert> findAlertsNeedingEscalation(
        @Param("threshold") Instant threshold,
        @Param("maxLevel") int maxLevel
    );

    // ========== User-level Queries ==========

    @Query("SELECT a FROM Alert a WHERE a.alertType = 'USER_LEVEL' AND a.affectedUserId = :userId " +
           "AND a.status NOT IN ('RESOLVED', 'AUTO_RESOLVED', 'CLOSED')")
    List<Alert> findActiveUserAlerts(@Param("userId") String userId);

    // ========== Pagination ==========

    Page<Alert> findByAppIdOrderByTriggeredAtDesc(String appId, Pageable pageable);

    Page<Alert> findByStatusInOrderByTriggeredAtDesc(List<AlertStatus> statuses, Pageable pageable);

    // ========== Resolution Metrics ==========

    @Query("SELECT AVG(EXTRACT(EPOCH FROM (a.acknowledgedAt - a.triggeredAt))) FROM Alert a " +
           "WHERE a.acknowledgedAt IS NOT NULL AND a.triggeredAt > :since")
    Double avgTimeToAcknowledge(@Param("since") Instant since);

    @Query("SELECT AVG(EXTRACT(EPOCH FROM (a.resolvedAt - a.triggeredAt))) FROM Alert a " +
           "WHERE a.resolvedAt IS NOT NULL AND a.triggeredAt > :since")
    Double avgTimeToResolve(@Param("since") Instant since);

    // ========== Suppressed Alerts ==========

    @Query("SELECT a FROM Alert a WHERE a.status = 'SUPPRESSED' AND a.suppressedUntil < :now")
    List<Alert> findExpiredSuppressedAlerts(@Param("now") Instant now);
}