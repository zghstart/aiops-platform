# AIOps 智能运维平台 - 企业级架构设计文档

## 1. 文档信息

| 项目 | 内容 |
|------|------|
| 文档版本 | v1.0 |
| 创建日期 | 2026-03-14 |
| 作者 | Claude Code + Deep Research |
| 状态 | 正式发布 |

---

## 2. 项目背景与目标

### 2.1 业务目标

构建一个针对异构环境（裸机、Kubernetes、网络设备）的智能诊断平台，实现告警到根因分析（RCA）的自动化闭环，并为大屏展示和领导汇报提供数据支撑。

### 2.2 核心痛点

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     企业客户AI运维侧核心痛点                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  1️⃣ 数据孤岛之痛          2️⃣ 误报疲劳症           3️⃣ 专家经验流失        │
│  ├─ 分散的监控工具        ├─ 日告警量>1000+       ├─ 资深SRE离职         │
│  ├─ 日志/指标/链路割裂    ├─ 90%是关联重复告警    ├─ 排查经验无沉淀       │
│  └─ 故障时数据难关联      └─ 真正故障淹没在噪音中  └─ 新人成长周期长       │
│                                                                         │
│  4️⃣ 大屏虚有其表          5️⃣ 人机协作断层          6️⃣ 合规审计难         │
│  ├─ 酷炫但不实用          ├─ AI黑盒不可解释        ├─ 故障操作无留痕      │
│  ├─ 决策信息密度低        ├─ 建议无法一键执行      ├─ 敏感数据泄露风险    │
│  └─ 领导看不到价值        └─ 人工和执行脱节        └─ 跨国数据合规        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 总体架构

### 3.1 架构全景图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           🎛️ 统一门户层 (Unified Portal)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ 运维工作台   │  │ 领导驾驶舱   │  │  知识中心    │  │  管理后台 (多租户/权限)  │  │
│  │ (日常操作)   │  │ (大屏汇报)   │  │ (Case库)   │  │                        │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │ HTTPS/WebSocket + SSO + RBAC
┌────────────────────────────────┴────────────────────────────────────────────────┐
│                           🔀 API网关层 (Kong/APISIX)                            │
│  • 限流熔断  • 租户路由  • 审计日志  • API版本管理  • 认证鉴权                   │
└────────────────────────────────┬────────────────────────────────────────────────┘
                                 │
    ┌────────────────────────────┼────────────────────────────┐
    │                            │                            │
┌───┴────────┐           ┌───────┴────────┐          ┌─────────┴────────┐
│ Java控制面  │◄────────►│  Python AI推理  │          │   时序分析引擎    │
│(SpringBoot)│ HTTP/gRPC │   (FastAPI)    │          │  (Flink/Spark)   │
│            │           │                │          │                  │
├────────────┤           ├────────────────┤          ├──────────────────┤
│• 告警中心  │           │• Agent编排器   │          │• 异常检测模型    │
│• 拓扑服务  │           │• GLM5推理服务  │          │• 趋势预测       │
│• 降噪引擎  │           │• MCP Tools    │          │• 容量预测       │
│• 工作流引擎 │           │• 向量检索RAG  │          │                 │
│• 成本分析  │           │• 推理缓存     │          │                 │
└─────┬──────┘           └────────┬───────┘          └─────────────────┘
      │                           │
      │    ┌──────────────────────┴──────────────────────┐
      │    │           🔧 MCP Skills 工具箱               │
      │    │  ┌────────────┐ ┌────────────┐ ┌───────────┐ │
      │    │  │ 日志检索   │ │ 指标查询   │ │ 链路追踪  │ │
      │    │  │ (Doris SQL)│ │ (PromQL)   │ │ (Jaeger)  │ │
      │    │  └────────────┘ └────────────┘ └───────────┘ │
      │    │  ┌────────────┐ ┌────────────┐ ┌───────────┐ │
      │    │  │ CMDB查询   │ │ Runbook    │ │ 历史 Cases│ │
      │    │  │ (影响面)   │ │ 执行器     │ │ (RAG检索) │ │
      │    │  └────────────┘ └────────────┘ └───────────┘ │
      │    └────────────────────────────────────────────────┘
      │                  ▲
      │                  │
      ▼                  │
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              📊 数据层                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ Apache Doris │  │ Prometheus   │  │ Redis Cluster│  │ 向量数据库          │ │
│  │ (日志/链路)   │  │ + VM集群     │  │ (推理缓存)   │  │ (Milvus/PGVector)  │ │
│  │ Routine Load │  │ 长期存储     │  │ 相似告警去重  │  │ (RAG知识库)        │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
      │                  ▲
      ▼                  │
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           🚀 消息队列层 (Kafka)                                 │
│  • logs-raw (12分区)  • alerts-filtered (6分区)  • ai-tasks (优先级队列)        │
│  • metrics-json (6分区) • incidents (3分区)  • cost-events (3分区)              │
└─────────────────────────────────────────────────────────────────────────────────┘
      │                      │
