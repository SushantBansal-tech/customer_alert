package com.alerting.sdk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "alert.sdk")
public class AlertSDKProperties {
    
    private String apiUrl = "http://localhost:8080/api/v1/events";
    private String apiKey;
    private String appId;
    private boolean enabled = true;
    private boolean async = true;
    private int connectionTimeout = 5000;
    private int readTimeout = 5000;
    private int maxRetries = 3;
    private int retryDelayMs = 1000;
    private int bufferSize = 1000;
    private int flushIntervalMs = 5000;
}

