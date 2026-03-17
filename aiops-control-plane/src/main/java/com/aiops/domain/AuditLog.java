package com.aiops.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 审计日志实体
 * 记录所有关键操作的审计信息
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_operation_type", columnList = "operation_type"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_resource_type", columnList = "resource_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 操作用户ID
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * 用户名
     */
    @Column(name = "user_name")
    private String userName;

    /**
     * 操作类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 50)
    private OperationType operationType;

    /**
     * 资源类型
     */
    @Column(name = "resource_type", length = 100)
    private String resourceType;

    /**
     * 资源ID
     */
    @Column(name = "resource_id", length = 200)
    private String resourceId;

    /**
     * 操作描述
     */
    @Column(name = "operation_desc", columnDefinition = "TEXT")
    private String operationDesc;

    /**
     * 请求方法
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /**
     * 请求路径
     */
    @Column(name = "request_path", length = 500)
    private String requestPath;

    /**
     * 请求参数
     */
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    /**
     * 响应状态码
     */
    @Column(name = "response_status")
    private Integer responseStatus;

    /**
     * 响应时间（毫秒）
     */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /**
     * 客户端IP
     */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /**
     * User-Agent
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * 是否成功
     */
    @Column(name = "is_success")
    private Boolean isSuccess;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 是否为敏感操作
     */
    @Column(name = "is_sensitive")
    private Boolean isSensitive;

    /**
     * 额外元数据（JSON格式）
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        // 认证操作
        LOGIN("用户登录", true),
        LOGOUT("用户登出", false),
        LOGIN_FAILED("登录失败", true),

        // 数据操作
        CREATE("创建", false),
        UPDATE("更新", false),
        DELETE("删除", true),
        READ("查询", false),
        EXPORT("导出", true),

        // 系统操作
        CONFIG_CHANGE("配置变更", true),
        PERMISSION_CHANGE("权限变更", true),
        PASSWORD_CHANGE("密码变更", true),

        // 告警操作
        ALERT_ACKNOWLEDGE("告警确认", false),
        ALERT_CLOSE("告警关闭", false),
        ALERT_ESCALATE("告警升级", false),

        // 事件操作
        INCIDENT_CREATE("事件创建", false),
        INCIDENT_UPDATE("事件更新", false),
        INCIDENT_CLOSE("事件关闭", false),

        // 其他
        OTHER("其他操作", false);

        private final String description;
        private final boolean sensitive;

        OperationType(String description, boolean sensitive) {
            this.description = description;
            this.sensitive = sensitive;
        }

        public String getDescription() {
            return description;
        }

        public boolean isSensitive() {
            return sensitive;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
