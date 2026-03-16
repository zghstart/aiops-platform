# CLAUDE.md - AIOps 智能运维平台

## 项目概述

企业级 AI 智能运维平台，采用 **Java + Python 双栈架构**，集成 Claude Code + GLM5 实现 AI 根因分析，为企业运维团队提供智能化的故障诊断和决策支持。

### 核心特性

- **多源日志采集**：支持主机、数据库、K8s、网络、存储等多种日志解析
- **AI 智能降噪**：规则降噪 + 时间窗口 + 频率控制，减少告警疲劳
- **双栈架构**：Java 控制面（高性能） + Python AI 引擎（灵活推理）
- **MCP 工具调用**：ReAct 模式，AI 可查询日志、指标、拓扑
- **链路追踪**：OpenTelemetry 集成，Trace-Log-Metric 关联
- **大屏可视化**：React + AntV G6，适配 4K 大屏展示
- **多租户隔离**：RBAC + 数据隔离，支持企业级部署

---

## 文档清单

项目共包含 16 份设计文档，涵盖完整的技术架构：

| 序号 | 文档 | 核心内容 | 大小 |
|-----|------|---------|------|
| 01 | Architecture_Design.md | 整体架构、业务流程、技术选型 | 22KB |
| 02 | API_Specification.md | REST API、SSE 流式、Webhook 接口 | 13KB |
| 03 | Database_Design.md | Doris + MySQL + Redis + Milvus 模型 | 18KB |
| 04 | MCP_Tools.md | 工具定义、调用链、Registry | 16KB |
| 05 | Java_Control_Plane.md | Spring Boot 控制面实现 | 24KB |
| 06 | Python_AI_Engine.md | FastAPI + ReAct 编排 + vLLM 集成 | 25KB |
| 07 | Frontend_Dashboard.md | React 大屏 + TypeWriter + SSE | 25KB |
| 08 | Deployment_Ops.md | Docker + K8s + 监控告警 | 22KB |
| 09 | Log_Parser_Design.md | iLogtail 多源日志解析 | 19KB |
| 10 | Auth_Permission.md | JWT + RBAC + MFA + 审计 | 27KB |
| 11 | Test_Specification.md | 单元/集成/E2E + AI 准确性测试 | 20KB |
| 12 | HA_DR_Design.md | 高可用架构 + 故障切换 + 容灾 | 22KB |
| 13 | Capacity_Planning.md | 容量模型 + 性能基线 + 成本估算 | 18KB |
| 14 | Data_Lifecycle.md | 分层存储 + 归档 + 数据治理 | 15KB |
| 15 | Telemetry_Tracing.md | OpenTelemetry + Trace 关联分析 | 23KB |
| - | AI运维平台.md | 原始参考文档（Gemini 方案） | 12KB |

---

## 技术栈

### 后端服务

| 组件 | 技术 | 版本 |
|-----|------|------|
| Java 控制面 | Spring Boot | 3.2+ |
| Python AI 引擎 | FastAPI | 0.100+ |
| LLM 推理 | vLLM / SGLang | latest |
| 大模型 | GLM5-32B | 官方 |
| 消息队列 | Apache Kafka | 3.6+ |
| 缓存 | Redis Cluster | 7.x |

### 数据存储

| 用途 | 存储 | 说明 |
|-----|------|------|
| 日志/Trace | Apache Doris | 日志 + 指标 + 链路统一存储 |
| 业务数据 | MySQL 8 | 租户、用户、配置 |
| 向量检索 | Milvus | RAG 知识库 |
| 对象存储 | OSS/S3 | 归档 + 备份 |

### 前端与可视化

| 组件 | 技术 |
|-----|------|
| 框架 | React 18 + TypeScript |
| UI 库 | Ant Design |
| 可视化 | AntV G6 (拓扑) |
| 图表 | AntV S2 / G2Plot |

### 基础设施

| 组件 | 技术 |
|-----|------|
| 容器化 | Docker |
| 编排 | Kubernetes |
| 可观测 | OpenTelemetry + Jaeger |
| 监控 | Prometheus + Grafana |

---

## 快速启动

### 开发环境启动

```bash
# 1. 启动基础设施
docker-compose -f deploy/docker-compose.dev.yml up -d

# 2. 启动 Java 控制面
cd aiops-control-plane
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 启动 Python AI 引擎
cd aiops-ai-engine
pip install -r requirements.txt
python -m app.main

# 4. 启动前端
cd aiops-dashboard
npm install
npm run dev
```

