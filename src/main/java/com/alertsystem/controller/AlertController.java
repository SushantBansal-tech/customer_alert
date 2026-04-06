package com.alertsystem.controller;

import com.alertsystem.entity.Alert;
import com.alertsystem.service.AlertManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alerts")
@CrossOrigin(origins = "*")
public class AlertController {
    
    @Autowired
    private AlertManagementService alertService;
    
    @GetMapping("/pending")
    public ResponseEntity<List<Alert>> getPendingAlerts() {
        List<Alert> alerts = alertService.getPendingAlerts();
        return ResponseEntity.ok(alerts);
    }
    
    @GetMapping("/{alertId}")
    public ResponseEntity<Alert> getAlertDetails(@PathVariable UUID alertId) {
        Alert alert = alertService.getAlertDetails(alertId);
        if (alert == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alert);
    }
    
    @PostMapping("/recheck")
    public ResponseEntity<?> manualRecheck() {
        alertService.reCheckAlerts();
        return ResponseEntity.ok("Alerts rechecked");
    }
}