# AIOps 智能运维平台 - API接口规范

## 1. 接口设计原则

- RESTful设计，版本号v1
- 统一返回格式：{code, message, data}
- 认证：JWT Token + API Key
- 限流：租户级别限流

---

## 2. 接口列表

### 2.1 告警接口

#### 接收告警
```http
POST /api/v1/alerts/webhook
Content-Type: application/json
X-API-Key: {tenant_api_key}

# Request
{
  "alert_id": "alert-20240314-001",
  "source": "prometheus",           # prometheus/zebrium/custom
  "severity": "critical",            # critical/warning/info
  "title": "DB Connection Pool Exhausted",
  "description": "Connection pool full on payment-db",
  "service_id": "payment-service",
  "labels": {
    "instance": "10.0.1.15:3306",
    "job": "mysql-exporter",
    "tenant_id": "tenant_001"
  },
  "starts_at": "2024-03-14T14:30:00Z",
  "payload": {                       # 原始告警数据
    "value": 100,
    "threshold": 80
  }
}

# Response
{
  "code": 200,
  "message": "Alert received",
  "data": {
    "incident_id": "inc-20240314-001",
    "status": "analyzing"
  }
}
```

#### 查询告警列表
```http
GET /api/v1/alerts?status=active&tenant_id=tenant_001&page=1&size=20
Authorization: Bearer {jwt_token}

# Response
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 156,
    "page": 1,
    "size": 20,
    "items": [
      {
        "alert_id": "alert-20240314-001",
        "incident_id": "inc-20240314-001",
        "status": "analyzing",
        "severity": "critical",
        "title": "DB Connection Pool Exhausted",
        "service_id": "payment-service",
        "created_at": "2024-03-14T14:30:00Z",
        "ai_status": "in_progress"      # pending/in_progress/completed
      }
    ]
  }
}
```

### 2.2 AI诊断接口

#### 触发AI诊断
```http
POST /api/v1/ai/analyze
Content-Type: application/json
Authorization: Bearer {jwt_token}

# Request
{
  "incident_id": "inc-20240314-001",
  "alert_ids": ["alert-20240314-001"],
  "context": {
    "service_id": "payment-service",
    "ip": "10.0.1.15",
    "time_range": {
      "start": "2024-03-14T14:00:00Z",
      "end": "2024-03-14T14:30:00Z"
    },
    "topology_depth": 2              # 拓扑展开层级
  },
  "options": {
    "stream": true,                  # 是否流式输出
    "max_rounds": 5,                 # AI最大推理轮数
    "confidence_threshold": 0.7      # 最低置信度
  }
}

# Response (非流式)
{
  "code": 200,
  "message": "Analysis completed",
  "data": {
    "incident_id": "inc-20240314-001",
    "analysis_id": "ana-20240314-001",
    "status": "completed",
    "confidence": 0.87,
    "conclusion": {
      "root_cause": "Disk I/O bottleneck on payment-db master",
      "description": "SSD health degradation caused 98% IO utilization",
      "impact": ["payment-service", "order-service"]
    },
    "reasoning_chain": [
      {
        "step": 1,
        "timestamp": "2024-03-14T14:30:05Z",
        "action": "search_logs",
        "tool": "doris_search",
        "input": {
          "service": "payment-service",
          "level": "ERROR",
          "time_range": "30m"
        },
        "output": "Found 156 connection pool errors",
        "thought": "大量连接池错误，需要排查下游依赖"
      },
      {
        "step": 2,
        "timestamp": "2024-03-14T14:30:08Z",
        "action": "query_metrics",
        "tool": "prometheus_query",
        "input": {
          "metric": "node_disk_io_util",
          "instance": "10.0.1.15"
        },
        "output": "98% utilization for 15 minutes",
        "thought": "磁盘IO利用率过高，疑似瓶颈"
      },
      {
        "step": 3,
        "timestamp": "2024-03-14T14:30:12Z",
        "action": "get_topology",
        "tool": "cmdb_topology",
        "input": {
          "service": "payment-service",
          "depth": 2
        },
        "output": "payment-service -> payment-db (master)",
        "thought": "确认数据库是瓶颈点"
      },
      {
        "step": 4,
        "timestamp": "2024-03-14T14:30:15Z",
        "action": "llm_reasoning",
        "thought": "磁盘IO瓶颈导致数据库写入慢，进而引发上游连接池耗尽。建议检查SSD健康度。",
        "conclusion": true
      }
    ],
    "recommendations": [
      {
        "id": "rec-001",
        "type": "mitigation",
        "title": "Switch to Read Replica",
        "description": "Switch read traffic to replica to reduce master load",
        "auto_safe": true,
        "command": "cmdb:switch_read_replica(service=payment-service)",
        "estimated_impact": "Reduces master load by 60%"
      },
      {
        "id": "rec-002",
        "type": "fix",
        "title": "Replace SSD Disk",
        "description": "Contact vendor to replace degraded SSD",
        "auto_safe": false,
        "need_approval": true,
        "approval_level": "L3"
      }
    ],
    "similar_cases": [
      {
        "case_id": "case-20240215-003",
        "similarity": 0.92,
        "resolution": "Replaced SSD and restored service"
      }
    ],
    "cost": {
      "tokens_input": 2048,
      "tokens_output": 512,
      "cost_usd": 0.015
    },
    "latency_ms": 12500,
    "created_at": "2024-03-14T14:30:00Z",
    "completed_at": "2024-03-14T14:30:15Z"
  }
}

# Response (流式-SSE)
# GET /api/v1/ai/analyze/stream?incident_id=inc-20240314-001

# Stream Events:
event: start
data: {"step": 0, "status": "started", "timestamp": "2024-03-14T14:30:00Z"}

event: reasoning
data: {"step": 1, "thought": "分析告警：数据库连接池耗尽", "timestamp": "2024-03-14T14:30:01Z"}

event: tool_call
data: {
  "step": 1,
  "action": "search_logs",
  "status": "calling",
  "timestamp": "2024-03-14T14:30:02Z"
}

event: tool_result
data: {
  "step": 1,
  "action": "search_logs",
  "status": "completed",
  "result_summary": "找到156条连接池错误",
  "timestamp": "2024-03-14T14:30:05Z"
}

# ... 更多步骤 ...

event: conclusion
data: {
  "step": 4,
  "confidence": 0.87,
  "root_cause": "Disk I/O bottleneck on payment-db master",
  "recommendations": [...]
}

event: complete
data: {"status": "completed", "final_cost_usd": 0.015}
```

