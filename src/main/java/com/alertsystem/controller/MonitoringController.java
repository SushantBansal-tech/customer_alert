package com.alertsystem.controller;

import com.alertsystem.service.ActivityCaptureService;
import com.alertsystem.service.LogReaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/monitoring")
@CrossOrigin(origins = "*")
public class MonitoringController {
    
    @Autowired
    private LogReaderService logReaderService;
    
    @Autowired
    private ActivityCaptureService activityCaptureService;
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("logBufferSize", logReaderService.getBufferSize());
        status.put("activityQueueSize", activityCaptureService.getQueueSize());
        status.put("status", "running");
        
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("System healthy");
    }
}