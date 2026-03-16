CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(32) NOT NULL UNIQUE,
    status VARCHAR(16) DEFAULT 'active',
    api_key VARCHAR(128),
    ai_quota_daily INT DEFAULT 1000,
    ai_quota_used INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_code (code),
    INDEX idx_tenant_api_key (api_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_entities (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_name VARCHAR(256),
    type VARCHAR(64),
    owner VARCHAR(64),
    status VARCHAR(16) DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_service_tenant (tenant_id),
    INDEX idx_service_name (name),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    cluster_key VARCHAR(128),
    service_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) DEFAULT 'analyzing',
    severity VARCHAR(16),
    title VARCHAR(512),
    description TEXT,
    root_cause TEXT,
    confidence DOUBLE,
    recommendations TEXT,
    assigned_to VARCHAR(64),
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident_tenant (tenant_id),
    INDEX idx_incident_status (status),
    INDEX idx_incident_service (service_id),
    INDEX idx_incident_cluster (cluster_key),
    INDEX idx_incident_created (created_at),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alerts (
    alert_id VARCHAR(64) PRIMARY KEY,
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
    ends_at TIMESTAMP NULL,
    payload LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_tenant (tenant_id),
    INDEX idx_alert_incident (incident_id),
    INDEX idx_alert_status (status),
    INDEX idx_alert_service (service_id),
    INDEX idx_alert_time (starts_at),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    FOREIGN KEY (incident_id) REFERENCES incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alert_labels (
    alert_id VARCHAR(64) NOT NULL,
    label_key VARCHAR(128) NOT NULL,
    label_value VARCHAR(512),
    PRIMARY KEY (alert_id, label_key),
    INDEX idx_label_key (label_key),
    FOREIGN KEY (alert_id) REFERENCES alerts(alert_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_tags (
    service_id VARCHAR(64) NOT NULL,
    tag VARCHAR(128) NOT NULL,
    PRIMARY KEY (service_id, tag),
    FOREIGN KEY (service_id) REFERENCES service_entities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tenant_admins (
    tenant_id VARCHAR(64) NOT NULL,
    admin_email VARCHAR(128) NOT NULL,
    PRIMARY KEY (tenant_id, admin_email),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default tenant
INSERT INTO tenants (id, name, code, api_key) VALUES
('default', 'Default Tenant', 'default', 'default-api-key-123');
