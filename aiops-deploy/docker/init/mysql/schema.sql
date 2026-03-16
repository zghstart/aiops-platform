-- AIOps Database Schema
-- MySQL 8.0

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- Tenants
-- ============================================
CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) DEFAULT 'active',
    config JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- Services
-- ============================================
CREATE TABLE IF NOT EXISTS service_entities (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_name VARCHAR(256),
    type VARCHAR(64) DEFAULT 'service',
    owner VARCHAR(64),
    team VARCHAR(128),
    health VARCHAR(32) DEFAULT 'unknown',
    status VARCHAR(16) DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_service (tenant_id, service_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_health (health)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- Service Dependencies
-- ============================================
CREATE TABLE IF NOT EXISTS service_dependencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    source_service_id VARCHAR(128) NOT NULL,
    target_service_id VARCHAR(128) NOT NULL,
    dependency_type VARCHAR(64) DEFAULT 'depends',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dependency (tenant_id, source_service_id, target_service_id),
    INDEX idx_source (tenant_id, source_service_id),
    INDEX idx_target (tenant_id, target_service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- Alerts
-- ============================================
CREATE TABLE IF NOT EXISTS alerts (
    alert_id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
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
    ends_at TIMESTAMP,
    payload LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_incident (incident_id),
    INDEX idx_service (service_id),
    INDEX idx_status (status),
    INDEX idx_severity (severity),
    INDEX idx_starts_at (starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- Alert Labels
-- ============================================
CREATE TABLE IF NOT EXISTS alert_labels (
    alert_id VARCHAR(64) NOT NULL,
    label_key VARCHAR(128) NOT NULL,
    label_value VARCHAR(512),
    PRIMARY KEY (alert_id, label_key),
    INDEX idx_label_key (label_key),
    INDEX idx_label_value (label_value(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- Incidents
-- ============================================
CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    cluster_key VARCHAR(256) NOT NULL,
    service_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) DEFAULT 'analyzing',
    root_cause TEXT,
    confidence DECIMAL(3,2),
    resolution TEXT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_service (service_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- Users
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(256) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(32) DEFAULT 'user',
    status VARCHAR(16) DEFAULT 'active',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    UNIQUE KEY uk_tenant_email (tenant_id, email),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- Noise Reduction Rules
-- ============================================
CREATE TABLE IF NOT EXISTS noise_rules (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    priority INT DEFAULT 100,
    condition_type VARCHAR(32) NOT NULL,
    condition_config JSON NOT NULL,
    action VARCHAR(32) DEFAULT 'suppress',
    suppress_duration_minutes INT DEFAULT 60,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- Insert Default Data
-- ============================================

-- Default tenant
INSERT INTO tenants (id, name, status, config) VALUES
('default', 'Default Tenant', 'active', '{"features":["ai_analysis","topology"]}');

-- Default noise rules
INSERT INTO noise_rules (id, tenant_id, name, description, priority, condition_type, condition_config, action, suppress_duration_minutes) VALUES
('rule-001', 'default', 'Suppress Test Alerts', 'Suppress low severity alerts from test environment', 100, 'label_match', '{"labels":{"env":"test|staging"},"severity":"P4|P5"}', 'suppress', 30),
('rule-002', 'default', 'Flaky Health Checks', 'Suppress transient health check failures', 90, 'title_regex', '{"pattern":"health check.*timeout|probe failed","source":"prometheus|blackbox"}', 'suppress', 10);

SET FOREIGN_KEY_CHECKS = 1;