### 访问地址

- 控制面 API: http://localhost:8080
- AI 引擎: http://localhost:8000
- 大屏: http://localhost:3000
- Jaeger UI: http://localhost:16686

---

## 关键设计决策

### 1. 为什么用 Java + Python 双栈？

| 层面 | Java | Python |
|-----|------|--------|
| **控制面** | HTTP/线程管理成熟 | 生态较弱 |
| **数据层** | MyBatis/JDBC 稳定 | ORM 相对弱 |
| **AI 推理** | 不擅长 | Transformers/HuggingFace |
| **工具生态** | 企业级中间件丰富 | ML/DL 库丰富 |

**决策**：Java 负责高并发控制面，Python 负责灵活 AI 编排

### 2. 为什么选择 GLM5？

- **工具调用能力**：原生支持 Function Calling，适合 MCP
- **中文理解**：优于多数开源模型
- **成本控制**：相比 GPT-4，自部署成本可控
- **数据安全**：私有化部署，不外泄数据

### 3. 为什么用 Doris 而不是 Elasticsearch？

| 特性 | Doris | ES |
|-----|-------|-----|
| 标准 SQL | ✅ | DSL |
| 实时写入 | ✅ 秒级 | 秒级 |
| 复杂分析 | ✅ MPP | 有限 |
| 存储成本 | 较低 | 高 |

**决策**：统一日志+指标+链路存储，简化架构

---

## 开发规范

### 代码规范

- **Java**: Google Java Style + Spotless 自动格式化
- **Python**: Black + isort + flake8
- **TypeScript**: Prettier + ESLint

### Git 分支

```
main
├── develop
├── feature/xxx
├── hotfix/xxx
└── release/xxx
```

### 提交信息

```
type(scope): subject

body

footer

# type: feat|fix|docs|style|refactor|test|chore
```

---

## 项目结构

```
aiops/
├── aiops-control-plane/          # Java 控制面
│   ├── src/main/java/
│   │   ├── controller/           # API 层
│   │   ├── service/              # 业务逻辑
│   │   ├── repository/           # 数据访问
│   │   ├── domain/               # 领域模型
│   │   └── config/               # 配置
│   └── src/test/
│
├── aiops-ai-engine/              # Python AI 引擎
│   ├── app/
│   │   ├── core/                 # ReAct 编排
│   │   ├── adapters/             # LLM 适配器
│   │   ├── tools/                # MCP 工具
│   │   ├── services/             # 业务服务
│   │   └── api/                  # FastAPI 路由
│   └── tests/
│
├── aiops-dashboard/              # 前端大屏
│   ├── src/
│   │   ├── components/           # 组件
│   │   ├── pages/                # 页面
│   │   ├── hooks/                # React Hooks
│   │   ├── services/             # API 调用
│   │   └── utils/                # 工具
│   └── public/
│
├── aiops-ilogtail-config/        # iLogtail 配置
│   └── plugins/
│
├── aiops-deploy/                 # K8s 部署
│   ├── k8s/
│   ├── docker/
│   └── helm/
│
└── docs/                         # 设计文档
    └── *.md
```

---

## 性能基准

| 指标 | 目标 | 说明 |
|-----|------|------|
| 告警延迟 | < 200ms | 从接收到入库 |
| AI 分析 | 5-30s | 简单到复杂场景 |
| 大屏加载 | < 2s | 首次渲染 |
| 日志写入 | 50000 EPS | 单集群 |
| AI 并发 | 50 QPS | 复杂分析 |

---

## 安全合规

- **认证**：JWT + API Key 双模式
- **授权**：RBAC，租户数据隔离
- **审计**：操作日志永久保留
- **加密**：传输 TLS，存储 AES
- **敏感数据**：密码脱敏，SQL 隐藏

---

## 维护者

- **架构设计**: Claude Code + Human
- **核心开发**: [待补充]
- **文档维护**: [待补充]

---

## 参考资源

- [MCP Protocol](https://modelcontextprotocol.io/)
- [OpenTelemetry](https://opentelemetry.io/)
- [Doris Docs](https://doris.apache.org/)
- [GLM5 Model](https://huggingface.co/THUDM/glm-4)

---

*最后更新: 2024-03-15*
