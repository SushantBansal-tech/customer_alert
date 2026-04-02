package com.alerting.platform.app.repository;

import com.alerting.platform.app.model.RegisteredApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegisteredAppRepository extends JpaRepository<RegisteredApp, Long> {
    
    Optional<RegisteredApp> findByAppId(String appId);
    
    Optional<RegisteredApp> findByApiKey(String apiKey);
    
    boolean existsByApiKeyAndAppIdAndActiveTrue(String apiKey, String appId);
}

