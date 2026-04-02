package com.alerting.platform.metrics;

import com.alerting.platform.processing.service.AggregationService;
import com.alerting.platform.processing.service.AggregationService.AggregatedMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final AggregationService aggregationService;

    @GetMapping("/{appId}/{feature}")
    public ResponseEntity<AggregatedMetrics> getMetrics(
            @PathVariable String appId,
            @PathVariable String feature) {
        return ResponseEntity.ok(aggregationService.getMetrics(appId, feature));
    }
}

