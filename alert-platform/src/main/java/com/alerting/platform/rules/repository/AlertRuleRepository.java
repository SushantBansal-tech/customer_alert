package com.alerting.platform.rules.repository;

import com.alerting.platform.rules.model.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    
    List<AlertRule> findByAppIdAndEnabledTrue(String appId);
    
    List<AlertRule> findByAppIdAndFeatureAndEnabledTrue(String appId, String feature);
    
    List<AlertRule> findByEnabledTrue();
}