┌─────┴──────────────────────┴────────────────────────────────────────────────────┐
│                              🔍 采集层                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ iLogtail     │  │ Metricbeat   │  │ OpenTelemetry│  │ CMDB 变更监听        │ │
│  │ (日志采集)    │  │ (指标采集)   │  │ (链路追踪)   │  │ (配置变更事件)       │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. 核心模块详解

### 4.1 采集层设计

**技术选型：iLogtail (LoongCollector)**

| 功能 | 实现方式 |
|------|----------|
| 应用日志 | 容器标准输出/文件采集，支持正则解析 |
| 系统指标 | 集成Metricbeat，Node Exporter |
| 网络设备 | SNMP协议采集，支持Trap接收 |
| eBPF网络 | Cilium Hubble，无侵入式网络观测 |
| CMDB变更 | Webhook接收配置变更事件 |

**预处理策略**：
```yaml
# iLogtail processor配置
processors:
  - type: extract_log_fields
    regex: '(?P<level>ERROR|WARN|INFO).*(?P<service_id>[\w-]+).*(?P<trace_id>[0-9a-f]{32})'
  - type: desensitize    # PII脱敏
    fields: [phone, id_card, bank_card]
    rules:
      phone: '(\d{3})\d{4}(\d{4})'
      replacement: '$1****$2'
  - type: add_meta
    fields:
      tenant_id: '${_tenant_}'
      host_ip: '${_host_ip_}'
```

### 4.2 存储层设计

| 存储类型 | 选择 | 理由 |
|----------|------|------|
| 日志/链路 | Apache Doris 2.1+ | 国产开源、倒排索引、Routine Load |
| 指标 | Prometheus + VictoriaMetrics | 云原生标准、长期存储 |
| 缓存 | Redis Cluster | 推理缓存、告警去重 |
| 向量 | Milvus | RAG知识库、相似告警检索 |

**数据生命周期管理**：
```sql
-- Doris TTL策略
CREATE TABLE logs (
    timestamp DATETIME,
    tenant_id VARCHAR(32),
    raw_message TEXT,
    INDEX idx_message (raw_message) USING INVERTED
) DUPLICATE KEY(timestamp)
PARTITION BY RANGE(timestamp) (
    PARTITION p_current VALUES LESS THAN ("2026-04-01"),
    PARTITION p_next VALUES LESS THAN ("2026-05-01")
)
PROPERTIES (
    "dynamic_partition.enable" = "true",
    "dynamic_partition.create_history_partition" = "true",
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "dynamic_partition.buckets" = "16",
    "expire.partition.num" = "7"  -- 保留7天
);
```

### 4.3 双栈服务层设计

#### 4.3.1 Java 控制面

