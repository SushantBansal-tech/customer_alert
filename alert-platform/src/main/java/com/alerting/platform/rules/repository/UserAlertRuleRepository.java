package com.alerting.platform.rules.repository;

import com.alerting.platform.rules.model.UserAlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAlertRuleRepository extends JpaRepository<UserAlertRule, Long> {

    List<UserAlertRule> findByEnabledTrue();

    List<UserAlertRule> findByAppIdAndEnabledTrue(String appId);

    List<UserAlertRule> findByAppIdAndFeatureAndEnabledTrue(String appId, String feature);
}