#### 查询诊断结果
```http
GET /api/v1/ai/analysis/{analysis_id}
Authorization: Bearer {jwt_token}

# Response: 同上
```

### 2.3 大屏数据接口

#### 获取大屏摘要数据
```http
GET /api/v1/dashboard/summary?tenant_id=tenant_001
Authorization: Bearer {jwt_token}

# Response
{
  "code": 200,
  "message": "success",
  "data": {
    "health_score": 87,              # 系统健康度 0-100
    "alert_stats": {
      "today_total": 12,
      "today_critical": 2,
      "week_trend": [15, 18, 12, 9, 14, 11, 12],
      "yoy_change": -0.40            # 同比-40%
    },
    "mttr": {
      "current_month_avg": 15,        # 分钟
      "target": 30,
      "trend": [18, 16, 15]
    },
    "ai_accuracy": {
      "current": 0.92,
      "trend": [0.88, 0.90, 0.92],
      "human_confirmed_rate": 0.85
    },
    "cost": {
      "this_month": 1500.50,
      "last_month": 1350.30,
      "change_pct": 0.12,
      "ai_inference_cost": 450.20
    },
    "active_incidents": [
      {
        "incident_id": "inc-20240314-001",
        "status": "analyzing",
        "severity": "critical",
        "service": "payment-service",
        "ai_progress": 0.75
      }
    ],
    "topology_health": {
      "nodes": 156,
      "healthy": 152,
      "warning": 3,
      "critical": 1
    }
  }
}
```

#### 获取拓扑图数据
```http
GET /api/v1/dashboard/topology?service=payment-service&depth=2
Authorization: Bearer {jwt_token}

# Response
{
  "code": 200,
  "message": "success",
  "data": {
    "nodes": [
      {
        "id": "svc-payment",
        "name": "payment-service",
        "type": "service",
        "status": "warning",           # healthy/warning/critical
        "metrics": {
          "latency_p99": 5200,
          "error_rate": 0.15,
          "qps": 1250
        },
        "alerts": 3
      },
      {
        "id": "db-payment-master",
        "name": "payment-db-master",
        "type": "database",
        "status": "critical",
        "metrics": {
          "disk_io_util": 0.98,
          "connections": 100,
          "connections_max": 100
        },
        "alerts": 1
      }
    ],
    "edges": [
      {
        "source": "svc-payment",
        "target": "db-payment-master",
        "type": "database",
        "traffic": {
          "rps": 850,
          "latency_avg": 450,
          "error_rate": 0.15
        },
        "status": "critical"
      }
    ]
  }
}
```

