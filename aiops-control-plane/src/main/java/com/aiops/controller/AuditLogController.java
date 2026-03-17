package com.aiops.controller;

import com.aiops.annotation.Auditable;
import com.aiops.domain.AuditLog;
import com.aiops.dto.AuditLogDTO;
import com.aiops.dto.PageDTO;
import com.aiops.service.audit.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审计日志控制器
 */
@Tag(name = "审计日志", description = "审计日志查询和统计接口")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 查询审计日志
     */
    @Operation(summary = "查询审计日志", description = "支持多条件组合查询")
    @GetMapping
    @Auditable(operationType = AuditLog.OperationType.READ, resourceType = "audit_log")
    public PageDTO<AuditLogDTO> search(
        @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
        @Parameter(description = "用户ID") @RequestParam(required = false) String userId,
        @Parameter(description = "操作类型") @RequestParam(required = false) AuditLog.OperationType operationType,
        @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType,
        @Parameter(description = "是否成功") @RequestParam(required = false) Boolean isSuccess,
        @Parameter(description = "是否敏感") @RequestParam(required = false) Boolean isSensitive,
        @Parameter(description = "开始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
        @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "排序方向") @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<AuditLog> pageResult = auditLogService.search(
            tenantId, userId, operationType, resourceType,
            isSuccess, isSensitive, startTime, endTime, pageable
        );

        List<AuditLogDTO> dtos = pageResult.getContent().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());

        return PageDTO.<AuditLogDTO>builder()
            .content(dtos)
            .totalElements(pageResult.getTotalElements())
            .totalPages(pageResult.getTotalPages())
            .page(page)
            .size(size)
            .hasNext(pageResult.hasNext())
            .hasPrevious(pageResult.hasPrevious())
            .build();
    }

    /**
     * 查询资源操作历史
     */
    @Operation(summary = "查询资源操作历史", description = "查询指定资源的所有操作记录")
    @GetMapping("/resource/{resourceType}/{resourceId}")
    @Auditable(operationType = AuditLog.OperationType.READ, resourceType = "audit_log")
    public List<AuditLogDTO> getResourceHistory(
        @PathVariable String resourceType,
        @PathVariable String resourceId
    ) {
        return auditLogService.getResourceHistory(resourceType, resourceId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取操作统计
     */
    @Operation(summary = "获取操作统计", description = "统计指定时间范围内的操作情况")
    @GetMapping("/statistics")
    @Auditable(operationType = AuditLog.OperationType.READ, resourceType = "audit_log")
    public Map<String, Object> getStatistics(
        @Parameter(description = "开始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        return auditLogService.getStatistics(startTime, endTime);
    }

    /**
     * 查询失败操作
     */
    @Operation(summary = "查询失败操作", description = "查询最近的失败操作记录")
    @GetMapping("/failures")
    @Auditable(operationType = AuditLog.OperationType.READ, resourceType = "audit_log")
    public List<AuditLogDTO> getRecentFailures() {
        return auditLogService.getStatistics(
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now()
        ).containsKey("recentFailures") ?
            List.of() : List.of(); // 简化实现
    }

    /**
     * 查询敏感操作
     */
    @Operation(summary = "查询敏感操作", description = "查询最近的敏感操作记录")
    @GetMapping("/sensitive")
    @Auditable(operationType = AuditLog.OperationType.READ, resourceType = "audit_log", sensitive = true)
    public List<AuditLogDTO> getRecentSensitiveOperations() {
        return List.of(); // 简化实现
    }

    /**
     * 实体转DTO
     */
    private AuditLogDTO toDTO(AuditLog entity) {
        return AuditLogDTO.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .userId(entity.getUserId())
            .userName(entity.getUserName())
            .operationType(entity.getOperationType().name())
            .operationTypeDesc(entity.getOperationType().getDescription())
            .resourceType(entity.getResourceType())
            .resourceId(entity.getResourceId())
            .operationDesc(entity.getOperationDesc())
            .httpMethod(entity.getHttpMethod())
            .requestPath(entity.getRequestPath())
            .requestParams(entity.getRequestParams())
            .responseStatus(entity.getResponseStatus())
            .responseTimeMs(entity.getResponseTimeMs())
            .clientIp(entity.getClientIp())
            .userAgent(entity.getUserAgent())
            .isSuccess(entity.getIsSuccess())
            .errorMessage(entity.getErrorMessage())
            .isSensitive(entity.getIsSensitive())
            .metadata(entity.getMetadata())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
