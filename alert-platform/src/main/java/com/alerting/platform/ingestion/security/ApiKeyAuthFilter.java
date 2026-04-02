package com.alerting.platform.ingestion.security;

import com.alerting.platform.app.service.AppRegistrationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final AppRegistrationService appService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Skip auth for health checks and admin endpoints
        if (path.startsWith("/actuator") || path.startsWith("/api/admin")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Only protect event ingestion endpoints
        if (!path.startsWith("/api/v1/events")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        String appId = request.getHeader("X-App-Id");

        if (apiKey == null || appId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Missing API key or App ID\"}");
            return;
        }

        if (!appService.validateApp(apiKey, appId)) {
            log.warn("Invalid API key attempt for app: {}", appId);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\": \"Invalid API key\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

