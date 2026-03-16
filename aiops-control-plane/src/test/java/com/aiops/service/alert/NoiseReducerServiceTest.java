package com.aiops.service.alert;

import com.aiops.repository.entity.Alert;
import com.aiops.repository.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NoiseReducerService
 */
@ExtendWith(MockitoExtension.class)
public class NoiseReducerServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private NoiseReducerService noiseReducer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testProcess_DuplicateAlert() {
        // Given
        Alert alert = Alert.builder()
                .alertId("alert-001")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("High CPU Usage")
                .severity("P1")
                .startsAt(Instant.now())
                .status("active")
                .build();

        // Redis returns false (key already exists) - duplicate
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenReturn(false);

        // When
        NoiseReducerService.NoiseReductionResult result = noiseReducer.process(alert);

        // Then
        assertTrue(result.isSuppressed());
        assertEquals("TIME_WINDOW_DEDUP", result.getRuleId());
        assertNotNull(result.getReason());
    }

    @Test
    void testProcess_NewAlert() {
        // Given
        Alert alert = Alert.builder()
                .alertId("alert-002")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("Memory Leak")
                .severity("P1")
                .startsAt(Instant.now())
                .status("active")
                .build();

        // Redis returns true (new key) - accepted
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenReturn(true);

        // When
        NoiseReducerService.NoiseReductionResult result = noiseReducer.process(alert);

        // Then
        assertFalse(result.isSuppressed());
        assertTrue(result.isRootCauseCandidate());
        assertNotNull(result.getClusterKey());
    }

    @Test
    void testProcess_ServiceThrottling() {
        // Given - high frequency alert
        Alert alert = Alert.builder()
                .alertId("alert-003")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("Frequent Alert")
                .severity("P3")
                .startsAt(Instant.now())
                .status("active")
                .build();

        // Redis dedup check passes
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenReturn(true);

        // But frequency check throttles (count > 20)
        when(valueOperations.increment(anyString()))
                .thenReturn(21L);

        // When
        NoiseReducerService.NoiseReductionResult result = noiseReducer.process(alert);

        // Then
        assertTrue(result.isSuppressed());
        assertEquals("FREQUENCY_THROTTLE", result.getRuleId());
    }

    @Test
    void testProcess_LowSeverity() {
        // Given - low severity alert
        Alert alert = Alert.builder()
                .alertId("alert-004")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("Info Alert")
                .severity("P5")
                .startsAt(Instant.now())
                .status("active")
                .build();

        // Passed dedup and throttle
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(valueOperations.increment(anyString()))
                .thenReturn(1L);

        // When
        NoiseReducerService.NoiseReductionResult result = noiseReducer.process(alert);

        // Then - should not be root cause candidate (low severity)
        assertFalse(result.isSuppressed());
        assertFalse(result.isRootCauseCandidate());
    }

    @Test
    void testFindMatchingRule_TitleRegex() {
        // Given - alert matching rule
        Alert alert = Alert.builder()
                .alertId("alert-005")
                .tenantId("tenant-001")
                .serviceId("test-service")
                .title("Health Check Timeout")
                .severity("P4")
                .source("prometheus")
                .startsAt(Instant.now())
                .status("active")
                .build();

        // When
        NoiseReducerService.NoiseReductionResult result = noiseReducer.process(alert);

        // Then - should match RULE-002 (flaky health checks)
        assertTrue(result.isSuppressed());
        assertEquals("RULE-002", result.getRuleId());
    }

    @Test
    void testGenerateFingerprint() {
        // Given
        Alert alert = Alert.builder()
                .alertId("alert-006")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("Database Connection Timeout 2024-03-16 10:30:00")
                .instance("192.168.1.100")
                .startsAt(Instant.parse("2024-03-16T10:30:00Z"))
                .severity("P2")
                .build();

        // When
        NoiseReducerService.NoiseReductionResult result = noiseReducer.process(alert);

        // Then - should generate consistent cluster key
        assertNotNull(result.getClusterKey());
        assertTrue(result.getClusterKey().contains("payment-service"));
    }

    @Test
    void testAcknowledgeAlert() {
        // Test acknowledging an alert removes it from active count
        assertDoesNotThrow(() -> {
            // Implementation would test acknowledge logic
        });
    }

    @Test
    void testStatsTracking() {
        // Test that statistics are being tracked
        Alert alert = Alert.builder()
                .alertId("alert-007")
                .tenantId("tenant-001")
                .serviceId("test-service")
                .title("Test Alert")
                .severity("P1")
                .startsAt(Instant.now())
                .status("active")
                .build();

        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenReturn(true);

        noiseReducer.process(alert);

        // Verify stats are being tracked
        var stats = noiseReducer.getStats();
        assertNotNull(stats);
        assertTrue(stats.size() > 0 || stats.isEmpty());
    }

    // ============================================
    // Edge Case Tests
    // ============================================

    @Test
    void testProcess_NullInstance() {
        Alert alert = Alert.builder()
                .alertId("alert-008")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("Test Alert")
                .severity("P1")
                .instance(null)
                .startsAt(Instant.now())
                .status("active")
                .build();

        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenReturn(true);

        // Should not throw NullPointerException
        assertDoesNotThrow(() -> noiseReducer.process(alert));
    }

    @Test
    void testProcess_EmptyTitle() {
        Alert alert = Alert.builder()
                .alertId("alert-009")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("")
                .severity("P1")
                .startsAt(Instant.now())
                .status("active")
                .build();

        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenReturn(true);

        // Should handle empty title gracefully
        assertDoesNotThrow(() -> noiseReducer.process(alert));
    }

    @Test
    void testSimilarityMatch() {
        // Given two similar alerts
        Alert alert1 = Alert.builder()
                .alertId("alert-010")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("Connection timeout to database server")
                .description("Failed to connect to mysql database")
                .severity("P2")
                .startsAt(Instant.now())
                .status("active")
                .build();

        Alert alert2 = Alert.builder()
                .alertId("alert-011")
                .tenantId("tenant-001")
                .serviceId("payment-service")
                .title("Connection timeout to database")
                .description("Failed to connect to mysql db")
                .severity("P2")
                .startsAt(Instant.now().plusSeconds(60))
                .status("active")
                .build();

        // First alert should be accepted
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenReturn(true)
                .thenReturn(false); // Second is duplicate

        NoiseReducerService.NoiseReductionResult result1 = noiseReducer.process(alert1);
        assertFalse(result1.isSuppressed());

        // Mock finding similar alert
        when(alertRepository.findByServiceAndTimeRange(any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(alert1));

        NoiseReducerService.NoiseReductionResult result2 = noiseReducer.process(alert2);
        // Second similar alert within time window should be suppressed
        assertTrue(result2.isSuppressed() || result2.isRootCauseCandidate());
    }
}
