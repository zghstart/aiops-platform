-- 审计日志表创建脚本
-- 用于记录所有关键操作的审计信息

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',

    -- 租户和用户信息
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    user_id VARCHAR(64) NOT NULL COMMENT '操作用户ID',
    user_name VARCHAR(100) COMMENT '用户名',

    -- 操作信息
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    resource_type VARCHAR(100) COMMENT '资源类型',
    resource_id VARCHAR(200) COMMENT '资源ID',
    operation_desc TEXT COMMENT '操作描述',

    -- HTTP信息
    http_method VARCHAR(10) COMMENT 'HTTP方法',
    request_path VARCHAR(500) COMMENT '请求路径',
    request_params TEXT COMMENT '请求参数',

    -- 响应信息
    response_status INT COMMENT '响应状态码',
    response_time_ms BIGINT COMMENT '响应时间(毫秒)',

    -- 客户端信息
    client_ip VARCHAR(50) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT 'User-Agent',

    -- 执行状态
    is_success BOOLEAN COMMENT '是否成功',
    error_message TEXT COMMENT '错误信息',

    -- 敏感标记
    is_sensitive BOOLEAN DEFAULT FALSE COMMENT '是否为敏感操作',
    metadata TEXT COMMENT '额外元数据(JSON)',

    -- 时间戳
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_user_id (user_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_at (created_at),
    INDEX idx_resource_type (resource_type),
    INDEX idx_is_sensitive (is_sensitive)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- 创建分区表（用于大数据量场景，按月分区）
-- ALTER TABLE audit_logs PARTITION BY RANGE (TO_DAYS(created_at)) (
--     PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
--     PARTITION p202402 VALUES LESS THAN (TO_DAYS('2024-03-01')),
--     PARTITION pmax VALUES LESS THAN MAXVALUE
-- );
