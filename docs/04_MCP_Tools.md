# AIOps 智能运维平台 - MCP Tools 设计文档

## 1. MCP (Model Context Protocol) 概述

MCP 是 AI 模型与外部工具交互的标准协议，类似于 OpenAI 的 Function Calling。

### 核心组件

```
┌─────────────────────────────────────────────────────────┐
│                    AI Agent                            │
├─────────────────────────────────────────────────────────┤
│  User Input → LLM → Function Call → Tool → Result      │
│                          ↓                              │
│                    MCP Protocol                        │
│                   (standard JSON)                      │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Tools 清单

| Tool | 描述 | 输入 | 输出 | 安全等级 |
|------|------|------|------|---------|
| `search_logs` | Doris日志检索 | SQL/条件 | 日志列表 | 只读 |
| `query_metrics` | Prometheus指标查询 | PromQL | 时序数据 | 只读 |
| `get_trace` | 链路追踪查询 | trace_id | 调用链 | 只读 |
| `get_topology` | CMDB拓扑查询 | service_id | 依赖图 | 只读 |
| `get_service_details` | 服务详情 | service_id | 元信息 | 只读 |
| `get_current_incidents` | 当前故障列表 | 过滤条件 | 故障列表 | 只读 |
| `search_historical_cases` | 历史Case检索 | semantic query | 相似Case | 只读 |
| `get_runbook` | 获取运维手册 | symptom | Runbook | 只读 |
| `format_time_range` | 时间格式化 | 自然语言 | ISO时间 | 无状态 |

---

## 3. Tool 详细定义

### 3.1 search_logs

```typescript
interface SearchLogsTool {
  name: "search_logs";
  description: "Search logs from Apache Doris with filters. Use this when you need to find error logs, exceptions, or specific events.";
  parameters: {
    service_id?: string;           // 服务ID，null表示全部
    level?: "ERROR" | "WARN" | "INFO" | "DEBUG";
    time_range: {
      start: string;               // ISO 8601
      end: string;
    };
    keyword?: string;              // 关键词匹配
    trace_id?: string;             // 关联trace
    limit: number;                 // 默认100，最大1000
    offset?: number;               // 分页偏移
  };
  returns: {
    total: number;
    logs: Array<{
      timestamp: string;
      service_id: string;
      level: string;
      message: string;
      trace_id?: string;
      host_ip?: string;
      parsed_fields?: object;
    }>;
    summary: {
      error_count: number;
      warn_count: number;
      top_errors: Array<{message: string; count: number}>;
    }
  };
}

// 示例调用
{
  "name": "search_logs",
  "arguments": {
    "service_id": "payment-service",
    "level": "ERROR",
    "time_range": {
      "start": "2024-03-14T14:00:00Z",
      "end": "2024-03-14T14:30:00Z"
    },
    "keyword": "connection pool",
    "limit": 100
  }
}

// 示例返回
{
  "total": 156,
  "logs": [
    {
      "timestamp": "2024-03-14T14:15:32Z",
      "service_id": "payment-service",
      "level": "ERROR",
      "message": "java.sql.SQLException: Connection pool is full",
      "trace_id": "abc123def456",
      "host_ip": "10.0.1.15"
    }
  ],
  "summary": {
    "error_count": 156,
    "warn_count": 23,
    "top_errors": [
      {"message": "Connection pool is full", "count": 142},
      {"message": "Connection timeout", "count": 14}
    ]
  }
}
```

### 3.2 query_metrics

```typescript
interface QueryMetricsTool {
  name: "query_metrics";
  description: "Query metrics from Prometheus. Use this to check CPU, memory, disk, QPS, latency, error rates, etc.";
  parameters: {
    query: string;                 // PromQL表达式
    time_range?: {
      start: string;
      end: string;
    };
    step?: string;                 // 采样间隔, e.g., "1m"
    instant?: boolean;             // 瞬时查询
    timeout?: number;              // 超时秒数
  };
  returns: {
    result_type: "matrix" | "vector" | "scalar";
    data: Array<{
      metric: object;              // 标签
      values: Array<[number, string]>;  // [timestamp, value]
      value?: [number, string];    // 瞬时查询
    }>;
    stats: {
      current?: number;
      max?: number;
      min?: number;
      avg?: number;
      trend: "up" | "down" | "stable";
    }
  };
}

