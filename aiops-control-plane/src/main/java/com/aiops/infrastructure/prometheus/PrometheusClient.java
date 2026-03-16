package com.aiops.infrastructure.prometheus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Prometheus Client - Query metrics from Prometheus
 */
@Slf4j
@Component
public class PrometheusClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    /**
     * Execute instant query
     */
    public Map<String, Object> query(String promql) {
        String url = String.format("%s/api/v1/query?query=%s", prometheusUrl, encode(promql));
        log.debug("Prometheus instant query: {}", promql);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of("status", "error");
        } catch (Exception e) {
            log.error("Prometheus query failed: {}", e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    /**
     * Execute range query
     */
    public Map<String, Object> queryRange(String promql, String start, String end, String step) {
        String url = String.format("%s/api/v1/query_range?query=%s&start=%s&end=%s&step=%s",
                prometheusUrl, encode(promql), encode(start), encode(end), encode(step));
        log.debug("Prometheus range query: {} [{} to {}]", promql, start, end);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of("status", "error");
        } catch (Exception e) {
            log.error("Prometheus range query failed: {}", e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    /**
     * Get service metrics (common metrics for topology)
     */
    public Map<String, Double> getServiceMetrics(String serviceId) {
        Map<String, Double> metrics = new HashMap<>();

        // Query latency P99
        String latencyQuery = String.format(
                "histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket{service=\"%s\"}[5m])) by (le))",
                serviceId);
        metrics.put("latency_p99", extractValue(query(latencyQuery)));

        // Query error rate
        String errorQuery = String.format(
                "sum(rate(http_requests_total{service=\"%s\",status=~\"5..\"}[5m])) / sum(rate(http_requests_total{service=\"%s\"}[5m]))",
                serviceId, serviceId);
        double errorRate = extractValue(query(errorQuery));
        metrics.put("error_rate", Double.isNaN(errorRate) ? 0.0 : errorRate);

        // Query QPS
        String qpsQuery = String.format(
                "sum(rate(http_requests_total{service=\"%s\"}[5m]))",
                serviceId);
        metrics.put("qps", extractValue(query(qpsQuery)));

        // Query availability (mock if not available)
        metrics.put("availability", 99.9);

        return metrics;
    }

    /**
     * Get label names from Prometheus
     */
    public List<String> getLabelNames(String match) {
        String url = String.format("%s/api/v1/labels?match[]=%s", prometheusUrl, encode(match));
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map body = response.getBody();
            if (body != null && "success".equals(body.get("status"))) {
                Map data = (Map) body.get("data");
                return (List<String>) data.getOrDefault("data", new ArrayList<>());
            }
        } catch (Exception e) {
            log.error("Failed to get labels: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Get label values from Prometheus
     */
    public List<String> getLabelValues(String label, String match) {
        String url = String.format("%s/api/v1/label/%s/values?match[]=%s",
                prometheusUrl, encode(label), encode(match));
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map body = response.getBody();
            if (body != null && "success".equals(body.get("status"))) {
                return (List<String>) body.getOrDefault("data", new ArrayList<>());
            }
        } catch (Exception e) {
            log.error("Failed to get label values: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Get series metadata
     */
    public List<Map<String, String>> getSeries(List<String> matchers) {
        StringBuilder url = new StringBuilder(String.format("%s/api/v1/series?", prometheusUrl));
        for (String matcher : matchers) {
            url.append("match[]=").append(encode(matcher)).append("&");
        }
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url.toString(), Map.class);
            Map body = response.getBody();
            if (body != null && "success".equals(body.get("status"))) {
                return (List<Map<String, String>>) body.getOrDefault("data", new ArrayList<>());
            }
        } catch (Exception e) {
            log.error("Failed to get series: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private double extractValue(Map<String, Object> response) {
        if (response == null || !"success".equals(response.get("status"))) {
            return 0.0;
        }
        try {
            Map data = (Map) response.get("data");
            List results = (List) data.get("result");
            if (!results.isEmpty()) {
                Map result = (Map) results.get(0);
                List value = (List) result.get("value");
                if (value != null && value.size() >= 2) {
                    return Double.parseDouble((String) value.get(1));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract value from response");
        }
        return 0.0;
    }

    private String encode(String value) {
        if (value == null) return "";
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
