package com.alerting.sdk;

import com.alerting.sdk.client.AlertHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(AlertSDKProperties.class)
@ConditionalOnProperty(prefix = "alert.sdk", name = "enabled", havingValue = "true", 
                       matchIfMissing = true)
@EnableAsync
@EnableScheduling
public class AlertSDKAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlertHttpClient alertHttpClient(AlertSDKProperties properties) {
        return new AlertHttpClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertSDK alertSDK(AlertHttpClient httpClient, AlertSDKProperties properties) {
        return new AlertSDK(httpClient, properties);
    }

    @Bean(name = "alertSdkExecutor")
    public Executor alertSdkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("alert-sdk-");
        executor.initialize();
        return executor;
    }
}

