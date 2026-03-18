# AIOps 智能运维平台 - 数据库设计文档

## 1. 数据库选型

| 存储类型 | 数据库 | 版本 | 用途 |
|----------|--------|------|------|
| 日志存储 | Apache Doris | 2.1+ | 日志/链路分析 |
| 指标存储 | VictoriaMetrics | 1.97+ | 时序指标 |
| 元数据 | MySQL | 8.0+ | CMDB/配置/工单 |
| 缓存 | Redis Cluster | 7.0+ | 推理缓存/会话 |
| 向量库 | Milvus | 2.3+ | RAG知识库 |

---

## 2. Apache Doris 表设计

### 2.1 日志表 (logs)

```sql
-- 主日志表
CREATE TABLE IF NOT EXISTS logs (
    `timestamp` DATETIME NOT NULL,
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
    `service_id` VARCHAR(64) NOT NULL COMMENT '服务ID',
    `level` VARCHAR(8) NOT NULL COMMENT '日志级别: ERROR/WARN/INFO/DEBUG',
    `trace_id` VARCHAR(32) COMMENT '分布式追踪ID',
    `span_id` VARCHAR(16) COMMENT 'Span ID',
    `host_ip` VARCHAR(15) COMMENT '主机IP',
    `pod_name` VARCHAR(128) COMMENT 'Kubernetes Pod名',
    `container` VARCHAR(64) COMMENT '容器名',
    `source_file` VARCHAR(256) COMMENT '源文件',
    `line_number` INT COMMENT '行号',
    `message` TEXT NOT NULL COMMENT '日志内容',
    `raw_message` TEXT COMMENT '原始日志',
    `parsed_fields` JSON COMMENT '解析出的字段',
    INDEX idx_message (`message`) USING INVERTED PROPERTIES("analyzer"="standard")
)
DUPLICATE KEY(`timestamp`, `tenant_id`, `service_id`)
PARTITION BY RANGE(`timestamp`) ()
DISTRIBUTED BY HASH(`service_id`) BUCKETS 16
PROPERTIES (
    "replication_num" = "3",
    "dynamic_partition.enable" = "true",
    "dynamic_partition.create_history_partition" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-30",
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "dynamic_partition.buckets" = "16"
);
```

### 2.2 链路追踪表 (traces)

```sql
CREATE TABLE IF NOT EXISTS traces (
    `timestamp` DATETIME NOT NULL,
    `tenant_id` VARCHAR(32) NOT NULL,
    `trace_id` VARCHAR(32) NOT NULL,
    `span_id` VARCHAR(16) NOT NULL,
    `parent_span_id` VARCHAR(16),
    `service_id` VARCHAR(64) NOT NULL,
    `operation` VARCHAR(256) NOT NULL COMMENT '操作名/接口',
    `span_kind` VARCHAR(8) COMMENT 'server/client/producer/consumer',
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME,
    `duration_ms` INT COMMENT '耗时毫秒',
    `status` VARCHAR(8) COMMENT 'OK/ERROR',
    `http_method` VARCHAR(8),
    `http_status` INT,
    `http_url` VARCHAR(1024),
    `error_msg` TEXT,
    `attributes` JSON COMMENT '附加属性',
    INDEX idx_trace (`trace_id`) USING BITMAP
)
DUPLICATE KEY(`timestamp`, `trace_id`, `span_id`)
PARTITION BY RANGE(`timestamp`) ()
DISTRIBUTED BY HASH(`trace_id`) BUCKETS 16
PROPERTIES (
    "replication_num" = "3",
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-7",
    "dynamic_partition.end" = "3"
);
```

### 2.3 告警事件表 (alerts)