#### 获取实时AI诊断流
```http
GET /api/v1/dashboard/ai-stream
Accept: text/event-stream
Authorization: Bearer {jwt_token}

# SSE Stream:
event: heartbeat
data: {"timestamp": "2024-03-14T14:30:00Z"}

event: new_incident
data: {
  "incident_id": "inc-20240314-002",
  "service": "order-service",
  "severity": "warning",
  "ai_status": "started"
}

event: reasoning_progress
data: {
  "incident_id": "inc-20240314-001",
  "step": 2,
  "thought": "检测到磁盘IO利用率98%",
  "partial_conclusion": "疑似磁盘瓶颈"
}

event: incident_complete
data: {
  "incident_id": "inc-20240314-001",
  "conclusion": {
    "root_cause": "Disk I/O bottleneck",
    "confidence": 0.87
  }
}
```

### 2.4 CMDB接口

#### 查询服务拓扑
```http
GET /api/v1/cmdb/topology/{service_id}?depth=2
Authorization: Bearer {jwt_token}

# Response
{
  "code": 200,
  "message": "success",
  "data": {
    "service": {
      "id": "payment-service",
      "name": "支付服务",
      "owner": "team-payment",
      "tier": "critical"
    },
    "upstream": [],                   # 调用本服务的
    "downstream": [
      {
        "id": "payment-db-master",
        "type": "database",
        "relation": "depends_on",
        "protocol": "mysql"
      },
      {
        "id": "redis-payment",
        "type": "cache",
        "relation": "depends_on",
        "protocol": "redis"
      }
    ],
    "dependencies": [
      {
        "id": "order-service",
        "type": "service",
        "relation": "called_by"
      }
    ]
  }
}
```

#### 查询实例信息
```http
GET /api/v1/cmdb/instances?ip=10.0.1.15
Authorization: Bearer {jwt_token}

# Response
{
  "code": 200,
  "message": "success",
  "data": {
    "instances": [
      {
        "id": "ins-db-001",
        "ip": "10.0.1.15",
        "hostname": "payment-db-master-01",
        "type": "database",
        "service": "payment-db",
        "role": "master",
        "zone": "zone-a",
        "rack": "rack-03",
        "spec": {
          "cpu": 32,
          "memory": "128Gi",
          "disk": "2TB SSD"
        },
        "owner": "dba-team",
        "created_at": "2023-06-01"
      }
    ]
  }
}
```

### 2.5 成本治理接口

#### 获取AI推理成本
```http
GET /api/v1/cost/inference?tenant_id=tenant_001&start=2024-03-01&end=2024-03-14
Authorization: Bearer {jwt_token}

# Response
{
  "code": 200,
  "message": "success",
  "data": {
    "summary": {
      "total_calls": 15234,
      "total_tokens": 4256780,
      "total_cost_usd": 156.50,
      "avg_cost_per_call": 0.0103,
      "avg_latency_ms": 8200
    },
    "by_date": [
      {
        "date": "2024-03-14",
        "calls": 1250,
        "tokens": 356780,
        "cost_usd": 12.30
      }
    ],
    "by_model": {
      "glm5-32b": {
        "calls": 12000,
        "cost_usd": 120.00
      },
      "glm5-9b": {
        "calls": 3234,
        "cost_usd": 36.50
      }
    }
  }
}
```

---

## 3. 通用规范

### 3.1 响应码定义

| Code | 含义 | 说明 |
|------|------|------|
| 200 | 成功 | 请求处理成功 |
| 400 | 参数错误 | 请求参数不符合要求 |
| 401 | 未授权 | JWT Token无效或过期 |
| 403 | 禁止访问 | 无权限访问该资源 |
| 404 | 资源不存在 | 请求的资源不存在 |
| 429 | 限流 | 请求频率超限 |
| 500 | 服务器错误 | 内部服务错误 |

### 3.2 错误响应格式

```json
{
  "code": 400,
  "message": "Invalid parameter: tenant_id is required",
  "error": {
    "type": "validation_error",
    "field": "tenant_id",
    "detail": "租户ID不能为空"
  },
  "trace_id": "trace-20240314-abc123"
}
```

### 3.3 分页参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 |
| size | int | 20 | 每页条数，最大100 |

### 3.4 时间格式

- 统一使用 ISO 8601: `2024-03-14T14:30:00Z`
- 时区：UTC

---

*本文档定义了AIOps平台的API规范，前后端开发应严格遵循。*
