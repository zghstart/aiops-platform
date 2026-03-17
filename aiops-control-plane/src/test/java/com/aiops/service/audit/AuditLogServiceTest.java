package com.aiops.service.audit;

import com.aiops.domain.AuditLog;
import com.aiops.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 审计日志服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private AuditLog testAuditLog;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        testAuditLog = AuditLog.builder()
            .id(1L)
            .tenantId("tenant-1")
            .userId("user-1")
            .userName("test-user")
            .operationType(AuditLog.OperationType.LOGIN)
            .resourceType("user")
            .resourceId("user-1")
            .operationDesc("用户登录")
            .isSensitive(true)
            .isSuccess(true)
            .createdAt(now)
            .build();
    }

    @Test
    void testSearch() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        List<AuditLog> logs = new ArrayList<>();
        logs.add(testAuditLog);
        Page<AuditLog> pageResult = new PageImpl<>(logs, pageable, 1);

        when(auditLogRepository.search(
            isNull(), isNull(), isNull(), isNull(),
            isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(pageResult);

        // When
        Page<AuditLog> result = auditLogService.search(
            null, null, null, null,
            null, null, null, null, pageable
        );

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("tenant-1", result.getContent().get(0).getTenantId());
        verify(auditLogRepository).search(
            isNull(), isNull(), isNull(), isNull(),
            isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        );
    }

    @Test
    void testFindByTenant() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditLog> pageResult = new PageImpl<>(List.of(testAuditLog));

        when(auditLogRepository.findByTenantId("tenant-1", pageable))
            .thenReturn(pageResult);

        // When
        Page<AuditLog> result = auditLogService.findByTenant("tenant-1", pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(auditLogRepository).findByTenantId("tenant-1", pageable);
    }

    @Test
    void testGetResourceHistory() {
        // Given
        when(auditLogRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("alert", "alert-1"))
            .thenReturn(List.of(testAuditLog));

        // When
        List<AuditLog> history = auditLogService.getResourceHistory("alert", "alert-1");

        // Then
        assertNotNull(history);
        assertEquals(1, history.size());
        verify(auditLogRepository).findByResourceTypeAndResourceIdOrderByCreatedAtDesc("alert", "alert-1");
    }

    @Test
    void testGetStatistics() {
        // Given
        LocalDateTime startTime = now.minusDays(7);
        LocalDateTime endTime = now;

        List<Object[]> userStats = new ArrayList<>();
        userStats.add(new Object[]{"user-1", 10L});

        List<Object[]> typeStats = new ArrayList<>();
        typeStats.add(new Object[]{"LOGIN", 5L});

        when(auditLogRepository.countByUser(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(userStats);

        when(auditLogRepository.countByOperationType(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(typeStats);

        when(auditLogRepository.findRecentFailures(any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());

        when(auditLogRepository.findRecentSensitiveOperations(any(LocalDateTime.class)))
            .thenReturn(List.of(testAuditLog));

        // When
        Map<String, Object> stats = auditLogService.getStatistics(startTime, endTime);

        // Then
        assertNotNull(stats);
        assertTrue(stats.containsKey("byUser"));
        assertTrue(stats.containsKey("byOperationType"));
        assertTrue(stats.containsKey("recentFailures"));
        assertTrue(stats.containsKey("recentSensitiveOperations"));
        assertEquals(1, stats.get("recentSensitiveOperations"));
    }

    @Test
    void testRecordManualLog() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class)))
            .thenReturn(testAuditLog);

        // When
        AuditLog result = auditLogService.recordManualLog(
            "tenant-1", "user-1", "test-user",
            AuditLog.OperationType.LOGIN,
            "user", "user-1", "手动记录登录", true
        );

        // Then
        assertNotNull(result);
        assertEquals("tenant-1", result.getTenantId());
        assertEquals("user-1", result.getUserId());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void testCleanOldLogs() {
        // Given
        LocalDateTime beforeTime = now.minusDays(90);
        List<AuditLog> oldLogs = new ArrayList<>();
        oldLogs.add(testAuditLog);

        when(auditLogRepository.findAll()).thenReturn(oldLogs);

        // When
        int deleted = auditLogService.cleanOldLogs(beforeTime);

        // Then
        assertTrue(deleted >= 0);
        verify(auditLogRepository).deleteAll(anyList());
    }
}