```sql
CREATE TABLE IF NOT EXISTS alerts (
    `alert_id` VARCHAR(32) NOT NULL COMMENT '唯一告警ID',
    `tenant_id` VARCHAR(32) NOT NULL,
    `incident_id` VARCHAR(32) COMMENT '关联故障ID',
    `source` VARCHAR(32) NOT NULL COMMENT '告警来源: prometheus/cmdb/ai',
    `severity` VARCHAR(16) NOT NULL COMMENT 'critical/warning/info',
    `status` VARCHAR(16) NOT NULL COMMENT 'active/suppressed/resolved',
    `title` VARCHAR(512) NOT NULL,
    `description` TEXT,
    `service_id` VARCHAR(64),
    `instance` VARCHAR(256) COMMENT '实例标识',
    `labels` JSON COMMENT '标签KV',
    `starts_at` DATETIME NOT NULL,
    `ends_at` DATETIME,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME,
    `silenced_until` DATETIME COMMENT '抑制截止时间',
    `silenced_by` VARCHAR(64) COMMENT '抑制规则ID',
    `noise_reduced` BOOLEAN DEFAULT FALSE COMMENT '是否经过降噪',
    `root_cause_candidate` BOOLEAN DEFAULT FALSE COMMENT '是否为根因候选',
    INDEX idx_service (`service_id`) USING BITMAP,
    INDEX idx_status (`status`) USING BITMAP
)
UNIQUE KEY(`alert_id`)
PARTITION BY RANGE(`created_at`) ()
DISTRIBUTED BY HASH(`tenant_id`) BUCKETS 16
PROPERTIES (
    "replication_num" = "3",
    "enable_unique_key_merge_on_write" = "true"
);
```

### 2.4 AI诊断结果表 (ai_analysis)

```sql
CREATE TABLE IF NOT EXISTS ai_analysis (
    `analysis_id` VARCHAR(32) NOT NULL,
    `incident_id` VARCHAR(32) NOT NULL,
    `tenant_id` VARCHAR(32) NOT NULL,
    `alert_ids` ARRAY<VARCHAR(32)>,
    `status` VARCHAR(16) NOT NULL COMMENT 'running/completed/failed',
    `confidence` DECIMAL(3,2) COMMENT '置信度 0-1',
    `root_cause` TEXT COMMENT '根因描述',
    `root_cause_category` VARCHAR(64) COMMENT '根因分类',
    `impacted_services` ARRAY<VARCHAR(64)> COMMENT '影响的服务',
    `reasoning_steps` JSON COMMENT '推理步骤详细',
    `thinking_process` TEXT COMMENT '思考过程文本',
    `recommendations` JSON COMMENT '建议列表',
    `human_feedback` VARCHAR(16) COMMENT 'useful/not_useful/auto',
    `human_comment` TEXT COMMENT '人工反馈备注',
    `similar_cases` JSON COMMENT '相似案例',
    `tokens_input` INT COMMENT '输入token数',
    `tokens_output` INT COMMENT '输出token数',
    `cost_usd` DECIMAL(10,6) COMMENT '推理成本USD',
    `latency_ms` INT COMMENT '耗时毫秒',
    `model_version` VARCHAR(32) COMMENT '模型版本',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `completed_at` DATETIME,
    `updated_by` VARCHAR(64),
    INDEX idx_incident (`incident_id`) USING BITMAP
)
UNIQUE KEY(`analysis_id`)
DISTRIBUTED BY HASH(`incident_id`) BUCKETS 8
PROPERTIES (
    "replication_num" = "3"
);
```

### 2.5 AI推理成本表 (inference_cost)

```sql
CREATE TABLE IF NOT EXISTS inference_cost (
    `timestamp` DATETIME NOT NULL,
    `tenant_id` VARCHAR(32) NOT NULL,
    `analysis_id` VARCHAR(32),
    `model` VARCHAR(32) NOT NULL,
    `tokens_input` INT NOT NULL,
    `tokens_output` INT NOT NULL,
    `cost_usd` DECIMAL(10,6) NOT NULL,
    `latency_ms` INT,
    `cache_hit` BOOLEAN DEFAULT FALSE,
    `source_ip` VARCHAR(15)
)
DUPLICATE KEY(`timestamp`, `tenant_id`)
PARTITION BY RANGE(`timestamp`) ()
DISTRIBUTED BY HASH(`tenant_id`) BUCKETS 8
PROPERTIES (
    "replication_num" = "3",
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "MONTH",
    "dynamic_partition.end" = "12"
);
```

---

## 3. MySQL 表设计

### 3.1 租户表 (tenants)