```
aiops-control-plane/
├── alert-center/          # 告警接收与处理
│   ├── AlertReceiver.java    # Webhook接收
│   ├── NoiseReducer.java     # 告警降噪
│   └── RootFinder.java       # 根因定位
├── topology-service/      # 拓扑与CMDB
│   ├── CMDBClient.java       # CMDB查询
│   ├── TopologyGraph.java    # 拓扑构建
│   └── ImpactAnalyzer.java   # 影响面分析
├── workflow-engine/       # 工作流编排
│   ├── TaskDispatcher.java   # AI任务派发
│   └── CallbackHandler.java  # 结果回传
├── cost-manager/          # 成本治理
│   ├── InferenceCost.java    # AI推理成本
│   └── ResourceUtil.java     # 资源利用率
└── gateway/               # API网关集成
```

#### 4.3.2 Python AI推理面

```
aiops-ai-engine/
├── agent/
│   ├── orchestrator.py       # ReAct编排器
│   ├── context.py            # 上下文管理
│   └── memory.py             # 对话记忆
├── llm/
│   ├── glm5_client.py        # GLM5推理客户端
│   ├── streaming.py          # SSE流式输出
│   └── cache.py              # 推理结果缓存
├── mcp/
│   ├── tools/                # MCP工具箱
│   │   ├── search_logs.py    # 日志检索
│   │   ├── query_metrics.py  # 指标查询
│   │   ├── get_topology.py   # 拓扑查询
│   │   └── runbook_exec.py   # Runbook执行
│   └── registry.py           # 工具注册
├── rag/
│   ├── indexer.py            # 知识库索引
│   ├── retriever.py          # 向量检索
│   └── cases_db.py           # 历史Case库
└── api/
    └── main.py               # FastAPI入口
```

### 4.4 降噪引擎设计

```
原始告警 (1000条/天)
    │
    ▼
┌──────────────┐
│ 时间窗口聚类  │  ← 5分钟内同类告警归为1组
│ (相似度95%+) │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 根源告警识别  │  ← AI判断根因，其他为扩散
│ (Root Cause) │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 抑制策略检查  │  ← 维护窗口、依赖抑制
│ (Suppression)│
└──────┬───────┘
       │
       ▼
  降噪后告警 (30条/天)
```

### 4.5 多租户隔离设计

```yaml
# tenant_isolation.yml
tenant_isolation:
  data:
    doris: "PARTITION BY tenant_id"
    prometheus: "metric标签 tenant={tenant_id}"
    redis: "Key前缀 tenant:{id}:cache:*"

  ai_resources:
    shared_pool:        # 共享池
      max_tokens_per_minute: 100000
      fair_scheduling: true
    dedicated_pool:     # 专属池
      enabled_for: ["tenant_big_customer"]
      gpu_replicas: 2

  alert_rules:
    per_tenant: true
    global: ["infrastructure_health"]

  # 新增: 强制隔离层 - 防止SQL注入绕过tenant_id
  enforcement_layer:
    doris_proxy:
      enabled: true
      mode: "rewrite"  # rewrite/reject/allow
      rules:
        # 强制每个查询必须包含 tenant_id
        - name: "mandatory_tenant_filter"
          sql_pattern: "SELECT|INSERT|UPDATE|DELETE"
          required_filter: "tenant_id"
          enforce_on: ["logs", "alerts", "traces", "ai_analysis"]
          exception_sql:
            - "information_schema"
            - "aiops.audit_logs"  # 审计表允许管理员跨租户查询

        # 禁止跨租户查询
        - name: "forbid_cross_tenant"
          forbidden_patterns:
            - "SELECT.*FROM logs WHERE tenant_id IN"
            - "SELECT.*FROM logs WHERE tenant_id !="
            - "SELECT.*FROM logs WHERE tenant_id =.*OR tenant_id ="

        # 参数化查询强制
        - name: "force_parameterized"
          reject_raw_params: true  # 拒绝原始字符串拼接

def validate_sql_enforcement(sql: str, user_tenant_id: str) -> Tuple[bool, str]:
    """
    强制SQL验证 - 防止租户隔离绕过

    返回: (是否允许, 错误信息)
    """
    import re

    # 1. 检查是否有tenant_id过滤
    tenant_filter_pattern = r"tenant_id\s*=\s*['\"]?(\w+)['\"]?"
    matches = re.findall(tenant_filter_pattern, sql, re.IGNORECASE)

    if not matches:
        return False, "SQL缺少tenant_id过滤条件"

    # 2. 租户的tenant_id必须与用户租户一致
    for matched_tenant in matches:
        if matched_tenant != user_tenant_id:
            return False, f"SQL包含非法tenant_id: {matched_tenant}"

    # 3. 检查禁止的模式
    forbidden_patterns = [
        r"tenant_id\s*=\s*['\"]?\w+['\"]?\s*OR\s*tenant_id\s*=",  # OR条件
        r"tenant_id\s+IN\s*\(",  # IN子句
        r"tenant_id\s*!=",  # 不等于
        r"tenant_id\s*<>",  # 不等于
    ]

    for pattern in forbidden_patterns:
        if re.search(pattern, sql, re.IGNORECASE):
            return False, f"SQL包含禁止的tenant_id查询模式"

    # 4. 检查注入特征
    injection_patterns = [
        r"';.*--",  # 注释注入
        r"1\s*=\s*1",  # 恒真
        r"union\s+select",  # union注入
        r"sleep\s*\(",  # 时间盲注
    ]

    for pattern in injection_patterns:
        if re.search(pattern, sql, re.IGNORECASE):
            return False, "SQL检测到注入特征"

    return True, "OK"
```