// 示例调用
{
  "name": "query_metrics",
  "arguments": {
    "query": "node_disk_io_util{job='node-exporter', instance='10.0.1.15:9100'}",
    "time_range": {
      "start": "2024-03-14T14:00:00Z",
      "end": "2024-03-14T14:30:00Z"
    },
    "step": "1m"
  }
}

// 示例返回
{
  "result_type": "matrix",
  "data": [
    {
      "metric": {
        "instance": "10.0.1.15:9100",
        "device": "nvme0n1"
      },
      "values": [
        [1710432000, "85.5"],
        [1710432060, "89.2"],
        [1710432120, "92.1"],
        [1710432180, "95.8"],
        [1710432240, "98.5"]
      ]
    }
  ],
  "stats": {
    "current": 98.5,
    "max": 98.5,
    "min": 85.5,
    "avg": 92.3,
    "trend": "up"
  }
}
```

### 3.3 get_topology

```typescript
interface GetTopologyTool {
  name: "get_topology";
  description: "Get service dependency topology from CMDB. Use this to understand upstream and downstream relationships.";
  parameters: {
    service_id: string;            // 服务ID
    depth: number;                 // 遍历深度 1-3
    direction?: "downstream" | "upstream" | "both";  // 方向
    include_metrics?: boolean;     // 是否包含实时指标
  };
  returns: {
    center: {
      id: string;
      name: string;
      status: "healthy" | "warning" | "critical";
      metrics?: object;
    };
    downstream: Array<{
      id: string;
      name: string;
      type: "service" | "database" | "cache" | "queue";
      relation: "calls" | "depends_on";
      protocol?: string;
      status: string;
      metrics?: object;
      alerts?: number;
    }>;
    upstream: Array<{
      id: string;
      name: string;
      type: "service";
      status: string;
    }>;
    impact_path?: Array<string>;   // 影响传递路径
  };
}

// 示例调用
{
  "name": "get_topology",
  "arguments": {
    "service_id": "payment-service",
    "depth": 2,
    "direction": "both",
    "include_metrics": true
  }
}

// 示例返回
{
  "center": {
    "id": "payment-service",
    "name": "支付服务",
    "status": "warning",
    "metrics": {
      "qps": 1250,
      "latency_p99": 5200,
      "error_rate": 0.15
    }
  },
  "downstream": [
    {
      "id": "payment-db-master",
      "name": "支付数据库主库",
      "type": "database",
      "relation": "depends_on",
      "protocol": "mysql",
      "status": "critical",
      "metrics": {
        "connections": 100,
        "connections_max": 100,
        "disk_io_util": 98.5,
        "qps": 3500
      },
      "alerts": 1
    },
    {
      "id": "redis-payment",
      "name": "支付缓存",
      "type": "cache",
      "relation": "depends_on",
      "protocol": "redis",
      "status": "healthy",
      "alerts": 0
    }
  ],
  "upstream": [
    {
      "id": "order-service",
      "name": "订单服务",
      "type": "service",
      "status": "healthy"
    }
  ],
  "impact_path": [
    "payment-db-master → payment-service → order-service"
  ]
}
```

### 3.4 search_historical_cases

```typescript
interface SearchHistoricalCasesTool {
  name: "search_historical_cases";
  description: "Search similar historical incidents using semantic search. Use this to find previous solutions for similar problems.";
  parameters: {
    query: string;                 // 自然语言描述
    service_id?: string;           // 可选：限定服务
    time_range?: {
      start: string;
      end: string;
    };
    limit: number;                 // 返回数量，默认5
    similarity_threshold: number;  // 相似度阈值 0-1，默认0.8
  };
  returns: {
    cases: Array<{
      case_id: string;
      similarity: number;          // 相似度分数
      occurred_at: string;
      root_cause: string;
      resolution: string;
      service_id: string;
      alert_summary: string;
      feedback_score: number;      // 1-5评分
    }>;
    suggestions: Array<string>;    // AI生成的建议
  };
}

// 示例调用
{
  "name": "search_historical_cases",
  "arguments": {
    "query": "数据库连接池耗尽 磁盘IO高",
    "service_id": "payment-service",
    "limit": 5,
    "similarity_threshold": 0.85
  }
}

