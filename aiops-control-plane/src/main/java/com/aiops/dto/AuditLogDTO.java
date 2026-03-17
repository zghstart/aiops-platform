package com.aiops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {

    private Long id;
    private String tenantId;
    private String userId;
    private String userName;
    private String operationType;
    private String operationTypeDesc;
    private String resourceType;
    private String resourceId;
    private String operationDesc;
    private String httpMethod;
    private String requestPath;
    private String requestParams;
    private Integer responseStatus;
    private Long responseTimeMs;
    private String clientIp;
    private String userAgent;
    private Boolean isSuccess;
    private String errorMessage;
    private Boolean isSensitive;
    private String metadata;
    private LocalDateTime createdAt;
}
