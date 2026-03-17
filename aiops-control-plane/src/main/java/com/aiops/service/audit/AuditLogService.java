package com.aiops.service.audit;

import com.aiops.domain.AuditLog;
import com.aiops.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * 查询审计日志
     */
    public Page<AuditLog> search(
        String tenantId,
        String userId,
        AuditLog.OperationType operationType,
        String resourceType,
        Boolean isSuccess,
        Boolean isSensitive,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Pageable pageable
    ) {
        return auditLogRepository.search(
            tenantId, userId, operationType, resourceType,
            isSuccess, isSensitive, startTime, endTime, pageable
        );
    }

    /**
     * 按租户查询
     */
    public Page<AuditLog> findByTenant(String tenantId, Pageable pageable) {
        return auditLogRepository.findByTenantId(tenantId, pageable);
    }

    /**
     * 按用户查询
     */
    public Page<AuditLog> findByUser(String userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }

    /**
     * 按资源查询操作历史
     */
    public List<AuditLog> getResourceHistory(String resourceType, String resourceId) {
        return auditLogRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            resourceType, resourceId
        );
    }

    /**
     * 获取操作统计
     */
    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();

        // 按用户统计
        List<Object[]> userStats = auditLogRepository.countByUser(startTime, endTime);
        stats.put("byUser", convertToMap(userStats));

        // 按操作类型统计
        List<Object[]> typeStats = auditLogRepository.countByOperationType(startTime, endTime);
        stats.put("byOperationType", convertToMap(typeStats));

        // 最近失败操作
        List<AuditLog> recentFailures = auditLogRepository.findRecentFailures(
            LocalDateTime.now().minusDays(1)
        );
        stats.put("recentFailures", recentFailures.size());

        // 最近敏感操作
        List<AuditLog> sensitiveOps = auditLogRepository.findRecentSensitiveOperations(
            LocalDateTime.now().minusDays(1)
        );
        stats.put("recentSensitiveOperations", sensitiveOps.size());

        return stats;
    }

    /**
     * 手动记录审计日志（用于特殊情况）
     */
    @Transactional
    public AuditLog recordManualLog(
        String tenantId,
        String userId,
        String userName,
        AuditLog.OperationType operationType,
        String resourceType,
        String resourceId,
        String description,
        boolean isSensitive
    ) {
        AuditLog auditLog = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .userName(userName)
            .operationType(operationType)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .operationDesc(description)
            .isSensitive(isSensitive)
            .isSuccess(true)
            .createdAt(LocalDateTime.now())
            .build();

        return auditLogRepository.save(auditLog);
    }

    /**
     * 清理过期审计日志
     */
    @Transactional
    public int cleanOldLogs(LocalDateTime beforeTime) {
        // 审计日志通常需要长期保留，这里提供清理接口但需要谨慎使用
        log.warn("Cleaning audit logs before: {}", beforeTime);
        List<AuditLog> oldLogs = auditLogRepository.findAll().stream()
            .filter(log -> log.getCreatedAt().isBefore(beforeTime))
            .toList();

        auditLogRepository.deleteAll(oldLogs);
        log.info("Deleted {} old audit logs", oldLogs.size());
        return oldLogs.size();
    }

    /**
     * 转换统计结果为Map
     */
    private Map<String, Long> convertToMap(List<Object[]> list) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : list) {
            map.put(String.valueOf(row[0]), (Long) row[1]);
        }
        return map;
    }
}