// 示例返回
{
  "cases": [
    {
      "case_id": "case-20240215-003",
      "similarity": 0.92,
      "occurred_at": "2024-02-15T09:30:00Z",
      "root_cause": "SSD写入放大导致IO延迟飙升",
      "resolution": "联系云厂商更换SSD实例，并开启写入缓存",
      "service_id": "payment-service",
      "alert_summary": "支付服务响应超时，数据库连接池耗尽",
      "feedback_score": 5
    },
    {
      "case_id": "case-20240120-007",
      "similarity": 0.88,
      "occurred_at": "2024-01-20T14:20:00Z",
      "root_cause": "慢查询导致MySQL锁等待",
      "resolution": "kill慢查询进程，优化SQL索引",
      "service_id": "order-service",
      "alert_summary": "数据库CPU高，大量锁等待",
      "feedback_score": 4
    }
  ],
  "suggestions": [
    "参考case-20240215-003: 检查SSD健康度",
    "参考case-20240120-007: 排查是否存在慢查询"
  ]
}
```

### 3.5 format_time_range

```typescript
interface FormatTimeRangeTool {
  name: "format_time_range";
  description: "Convert natural language time expressions to ISO timestamps. Use for 'last 30 minutes', 'since 2 hours ago', etc.";
  parameters: {
    expression: string;            // 自然语言表达
    timezone?: string;             // 默认 Asia/Shanghai
  };
  returns: {
    start: string;                 // ISO 8601
    end: string;
    human_readable: string;
  };
}

// 示例调用
{
  "name": "format_time_range",
  "arguments": {
    "expression": "最近30分钟",
    "timezone": "Asia/Shanghai"
  }
}

// 示例返回
{
  "start": "2024-03-14T14:00:00+08:00",
  "end": "2024-03-14T14:30:00+08:00",
  "human_readable": "2024-03-14 14:00:00 至 2024-03-14 14:30:00"
}
```

---

## 4. Tool Registry 实现

```python
# ai_engine/mcp/registry.py
from typing import Dict, Callable, Any
from dataclasses import dataclass
from functools import wraps

@dataclass
class Tool:
    name: str
    description: str
    parameters: Dict[str, Any]
    returns: Dict[str, Any]
    handler: Callable

class ToolRegistry:
    def __init__(self):
        self.tools: Dict[str, Tool] = {}

    def register(self, name: str, description: str, parameters: Dict, returns: Dict):
        """Decorator to register a tool"""
        def decorator(func: Callable):
            self.tools[name] = Tool(
                name=name,
                description=description,
                parameters=parameters,
                returns=returns,
                handler=func
            )
            @wraps(func)
            async def wrapper(*args, **kwargs):
                return await func(*args, **kwargs)
            return wrapper
        return decorator

    def get_tool(self, name: str) -> Tool:
        return self.tools.get(name)

    def list_tools(self) -> list:
        return [
            {
                "name": tool.name,
                "description": tool.description,
                "parameters": tool.parameters
            }
            for tool in self.tools.values()
        ]

    async def execute(self, name: str, arguments: Dict) -> Any:
        tool = self.get_tool(name)
        if not tool:
            raise ValueError(f"Unknown tool: {name}")
        return await tool.handler(**arguments)

# 全局注册器
registry = ToolRegistry()
```

---

## 5. Tools 实现示例

```python
# ai_engine/mcp/tools/search_logs.py
from ..registry import registry
from ai_engine.db.doris_client import DorisClient

