package com.aiops.service.alert;

import com.aiops.repository.entity.Alert;
import com.aiops.repository.repository.AlertRepository;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Noise Reduction Service - Intelligent alert deduplication and suppression
 *
 * Implements multiple noise reduction strategies:
 * 1. Time-window based deduplication - suppress duplicate alerts within time window
 * 2. Frequency-based throttling - limit alerts per service/instance
 * 3. Topological correlation - group related alerts by topology
 * 4. Rule-based suppression - configurable suppression rules
 * 5. Heuristic similarity - fuzzy matching for similar alerts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoiseReducerService {

    private final StringRedisTemplate redisTemplate;
    private final AlertRepository alertRepository;

    // In-memory noise rules cache (could be refreshed from database)
    private volatile List<NoiseRule> activeRules = new ArrayList<>();

    // Statistics for monitoring
    private final Map<String, NoiseStats> statsMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadDefaultRules();
        log.info("NoiseReducerService initialized with {} rules", activeRules.size());
    }

    /**
     * Process alert through noise reduction pipeline
     */
    public NoiseReductionResult process(Alert alert) {
        String tenantId = alert.getTenantId();
        String alertId = alert.getAlertId();
        Instant now = Instant.now();

        log.debug("Processing alert for noise reduction: {}", alertId);

        // Step 1: Check if tenant has noise reduction enabled
        // (In real system, fetch from tenant config)
        boolean noiseReductionEnabled = true;
        if (!noiseReductionEnabled) {
            return NoiseReductionResult.accepted(generateClusterKey(alert), true);
        }

        // Step 2: Apply rule-based suppression (highest priority)
        NoiseRule matchingRule = findMatchingRule(alert);
        if (matchingRule != null) {
            log.info("Alert {} suppressed by rule {}: {}",
                    alertId, matchingRule.getId(), matchingRule.getName());
            recordStats(tenantId, "RULE_SUPPRESSED");
            return NoiseReductionResult.suppressed(
                    matchingRule.getId(),
                    matchingRule.getName(),
                    "Matched rule: " + matchingRule.getDescription()
            );
        }

        // Step 3: Time-window based deduplication (sliding window)
        String fingerprint = generateFingerprint(alert);
        String dedupKey = String.format("aiops:%s:dedup:%s", tenantId, fingerprint);

        Boolean isNewFingerprint = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, alertId, Duration.ofMinutes(5));

        if (!Boolean.TRUE.equals(isNewFingerprint)) {
            String existingId = redisTemplate.opsForValue().get(dedupKey);
            log.info("Alert {} suppressed as duplicate of {} within 5min window",
                    alertId, existingId);
            recordStats(tenantId, "TIME_WINDOW_DEDUP");
            return NoiseReductionResult.suppressed(
                    "TIME_WINDOW_DEDUP",
                    existingId,
                    "Duplicate alert within 5 minutes"
            );
        }

        // Step 4: Frequency-based throttling (burst detection)
        ThrottleCheckResult throttleResult = checkThrottle(alert);
        if (throttleResult.isThrottled()) {
            log.info("Alert {} throttled for service {}: {}",
                    alertId, alert.getServiceId(), throttleResult.getReason());
            recordStats(tenantId, "FREQUENCY_THROTTLED");
            return NoiseReductionResult.suppressed(
                    "FREQUENCY_THROTTLE",
                    throttleResult.getCacheKey(),
                    throttleResult.getReason()
            );
        }

        // Step 5: Heuristic similarity matching (fuzzy dedup)
        Optional<SimilarAlertMatch> similarMatch = findSimilarAlert(alert);
        if (similarMatch.isPresent()) {
            SimilarAlertMatch match = similarMatch.get();
            if (match.getSimilarity() > 0.85) {
                log.info("Alert {} suppressed as {}% similar to {}",
                        alertId, (int)(match.getSimilarity() * 100), match.getExistingAlertId());
                recordStats(tenantId, "SIMILARITY_DEDUP");
                return NoiseReductionResult.suppressed(
                        "SIMILARITY_MATCH",
                        match.getExistingAlertId(),
                        String.format("%.0f%% similar to recent alert", match.getSimilarity() * 100)
                );
            }
        }

        // Step 6: Generate cluster key for topological grouping
        String clusterKey = generateClusterKey(alert);

        // Step 7: Determine if this is a root cause candidate
        boolean isRootCause = isRootCauseCandidate(alert, clusterKey);

        log.info("Alert {} accepted, clusterKey={}, isRootCause={}",
                alertId, clusterKey, isRootCause);
        recordStats(tenantId, "ACCEPTED");

        // Extend dedup window for this alert
        redisTemplate.expire(dedupKey, Duration.ofMinutes(10));

        return NoiseReductionResult.accepted(clusterKey, isRootCause);
    }

    /**
     * Generate unique fingerprint for time-window deduplication
     */
    private String generateFingerprint(Alert alert) {
        // 5-minute time bucket
        long timeBucket = alert.getStartsAt().getEpochSecond() / 300;

        String normalizedTitle = normalizeAlertTitle(alert.getTitle());
        String instance = alert.getInstance() != null ? alert.getInstance() : "";

        // Include severity in fingerprint for differentiation
        return String.format("%s|%s|%s|%s|%d",
                alert.getTenantId(),
                alert.getServiceId(),
                normalizedTitle,
                instance,
                timeBucket
        );
    }

    /**
     * Generate cluster key for alert grouping
     */
    private String generateClusterKey(Alert alert) {
        // Primary grouping by service and alert pattern
        String normalizedTitle = normalizeAlertTitle(alert.getTitle());
        String severity = alert.getSeverity() != null ? alert.getSeverity() : "";

        // Use service + title pattern for clustering
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(alert.getServiceId()).append("|");

        // Extract primary pattern from title (remove variable parts like IPs, timestamps)
        String pattern = extractPattern(normalizedTitle);

        // Include severity for P1/P2 separation
        if ("P1".equals(severity) || "P2".equals(severity)) {
            keyBuilder.append(severity).append("|");
        }

        keyBuilder.append(pattern.hashCode());

        return keyBuilder.toString();
    }

    /**
     * Normalize alert title for deduplication
     */
    private String normalizeAlertTitle(String title) {
        if (title == null) return "";

        return title
                .toLowerCase()
                .replaceAll("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", "TIMESTAMP")  // Timestamps
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "IP")     // IP addresses
                .replaceAll("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "UUID")  // UUIDs
                .replaceAll("\\b\\d+\\b", "NUM")  // Numbers
                .trim();
    }

    /**
     * Extract canonical pattern from alert title
     */
    private String extractPattern(String normalizedTitle) {
        // Remove very specific variable parts
        return normalizedTitle
                .replaceAll("TIMESTAMP", "")
                .replaceAll("IP", "")
                .replaceAll("UUID", "")
                .replaceAll("NUM", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Check frequency throttling for burst detection
     */
    private ThrottleCheckResult checkThrottle(Alert alert) {
        String tenantId = alert.getTenantId();
        String serviceId = alert.getServiceId();
        Instant now = Instant.now();

        // Service-level throttling: max 20 alerts per minute per service
        String serviceKey = String.format("aiops:%s:throttle:svc:%s", tenantId, serviceId);
        Long serviceCount = redisTemplate.opsForValue().increment(serviceKey);
        if (serviceCount != null && serviceCount == 1) {
            redisTemplate.expire(serviceKey, Duration.ofMinutes(1));
        }

        if (serviceCount != null && serviceCount > 20) {
            return ThrottleCheckResult.throttled(serviceKey,
                    String.format("Service %s exceeded 20 alerts/min limit", serviceId));
        }

        // Instance-level throttling: max 10 alerts per 5 minutes per instance
        if (alert.getInstance() != null) {
            String instanceKey = String.format("aiops:%s:throttle:inst:%s:%s",
                    tenantId, serviceId, alert.getInstance());
            Long instCount = redisTemplate.opsForValue().increment(instanceKey);
            if (instCount != null && instCount == 1) {
                redisTemplate.expire(instanceKey, Duration.ofMinutes(5));
            }

            if (instCount != null && instCount > 10) {
                return ThrottleCheckResult.throttled(instanceKey,
                        String.format("Instance %s exceeded 10 alerts/5min limit", alert.getInstance()));
            }
        }

        return ThrottleCheckResult.passed();
    }

    /**
     * Find similar alert using fuzzy matching
     */
    private Optional<SimilarAlertMatch> findSimilarAlert(Alert alert) {
        // Query recent alerts from database (last 10 minutes)
        Instant since = Instant.now().minusSeconds(600);
        List<Alert> recentAlerts = alertRepository.findByServiceAndTimeRange(
                alert.getTenantId(), alert.getServiceId(), since, Instant.now()
        );

        String alertTitle = normalizeAlertTitle(alert.getTitle());
        String alertDesc = alert.getDescription() != null ?
                normalizeAlertTitle(alert.getDescription()) : "";

        SimilarAlertMatch bestMatch = null;
        double bestSimilarity = 0.0;

        for (Alert recent : recentAlerts) {
            if (recent.getAlertId().equals(alert.getAlertId())) {
                continue;
            }

            // Calculate similarity
            String recentTitle = normalizeAlertTitle(recent.getTitle());
            double titleSim = calculateSimilarity(alertTitle, recentTitle);

            double descSim = 0.0;
            if (alert.getDescription() != null && recent.getDescription() != null) {
                String recentDesc = normalizeAlertTitle(recent.getDescription());
                descSim = calculateSimilarity(alertDesc, recentDesc);
            }

            // Weighted average (title more important)
            double similarity = titleSim * 0.7 + descSim * 0.3;

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = new SimilarAlertMatch(recent.getAlertId(), similarity);
            }
        }

        return bestSimilarity > 0.7 ? Optional.of(bestMatch) : Optional.empty();
    }

    /**
     * Calculate similarity between two strings (0-1)
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        // Jaccard similarity using word sets
        Set<String> set1 = Arrays.stream(s1.split("\\s+")).collect(Collectors.toSet());
        Set<String> set2 = Arrays.stream(s2.split("\\s+")).collect(Collectors.toSet());

        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    /**
     * Determine if alert is a root cause candidate
     */
    private boolean isRootCauseCandidate(Alert alert, String clusterKey) {
        String severity = alert.getSeverity();

        // P1/P2 severity are always root cause candidates
        if ("P1".equals(severity) || "P2".equals(severity)) {
            return true;
        }

        // Check if this is the first alert in the cluster
        String clusterKeyCount = String.format("aiops:%s:cluster:%s",
                alert.getTenantId(), clusterKey);
        Long count = redisTemplate.opsForValue().increment(clusterKeyCount);
        if (count != null && count == 1) {
            redisTemplate.expire(clusterKeyCount, Duration.ofMinutes(30));
            return true;
        }

        // P3 with new pattern might be root cause
        return "P3".equals(severity) && count != null && count <= 3;
    }

    /**
     * Find matching suppression rule
     */
    private NoiseRule findMatchingRule(Alert alert) {
        for (NoiseRule rule : activeRules) {
            if (matchesRule(alert, rule)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Check if alert matches suppression rule
     */
    private boolean matchesRule(Alert alert, NoiseRule rule) {
        RuleCondition condition = rule.getCondition();

        // Check severity regex
        if (condition.getSeverityRegex() != null) {
            if (!matchesRegex(alert.getSeverity(), condition.getSeverityRegex())) {
                return false;
            }
        }

        // Check service regex
        if (condition.getServiceRegex() != null) {
            if (!matchesRegex(alert.getServiceId(), condition.getServiceRegex())) {
                return false;
            }
        }

        // Check title regex
        if (condition.getTitleRegex() != null) {
            if (!matchesRegex(alert.getTitle(), condition.getTitleRegex())) {
                return false;
            }
        }

        // Check source regex
        if (condition.getSourceRegex() != null) {
            if (!matchesRegex(alert.getSource(), condition.getSourceRegex())) {
                return false;
            }
        }

        // Check label conditions
        if (condition.getLabels() != null && !condition.getLabels().isEmpty()) {
            Map<String, String> alertLabels = alert.getLabels();
            if (alertLabels == null) return false;

            for (Map.Entry<String, String> labelCond : condition.getLabels().entrySet()) {
                String alertValue = alertLabels.get(labelCond.getKey());
                if (alertValue == null || !matchesRegex(alertValue, labelCond.getValue())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchesRegex(String value, String regex) {
        if (value == null) return false;
        try {
            return Pattern.matches(regex, value);
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern: {}", regex);
            return false;
        }
    }

    /**
     * Load default noise reduction rules
     */
    private void loadDefaultRules() {
        activeRules.addAll(List.of(
                // Suppress low-priority test alerts
                NoiseRule.builder()
                        .id("RULE-001")
                        .name("Suppress Test Environment Alerts")
                        .priority(100)
                        .condition(RuleCondition.builder()
                                .severityRegex("P4|P5|info|warning")
                                .labels(Map.of("env", "test|staging"))
                                .build())
                        .description("Suppress low severity alerts from test environment")
                        .suppressDuration(Duration.ofMinutes(30))
                        .enabled(true)
                        .build(),

                // Suppress known flaky alerts
                NoiseRule.builder()
                        .id("RULE-002")
                        .name("Suppress Known Flaky Checks")
                        .priority(90)
                        .condition(RuleCondition.builder()
                                .titleRegex(".*health check.*timeout.*|.*probe failed.*")
                                .sourceRegex("prometheus|blackbox")
                                .build())
                        .description("Suppress transient health check failures")
                        .suppressDuration(Duration.ofMinutes(10))
                        .enabled(true)
                        .build(),

                // Suppress maintenance window alerts
                NoiseRule.builder()
                        .id("RULE-003")
                        .name("Maintenance Window Suppression")
                        .priority(80)
                        .condition(RuleCondition.builder()
                                .labels(Map.of("maintenance", "true|yes|1"))
                                .build())
                        .description("Suppress alerts during maintenance windows")
                        .suppressDuration(Duration.ofHours(1))
                        .enabled(true)
                        .build()
        ));

        // Sort by priority
        activeRules.sort(Comparator.comparingInt(NoiseRule::getPriority).reversed());
    }

    /**
     * Record statistics for monitoring
     */
    private void recordStats(String tenantId, String action) {
        statsMap.computeIfAbsent(tenantId, k -> new NoiseStats())
                .increment(action);
    }

    /**
     * Get noise reduction statistics
     */
    public Map<String, NoiseStats> getStats() {
        return new HashMap<>(statsMap);
    }

    // ==================== Data Classes ====================

    @Data
    @Builder
    @AllArgsConstructor
    public static class NoiseRule {
        private String id;
        private String name;
        private String description;
        private int priority;
        private RuleCondition condition;
        private Duration suppressDuration;
        private boolean enabled;
    }

    @Data
    @Builder
    public static class RuleCondition {
        private String severityRegex;
        private String serviceRegex;
        private String titleRegex;
        private String sourceRegex;
        private Map<String, String> labels;
    }

    @Data
    @AllArgsConstructor
    private static class SimilarAlertMatch {
        private String existingAlertId;
        private double similarity;
    }

    @Data
    @Builder
    private static class ThrottleCheckResult {
        private boolean throttled;
        private String cacheKey;
        private String reason;

        public static ThrottleCheckResult throttled(String key, String reason) {
            return ThrottleCheckResult.builder()
                    .throttled(true)
                    .cacheKey(key)
                    .reason(reason)
                    .build();
        }

        public static ThrottleCheckResult passed() {
            return ThrottleCheckResult.builder()
                    .throttled(false)
                    .build();
        }
    }

    /**
     * Statistics for noise reduction monitoring
     */
    @Data
    public static class NoiseStats {
        private long accepted = 0;
        private long ruleSuppressed = 0;
        private long timeWindowDedup = 0;
        private long frequencyThrottled = 0;
        private long similarityDedup = 0;

        public void increment(String action) {
            switch (action) {
                case "ACCEPTED" -> accepted++;
                case "RULE_SUPPRESSED" -> ruleSuppressed++;
                case "TIME_WINDOW_DEDUP" -> timeWindowDedup++;
                case "FREQUENCY_THROTTLED" -> frequencyThrottled++;
                case "SIMILARITY_DEDUP" -> similarityDedup++;
            }
        }

        public long getTotal() {
            return accepted + ruleSuppressed + timeWindowDedup +
                   frequencyThrottled + similarityDedup;
        }

        public double getSuppressionRate() {
            long total = getTotal();
            return total > 0 ? (double) (total - accepted) / total : 0.0;
        }
    }
}
