package com.aiops.controller;

import com.aiops.dto.ApiResponse;
import com.aiops.infrastructure.prometheus.PrometheusClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Metrics Controller - Prometheus metrics query
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final PrometheusClient prometheusClient;

    /**
     * Query Prometheus with PromQL
     */
    @PostMapping("/query")
    public ApiResponse<Map<String, Object>> queryMetrics(
            @RequestParam String tenantId,
            @RequestBody QueryRequest request) {

        log.debug("Querying metrics: {}", request.getQuery());

        Map<String, Object> result;
        if (request.getTimeRange() != null) {
            // Range query
            result = prometheusClient.queryRange(
                    request.getQuery(),
                    request.getTimeRange().getStart(),
                    request.getTimeRange().getEnd(),
                    request.getTimeRange().getStep()
            );
        } else {
            // Instant query
            result = prometheusClient.query(request.getQuery());
        }

        return ApiResponse.success(result);
    }

    /**
     * Get service metrics (shortcut)
     */
    @GetMapping("/services/{serviceId}")
    public ApiResponse<Map<String, Double>> getServiceMetrics(
            @PathVariable String serviceId,
            @RequestParam String tenantId) {

        var metrics = prometheusClient.getServiceMetrics(serviceId);
        return ApiResponse.success(metrics);
    }

    /**
     * Get service health status from Prometheus
     */
    @GetMapping("/services/{serviceId}/health")
    public ApiResponse<Map<String, Object>> getServiceHealth(
            @PathVariable String serviceId,
            @RequestParam String tenantId) {

        var metrics = prometheusClient.getServiceMetrics(serviceId);

        // Calculate health based on metrics
        double errorRate = metrics.getOrDefault("error_rate", 0.0);
        double latency = metrics.getOrDefault("latency_p99", 0.0);
        double availability = metrics.getOrDefault("availability", 100.0);

        String health;
        if (errorRate > 0.1 || availability < 95) {
            health = "critical";
        } else if (errorRate > 0.05 || availability < 99) {
            health = "error";
        } else if (errorRate > 0.01 || latency > 500) {
            health = "warning";
        } else {
            health = "healthy";
        }

        Map<String, Object> result = Map.of(
                "serviceId", serviceId,
                "health", health,
                "metrics", metrics,
                "timestamp", Instant.now().toString()
        );

        return ApiResponse.success(result);
    }

    /**
     * Get available metric names
     */
    @GetMapping("/labels")
    public ApiResponse<List<String>> getMetricLabels(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "") String match) {

        var labels = prometheusClient.getLabelNames(match);
        return ApiResponse.success(labels);
    }

    /**
     * Get label values
     */
    @GetMapping("/labels/{label}/values")
    public ApiResponse<List<String>> getLabelValues(
            @PathVariable String label,
            @RequestParam String tenantId,
            @RequestParam(required = false) String match) {

        var values = prometheusClient.getLabelValues(label, match);
        return ApiResponse.success(values);
    }

    /**
     * Get series metadata
     */
    @PostMapping("/series")
    public ApiResponse<List<Map<String, String>>> getSeries(
            @RequestParam String tenantId,
            @RequestBody SeriesRequest request) {

        var series = prometheusClient.getSeries(request.getMatchers());
        return ApiResponse.success(series);
    }

    // Request DTOs
    public static class QueryRequest {
        private String query;
        private TimeRange timeRange;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public TimeRange getTimeRange() { return timeRange; }
        public void setTimeRange(TimeRange timeRange) { this.timeRange = timeRange; }
    }

    public static class TimeRange {
        private String start;
        private String end;
        private String step;

        public String getStart() { return start; }
        public void setStart(String start) { this.start = start; }
        public String getEnd() { return end; }
        public void setEnd(String end) { this.end = end; }
        public String getStep() { return step; }
        public void setStep(String step) { this.step = step; }
    }

    public static class SeriesRequest {
        private List<String> matchers;

        public List<String> getMatchers() { return matchers; }
        public void setMatchers(List<String> matchers) { this.matchers = matchers; }
    }
}