@registry.register(
    name="search_logs",
    description="Search logs from Apache Doris with filters",
    parameters={
        "type": "object",
        "properties": {
            "service_id": {"type": "string", "description": "Service ID"},
            "level": {"enum": ["ERROR", "WARN", "INFO", "DEBUG"]},
            "time_range": {
                "type": "object",
                "properties": {
                    "start": {"type": "string"},
                    "end": {"type": "string"}
                },
                "required": ["start", "end"]
            },
            "keyword": {"type": "string"},
            "limit": {"type": "integer", "default": 100}
        },
        "required": ["time_range"]
    },
    returns={
        "total": {"type": "integer"},
        "logs": {"type": "array"},
        "summary": {"type": "object"}
    }
)
async def search_logs(
    service_id: str = None,
    level: str = None,
    time_range: dict = None,
    keyword: str = None,
    limit: int = 100,
    doris_client: DorisClient = None
) -> dict:
    """
    Doris日志检索工具 - 安全版本

    安全增强:
    - 参数化查询防止SQL注入
    - 输入长度限制
    - SQL关键词黑名单过滤
    - 返回条数硬上限
    """

    # 参数校验
    _validate_search_params(service_id, level, keyword, limit)

    # 使用参数化查询
    params = []
    where_conditions = ["timestamp >= ? AND timestamp <= ?"]
    params.extend([time_range['start'], time_range['end']])

    if service_id:
        where_conditions.append("service_id = ?")
        params.append(service_id)
    if level:
        # 严格白名单校验
        allowed_levels = ['ERROR', 'WARN', 'INFO', 'DEBUG']
        if level not in allowed_levels:
            raise ValueError(f"Invalid level: {level}")
        where_conditions.append("level = ?")
        params.append(level)
    if keyword:
        # 关键词清洗
        safe_keyword = _sanitize_keyword(keyword)
        where_conditions.append("message MATCH ?")
        params.append(safe_keyword)

    where_clause = " AND ".join(where_conditions)

    # 构造参数化SQL
    logs_sql = f"""
    SELECT timestamp, service_id, level, message, trace_id, host_ip, parsed_fields
    FROM logs
    WHERE {where_clause}
    ORDER BY timestamp DESC
    LIMIT ?
    """
    params.append(min(limit, 1000))  # 硬上限保护

    logs_result = await doris_client.execute(logs_sql, params)

    # 统计摘要
    summary_sql = f"""
    SELECT
      level,
      COUNT(*) as count,
      message,
      ROW_NUMBER() OVER (PARTITION BY level ORDER BY COUNT(*) DESC) as rn
    FROM logs
    WHERE {where_clause}
    GROUP BY level, message
    """
    summary_result = await doris_client.execute(summary_sql, params[:-1])

    # 聚合统计
    error_count = sum(r['count'] for r in summary_result if r['level'] == 'ERROR')
    warn_count = sum(r['count'] for r in summary_result if r['level'] == 'WARN')

    top_errors = [
        {"message": r['message'][:200], "count": r['count']}
        for r in summary_result
        if r['level'] == 'ERROR' and r['rn'] <= 5
    ]

    return {
        "total": len(logs_result),
        "logs": logs_result[:50],  # 限制返回数量
        "summary": {
            "error_count": error_count,
            "warn_count": warn_count,
            "top_errors": top_errors
        }
    }


def _validate_search_params(service_id: str, level: str, keyword: str, limit: int):
    """参数校验 - 拒绝可疑输入"""
    import re

    if service_id and len(service_id) > 128:
        raise ValueError("service_id too long (max 128)")
    if keyword and len(keyword) > 512:
        raise ValueError("keyword too long (max 512)")
    if not 1 <= limit <= 1000:
        raise ValueError("limit must be between 1 and 1000")

    # 拒绝SQL注入特征
    sql_injection_patterns = [
        r"(\b(union|select|insert|update|delete|drop|create|alter|exec|execute)\b)",
        r"(--|#|/\*)",
        r"'\s*(or|and)\s*'",
        r";\s*$",
    ]

    for field, value in [('service_id', service_id), ('keyword', keyword)]:
        if value:
            for pattern in sql_injection_patterns:
                if re.search(pattern, value, re.IGNORECASE):
                    raise ValueError(f"Invalid characters in {field}: potential SQL injection")


def _sanitize_keyword(keyword: str) -> str:
    """清洗关键词 - 移除SQL特殊字符"""
    import re
    # 移除SQL特殊字符
    sanitized = re.sub(r'[";\']', '', keyword)
    # 限制长度
    return sanitized[:256]
```

---

## 6. Tools 调用链示例

```
Agent思考:
"用户报告支付服务超时，我需要：
1. 先查支付服务的ERROR日志
2. 再查它的下游依赖状态
3. 看看有没有类似历史Case"

→ Tool Call #1: search_logs
  {
    "service_id": "payment-service",
    "level": "ERROR",
    "time_range": {"start": "-30min", "end": "now"}
  }
← Result: 发现大量"connection pool exhausted"

→ Tool Call #2: get_topology
  {
    "service_id": "payment-service",
    "depth": 2,
    "include_metrics": true
  }
← Result: payment-db-master磁盘IO 98%

→ Tool Call #3: query_metrics
  {
    "query": "node_disk_io_util{instance='10.0.1.15'}"
  }
← Result: IO趋势持续上升

→ Tool Call #4: search_historical_cases
  {
    "query": "数据库连接池耗尽 磁盘IO高"
  }
← Result: 找到相似Case，根因是SSD故障

Agent结论:
"根因：payment-db-master磁盘IO瓶颈(98%)，导致数据库响应慢，
上游连接池耗尽。建议：1)切换只读副本 2)更换SSD"
```

---

*本文档定义了AIOps平台的MCP Tools规范，AI Agent开发应以此为准。*
