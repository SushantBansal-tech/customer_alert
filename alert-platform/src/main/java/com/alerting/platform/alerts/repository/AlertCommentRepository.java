package com.alerting.platform.alerts.repository;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.alerts.model.AlertComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertCommentRepository extends JpaRepository<AlertComment, Long> {

    List<AlertComment> findByAlertOrderByCreatedAtDesc(Alert alert);

    List<AlertComment> findByAlertIdOrderByCreatedAtDesc(Long alertId);

    List<AlertComment> findByAlertAndIsInternalFalseOrderByCreatedAtDesc(Alert alert);

    List<AlertComment> findByAuthorIdOrderByCreatedAtDesc(String authorId);
}