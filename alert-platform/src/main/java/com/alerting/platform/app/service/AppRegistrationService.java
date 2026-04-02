package com.alerting.platform.app.service;

import com.alerting.platform.app.model.RegisteredApp;
import com.alerting.platform.app.repository.RegisteredAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppRegistrationService {

    private final RegisteredAppRepository appRepository;

    @Transactional
    public RegisteredApp registerApp(String name, String description) {
        RegisteredApp app = RegisteredApp.builder()
            .appId(generateAppId())
            .apiKey(generateApiKey())
            .name(name)
            .description(description)
            .active(true)
            .build();
        
        return appRepository.save(app);
    }

    @Cacheable(value = "apps", key = "#apiKey + ':' + #appId")
    public boolean validateApp(String apiKey, String appId) {
        return appRepository.existsByApiKeyAndAppIdAndActiveTrue(apiKey, appId);
    }

    public Optional<RegisteredApp> findByAppId(String appId) {
        return appRepository.findByAppId(appId);
    }

    private String generateAppId() {
        return "app-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateApiKey() {
        return "ak-" + UUID.randomUUID().toString().replace("-", "");
    }
}

