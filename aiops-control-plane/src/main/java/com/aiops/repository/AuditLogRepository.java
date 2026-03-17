package com.aiops.repository;

import com.aiops.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志数据访问层
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 按租户查询审计日志
     */
    Page<AuditLog> findByTenantId(String tenantId, Pageable pageable);

    /**
     * 按用户查询审计日志
     */
    Page<AuditLog> findByUserId(String userId, Pageable pageable);

    /**
     * 按操作类型查询
     */
    Page<AuditLog> findByOperationType(AuditLog.OperationType operationType, Pageable pageable);

    /**
     * 按时间范围查询
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startTime AND :endTime")
    Page<AuditLog> findByTimeRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /**
     * 多条件组合查询
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:tenantId IS NULL OR a.tenantId = :tenantId) AND " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:operationType IS NULL OR a.operationType = :operationType) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:isSuccess IS NULL OR a.isSuccess = :isSuccess) AND " +
           "(:isSensitive IS NULL OR a.isSensitive = :isSensitive) AND " +
           "(:startTime IS NULL OR a.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR a.createdAt <= :endTime)")
    Page<AuditLog> search(
        @Param("tenantId") String tenantId,
        @Param("userId") String userId,
        @Param("operationType") AuditLog.OperationType operationType,
        @Param("resourceType") String resourceType,
        @Param("isSuccess") Boolean isSuccess,
        @Param("isSensitive") Boolean isSensitive,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /**
     * 统计用户操作次数
     */
    @Query("SELECT a.userId, COUNT(a) FROM AuditLog a " +
           "WHERE a.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY a.userId ORDER BY COUNT(a) DESC")
    List<Object[]> countByUser(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 统计操作类型分布
     */
    @Query("SELECT a.operationType, COUNT(a) FROM AuditLog a " +
           "WHERE a.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY a.operationType ORDER BY COUNT(a) DESC")
    List<Object[]> countByOperationType(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 查询失败操作
     */
    @Query("SELECT a FROM AuditLog a WHERE a.isSuccess = false " +
           "AND a.createdAt >= :startTime ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentFailures(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询敏感操作
     */
    @Query("SELECT a FROM AuditLog a WHERE a.isSensitive = true " +
           "AND a.createdAt >= :startTime ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentSensitiveOperations(@Param("startTime") LocalDateTime startTime);

    /**
     * 按资源查询操作历史
     */
    List<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
        String resourceType,
        String resourceId
    );
}
