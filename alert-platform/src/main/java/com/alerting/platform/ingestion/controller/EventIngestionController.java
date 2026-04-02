package com.alerting.platform.ingestion.controller;

import com.alerting.platform.common.model.AlertEvent;
import com.alerting.platform.ingestion.service.EventIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
public class EventIngestionController {

    private final EventIngestionService ingestionService;

    @PostMapping
    public ResponseEntity<Map<String, String>> ingestEvent(
            @RequestHeader("X-App-Id") String appId,
            @Valid @RequestBody AlertEvent event) {
        
        event.setAppId(appId);
        ingestionService.ingestEvent(event);
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(Map.of("status", "accepted"));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(
            @RequestHeader("X-App-Id") String appId,
            @Valid @RequestBody List<AlertEvent> events) {
        
        events.forEach(event -> event.setAppId(appId));
        int processed = ingestionService.ingestBatch(events);
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(Map.of(
                "status", "accepted",
                "count", processed
            ));
    }
}

