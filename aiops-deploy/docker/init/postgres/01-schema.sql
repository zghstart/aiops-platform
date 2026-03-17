-- AIOps PostgreSQL Schema

-- 租户表
CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(32) NOT NULL UNIQUE,
    status VARCHAR(16) DEFAULT 'active',
    api_key VARCHAR(128),
    ai_quota_daily INT DEFAULT 1000,
    ai_quota_used INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_code ON tenants(code);
CREATE INDEX idx_tenant_api_key ON tenants(api_key);

-- 服务实体表
CREATE TABLE IF NOT EXISTS service_entities (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    service_id VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_name VARCHAR(256),
    type VARCHAR(64),
    health VARCHAR(32) DEFAULT 'unknown',
    owner VARCHAR(64),
    status VARCHAR(16) DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_tenant ON service_entities(tenant_id);
CREATE INDEX idx_service_name ON service_entities(name);

-- 服务依赖表
CREATE TABLE IF NOT EXISTS service_dependencies (
    id SERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    source_service_id VARCHAR(128) NOT NULL,
    target_service_id VARCHAR(128) NOT NULL,
    dependency_type VARCHAR(32) DEFAULT 'calls',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, source_service_id, target_service_id)
);

-- 告警表
CREATE TABLE IF NOT EXISTS alerts (
    alert_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    incident_id VARCHAR(64),
    source VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(512) NOT NULL,
    description TEXT,
    service_id VARCHAR(128) NOT NULL,
    instance VARCHAR(64),
    status VARCHAR(32) DEFAULT 'active',
    ai_status VARCHAR(32),
    silenced_by VARCHAR(64),
    silence_reason VARCHAR(256),
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NULL,
    payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alert_tenant ON alerts(tenant_id);
CREATE INDEX idx_alert_incident ON alerts(incident_id);
CREATE INDEX idx_alert_status ON alerts(status);
CREATE INDEX idx_alert_service ON alerts(service_id);
CREATE INDEX idx_alert_time ON alerts(starts_at);

-- 告警标签表
CREATE TABLE IF NOT EXISTS alert_labels (
    id SERIAL PRIMARY KEY,
    alert_id VARCHAR(64) NOT NULL REFERENCES alerts(alert_id) ON DELETE CASCADE,
    label_key VARCHAR(128) NOT NULL,
    label_value VARCHAR(512)
);

CREATE INDEX idx_label_key ON alert_labels(label_key);
CREATE UNIQUE INDEX idx_alert_label ON alert_labels(alert_id, label_key);

-- 事件表
CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    cluster_key VARCHAR(128),
    service_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) DEFAULT 'analyzing',
    severity VARCHAR(16),
    title VARCHAR(512),
    description TEXT,
    root_cause TEXT,
    confidence DOUBLE PRECISION,
    recommendations TEXT,
    assigned_to VARCHAR(64),
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_incident_tenant ON incidents(tenant_id);
CREATE INDEX idx_incident_status ON incidents(status);
CREATE INDEX idx_incident_service ON incidents(service_id);
CREATE INDEX idx_incident_created ON incidents(created_at);

-- 噪声规则表
CREATE TABLE IF NOT EXISTS noise_rules (
    id SERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    name VARCHAR(128) NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    conditions JSONB NOT NULL,
    action VARCHAR(32) DEFAULT 'suppress',
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64),
    user_id VARCHAR(64),
    operation_type VARCHAR(32) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(128),
    description TEXT,
    old_value JSONB,
    new_value JSONB,
    http_method VARCHAR(8),
    http_path VARCHAR(512),
    http_params TEXT,
    ip_address VARCHAR(64),
    user_agent VARCHAR(256),
    status VARCHAR(16) DEFAULT 'success',
    error_message TEXT,
    duration_ms BIGINT,
    sensitive BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_tenant ON audit_logs(tenant_id);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_operation ON audit_logs(operation_type);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_time ON audit_logs(created_at);
CREATE INDEX idx_audit_sensitive ON audit_logs(sensitive);

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(32) DEFAULT 'user',
    status VARCHAR(16) DEFAULT 'active',
    last_login TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, username),
    UNIQUE(tenant_id, email)
);

-- 插入默认租户
INSERT INTO tenants (id, name, code, api_key) VALUES
('default', 'Default Tenant', 'default', 'default-api-key-123')
ON CONFLICT (id) DO NOTHING;

-- 插入默认用户
INSERT INTO users (id, tenant_id, username, email, password_hash, role) VALUES
('user-001', 'default', 'admin', 'admin@aiops.local', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin')
ON CONFLICT DO NOTHING;