### 4.6 性能瓶颈分析与优化

#### 4.6.1 潜在性能瓶颈

| 组件 | 潜在瓶颈 | 优化策略 |
|------|----------|----------|
| 采集层 | 高并发日志采集 | 采用iLogtail本地缓冲，批量发送 |
| 消息队列 | 消息积压 | Kafka分区扩展，监控消费速度 |
| 存储层 | Doris查询性能 | 合理分区，倒排索引，预聚合 |
| AI推理 | 模型推理延迟 | 推理缓存，模型量化，GPU加速 |
| API网关 | 请求限流 | 租户级限流，缓存热点数据 |

#### 4.6.2 可扩展性设计

- **水平扩展**: 所有服务组件支持无状态部署，可通过增加实例数线性扩展
- **自动扩缩容**: 基于CPU/内存利用率和请求量自动调整实例数
- **数据分片**: Doris按时间和租户分区，Prometheus按时间分片
- **负载均衡**: 所有服务前端配置负载均衡，分发请求
- **缓存策略**: 多级缓存设计，减少重复计算和数据库访问

---

## 5. 大屏展示设计

### 5.1 大屏布局（4K屏幕）

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  [AIOps智能运维中心]                    [时间: 2026-03-14 14:32:08]  [系统健康] │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────┐  ┌──────────────────────────┐                 │
│  │   🔥 拓扑热力图           │  │   🤖 AI诊断实时对话       │                 │
│  │                          │  │                          │                 │
│  │   [服务依赖图谱]          │  │   > 发现异常: DB连接超时    │                 │
│  │   红色节点=故障服务       │  │   > 检索最近100条ERROR日志  │                 │
│  │   连线闪烁=流量异常       │  │   > 检测到磁盘I/O利用率98%  │                 │
│  │   双击下钻详情           │  │   > ████████████████████ <-- 打字机效果       │
│  │                          │  │   结论: 磁盘IO瓶颈导致      │                 │
│  └──────────────────────────┘  └──────────────────────────┘                 │
│                                                                              │
│  ┌──────────────┬──────────────┬──────────────┬──────────────────────────┐  │
│  │ 告警趋势 📈   │  MTTR趋势 ⏱️  │  AI准确率 ✅  │  成本分析 💰            │  │
│  │ 今日: 12     │  本月: 15min │  92%         │  ↑12% vs 上月           │  │
│  │ 环比: -40%   │  目标: <30min│  趋势: ↑5%   │  Top租户: A/B/C         │  │
│  └──────────────┴──────────────┴──────────────┴──────────────────────────┘  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  🚨 最新AI诊断结论 (最后更新: 14:32:05)                                  │ │
│  │  ┌──────────────────────────────────────────────────────────────────┐  │ │
│  │  │  [P1] disk-io-throttling  │  数据库主库  │  置信度: 87%          │  │ │
│  │  │  根因: SSD健康度下降导致写入延迟飙升                                  │  │ │
│  │  │  影响: 支付服务延迟>5s，订单服务部分超时                                │  │ │
│  │  │  建议: 1) 切换只读副本  2) 联系厂商更换SSD                             │  │ │
│  │  └──────────────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. 安全与合规