```sql
CREATE TABLE IF NOT EXISTS tenants (
    `id` VARCHAR(32) PRIMARY KEY COMMENT '租户ID',
    `name` VARCHAR(64) NOT NULL COMMENT '租户名称',
    `status` TINYINT DEFAULT 1 COMMENT '1:active 0:disabled',
    `type` VARCHAR(16) DEFAULT 'shared' COMMENT 'shared/dedicated',
    `plan` VARCHAR(16) DEFAULT 'basic' COMMENT 'basic/pro/enterprise',
    `quota_incidents_per_day` INT DEFAULT 1000,
    `quota_ai_tokens_per_month` BIGINT DEFAULT 10000000,
    `storage_retention_days` INT DEFAULT 30,
    `admin_email` VARCHAR(128) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `expired_at` DATE COMMENT '过期时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 服务表 (services)

```sql
CREATE TABLE IF NOT EXISTS services (
    `id` VARCHAR(64) PRIMARY KEY COMMENT '服务ID',
    `tenant_id` VARCHAR(32) NOT NULL,
    `name` VARCHAR(128) NOT NULL COMMENT '服务名称',
    `display_name` VARCHAR(256) COMMENT '显示名称',
    `owner_team` VARCHAR(64) COMMENT '负责团队',
    `owner_email` VARCHAR(128),
    `service_tier` TINYINT DEFAULT 3 COMMENT '1:critical 2:high 3:normal',
    `language` VARCHAR(32) COMMENT '技术栈',
    `repo_url` VARCHAR(512),
    `doc_url` VARCHAR(512),
    `alert_channels` JSON COMMENT '告警通知渠道',
    `labels` JSON COMMENT '标签',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (`tenant_id`),
    INDEX idx_owner (`owner_team`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 CMDB实例表 (cmdb_instances)

```sql
CREATE TABLE IF NOT EXISTS cmdb_instances (
    `id` VARCHAR(64) PRIMARY KEY,
    `tenant_id` VARCHAR(32) NOT NULL,
    `asset_id` VARCHAR(64) COMMENT '资产编号',
    `name` VARCHAR(128) NOT NULL,
    `type` VARCHAR(32) NOT NULL COMMENT 'server/database/network/middleware',
    `ip` VARCHAR(15),
    `private_ip` VARCHAR(15),
    `public_ip` VARCHAR(15),
    `hostname` VARCHAR(128),
    `idc` VARCHAR(64) COMMENT '机房',
    `zone` VARCHAR(64) COMMENT '可用区',
    `rack` VARCHAR(64) COMMENT '机架',
    `spec_cpu` INT,
    `spec_memory` VARCHAR(16),
    `spec_disk` VARCHAR(64),
    `os_type` VARCHAR(32),
    `os_version` VARCHAR(64),
    `status` VARCHAR(16) DEFAULT 'running' COMMENT 'running/stopped/maintenance',
    `labels` JSON,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (`tenant_id`),
    INDEX idx_ip (`ip`),
    INDEX idx_type (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.4 服务拓扑关系表 (service_topology)

```sql
CREATE TABLE IF NOT EXISTS service_topology (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` VARCHAR(32) NOT NULL,
    `source_service` VARCHAR(64) NOT NULL COMMENT '调用方',
    `target_service` VARCHAR(64) NOT NULL COMMENT '被调用方',
    `relation_type` VARCHAR(32) DEFAULT 'calls' COMMENT 'calls/depends_on/contains',
    `protocol` VARCHAR(32) COMMENT 'http/grpc/mysql/redis',
    `weight` INT DEFAULT 1 COMMENT '调用权重',
    `auto_discovered` BOOLEAN DEFAULT TRUE,
    `verified_at` TIMESTAMP COMMENT '人工验证时间',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_relation (`tenant_id`, `source_service`, `target_service`),
    INDEX idx_source (`source_service`),
    INDEX idx_target (`target_service`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.5 工单表 (tickets)

```sql
CREATE TABLE IF NOT EXISTS tickets (
    `id` VARCHAR(32) PRIMARY KEY,
    `tenant_id` VARCHAR(32) NOT NULL,
    `incident_id` VARCHAR(32),
    `analysis_id` VARCHAR(32),
    `title` VARCHAR(512) NOT NULL,
    `description` TEXT,
    `type` VARCHAR(32) DEFAULT 'incident' COMMENT 'incident/request/change',
    `priority` TINYINT DEFAULT 3 COMMENT '1:critical 2:high 3:medium 4:low',
    `status` VARCHAR(32) DEFAULT 'open' COMMENT 'open/in_progress/pending/resolved/closed',
    `assignee` VARCHAR(64) COMMENT '处理人',
    `assignee_team` VARCHAR(64),
    `created_by` VARCHAR(64),
    `closed_by` VARCHAR(64),
    `resolution` TEXT COMMENT '解决方案',
    `rca_category` VARCHAR(64) COMMENT '根因分类',
    `ai_assistance_level` VARCHAR(16) COMMENT 'ai_only/ai_human/human_only',
    `satisfaction_score` TINYINT COMMENT '满意度 1-5',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `resolved_at` TIMESTAMP,
    `closed_at` TIMESTAMP,
    INDEX idx_tenant (`tenant_id`),
    INDEX idx_status (`status`),
    INDEX idx_assignee (`assignee`),
    INDEX idx_created (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.6 降噪规则表 (noise_rules)

```sql
CREATE TABLE IF NOT EXISTS noise_rules (
    `id` VARCHAR(32) PRIMARY KEY,
    `tenant_id` VARCHAR(32) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `description` TEXT,
    `enabled` BOOLEAN DEFAULT TRUE,
    `rule_type` VARCHAR(32) NOT NULL COMMENT 'filter/aggregate/inhibit',

    -- 匹配条件
    `match_services` JSON COMMENT '匹配的服务',
    `match_labels` JSON COMMENT '匹配的标签',
    `match_patterns` JSON COMMENT '正则匹配',

    -- 处理动作
    `action` VARCHAR(32) NOT NULL COMMENT 'drop/aggregate/inhibit/silence',
    `action_params` JSON COMMENT '动作参数',

    -- 抑制/聚合参数
    `group_by` JSON COMMENT '聚合字段',
    `duration_seconds` INT COMMENT '持续时长',
    `threshold_count` INT COMMENT '触发阈值',

    `created_by` VARCHAR(64),
    `updated_by` VARCHAR(64),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (`tenant_id`),
    INDEX idx_enabled (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. Redis 数据设计

### 4.1 Key命名规范

```
aiops:{tenant}:{type}:{id}
```

| Key模式 | TTL | 用途 |
|---------|-----|------|
| `aiops:{tenant}:analysis:{id}` | 3600 | 诊断结果缓存 |
| `aiops:{tenant}:session:{user_id}` | 1800 | 用户会话 |
| `aiops:{tenant}:rate:{api}` | 60 | 限流计数 |
| `aiops:{tenant}:alert:fingerprint:{hash}` | 300 | 告警指纹去重 |
| `aiops:{tenant}:cache:llm:{prompt_hash}` | 86400 | LLM结果缓存 |
| `aiops:{tenant}:lock:{resource}` | 30 | 分布式锁 |
| `aiops:{tenant}:stream:incident:{id}` | - | 实时事件流 |

### 4.2 告警指纹去重

```
# Key: aiops:{tenant}:alert:fingerprint:{hash}
# Value: incident_id
# TTL: 5分钟

# 生成指纹
fingerprint = hash(
    service_id +
    alert_name +
    instance +
    floor(timestamp/300)  # 5分钟窗口
)
```

---

## 5. Milvus 向量库设计

### 5.1 历史Case集合 (historical_cases)

```python
# 集合定义
schema = CollectionSchema(
    fields=[
        FieldSchema(name="case_id", dtype=DataType.VARCHAR, max_length=32, is_primary=True),
        FieldSchema(name="tenant_id", dtype=DataType.VARCHAR, max_length=32),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1536),
        FieldSchema(name="root_cause", dtype=DataType.VARCHAR, max_length=2048),
        FieldSchema(name="resolution", dtype=DataType.VARCHAR, max_length=4096),
        FieldSchema(name="service_id", dtype=DataType.VARCHAR, max_length=64),
        FieldSchema(name="alert_summary", dtype=DataType.VARCHAR, max_length=1024),
        FieldSchema(name="created_at", dtype=DataType.INT64),  # timestamp
        FieldSchema(name="feedback_score", dtype=DataType.INT8),  # 1-5评分
    ],
    description="Historical incident cases for RAG"
)

# 索引
index_params = {
    "metric_type": "COSINE",
    "index_type": "IVF_FLAT",
    "params": {"nlist": 128}
}
```

---

## 6. 性能优化设计

### 6.1 索引优化

| 数据库 | 优化策略 | 详细配置 |
|--------|----------|----------|
| Doris | 倒排索引 | 为日志消息和告警标题创建倒排索引 |
| Doris | 前缀索引 | 为service_id、tenant_id等字段创建前缀索引 |
| MySQL | 复合索引 | 为频繁查询的字段组合创建复合索引 |
| MySQL | 覆盖索引 | 为常用查询创建覆盖索引，减少回表 |
| Redis | 数据结构优化 | 使用合适的数据结构（Hash、List、Set） |
| Milvus | 向量索引 | 使用IVF_FLAT索引加速向量搜索 |

### 6.2 查询优化

- **Doris**: 使用预聚合表，减少计算开销
- **Doris**: 合理使用分区剪枝，减少扫描范围
- **MySQL**: 避免全表扫描，使用索引覆盖
- **MySQL**: 优化JOIN操作，减少关联查询
- **Redis**: 使用Pipeline批量操作，减少网络往返
- **Prometheus**: 使用合适的查询时间范围，避免查询过多数据

### 6.3 存储优化

- **Doris**: 合理设置分区和分桶策略
- **Doris**: 使用合适的压缩算法
- **MySQL**: 合理设置表结构，避免大字段
- **Redis**: 设置合理的过期时间，避免内存溢出
- **Milvus**: 合理设置向量维度和索引参数

## 7. 安全性设计

### 7.1 数据安全

- **Doris**: 列级加密（AES-256-GCM）
- **MySQL**: 敏感字段加密存储
- **Redis**: 使用密码认证和ACL
- **传输加密**: 所有数据库连接使用TLS加密
- **数据脱敏**: 敏感信息（如手机号、身份证）脱敏存储

### 7.2 访问控制

- **Doris**: 基于角色的访问控制
- **MySQL**: 最小权限原则，为不同角色分配不同权限
- **Redis**: 使用ACL控制访问权限
- **连接限制**: 限制数据库连接数和并发数
- **审计日志**: 记录所有数据库操作

### 7.3 备份与恢复

- **Doris**: 定期备份，支持增量备份
- **MySQL**: 每日全量备份 + 实时binlog复制
- **Redis**: 定期RDB备份 + AOF持久化
- **Milvus**: 定期快照备份
- **备份验证**: 定期验证备份的可用性

## 8. 数据生命周期管理

### 8.1 数据分层策略

| 数据类型 | 存储介质 | 保留期限 | 访问频率 | 存储策略 |
|----------|----------|----------|----------|----------|
| 热数据 | Doris主表 + Redis缓存 | 最近7天 | 高 | 高性能存储，实时索引 |
| 温数据 | Doris温存储 | 7-30天 | 中 | 平衡性能和成本 |
| 冷数据 | Doris冷存储/外部存储 | 30-90天 | 低 | 压缩存储，按需加载 |
| 归档数据 | 对象存储 (S3/OSS) | 90天+ | 极低 | 低成本存储，离线访问 |

### 8.2 数据清理机制

- **Doris**: 使用动态分区和TTL策略自动清理过期数据
- **MySQL**: 定期清理过期的审计日志和工单数据
- **Redis**: 设置合理的过期时间，自动清理缓存数据
- **Prometheus**: 使用配置的存储期限自动清理旧指标
- **Milvus**: 定期清理不常用的向量数据

### 8.3 归档策略

1. **自动归档**: 超过90天的数据自动归档到对象存储
2. **归档格式**: 使用压缩格式存储，减少存储空间
3. **归档索引**: 为归档数据建立索引，支持快速检索
4. **归档访问**: 提供归档数据的查询接口，支持按需访问
5. **归档验证**: 定期验证归档数据的完整性

### 8.4 数据生命周期管理工具

- **调度工具**: 使用Airflow或类似工具调度数据生命周期任务
- **监控工具**: 监控数据增长和存储使用情况
- **告警机制**: 当存储使用率超过阈值时触发告警
- **报表生成**: 定期生成数据生命周期管理报表

---

*本文档定义了AIOps平台的数据库Schema，开发时应以此为准。*