| 安全域 | 措施 |
|--------|------|
| 数据脱敏 | iLogtail层正则脱敏，支持手机号/身份证/银行卡 |
| 字段加密 | Doris列级加密 (AES-256-GCM) |
| 访问审计 | 审计日志保留180天，支持SQL级别追溯 |
| 租户隔离 | SQL层强制tenant_id过滤 |
| 模型安全 | Prompt注入检测，敏感查询拦截 |
| 合规认证 | 等保三级、GDPR、ISO27001 |

---

## 7. 高可用性与灾难恢复设计

### 7.1 高可用性架构

| 组件 | 高可用方案 | 详细配置 |
|------|------------|----------|
| Kafka | 多副本集群 | 3节点，副本因子3，ISR机制 |
| Doris | 集群部署 | FE 3节点，BE 6节点，高可用模式 |
| Prometheus | 主备架构 | 2节点热备，使用Thanos实现长期存储 |
| Redis | Cluster模式 | 3主3从，自动故障转移 |
| Java控制面 | 多实例部署 | 3节点，负载均衡，会话共享 |
| Python AI推理面 | 多实例部署 | 4节点，负载均衡，自动扩缩容 |
| API网关 | 多实例部署 | 2节点，负载均衡 |

### 7.2 灾难恢复策略

| 级别 | 方案 | RTO | RPO |
|------|------|-----|-----|
| 单节点故障 | 自动故障转移 | < 30秒 | 0 |
| 单可用区故障 | 跨AZ部署 | < 5分钟 | 0 |
| 区域级故障 | 跨区域备份 | < 1小时 | < 5分钟 |

### 7.3 数据备份与恢复

- **Doris**: 每日全量备份 + 每小时增量备份，备份到对象存储
- **MySQL**: 每日全量备份 + 实时binlog复制
- **Milvus**: 定期快照备份
- **Prometheus**: 通过Thanos实现长期存储

### 7.4 监控与告警

- 部署Grafana + Prometheus监控全栈
- 关键指标告警：系统负载、磁盘使用率、服务可用性
- 部署ELK Stack收集和分析日志
- 实现分布式追踪，监控服务调用链路

---

## 8. 实施计划

### Phase 1: 基础平台 (M1-M2)
- [ ] iLogtail + Kafka部署（高可用集群）
- [ ] Doris + Prometheus集群搭建（HA配置）
- [ ] Java控制面基础框架（多实例部署）
- [ ] Python AI推理服务（GLM5部署，多实例）

### Phase 2: 核心AI能力 (M3-M4)
- [ ] MCP Tools工具箱开发
- [ ] AI诊断引擎上线
- [ ] 大屏可视化（AntV）
- [ ] 告警降噪机制

### Phase 3: 企业级增强 (M5-M6)
- [ ] 多租户隔离完善
- [ ] 成本治理模块
- [ ] RAG知识沉淀
- [ ] 安全合规模块
- [ ] 灾难恢复演练

### Phase 4: 智能化 (M7+)
- [ ] 异常检测模型训练
- [ ] 趋势预测能力
- [ ] 半自动修复
- [ ] 生态集成

---

*本文档为AIOps智能运维平台的顶层设计文档，后续开发时应以本文档为基准。*
