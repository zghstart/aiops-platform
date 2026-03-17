# AIOps 智能运维平台

企业级 AI 智能运维平台，采用 **Java + Python 双栈架构**，集成 Claude Code + GLM5 实现 AI 根因分析，为企业运维团队提供智能化的故障诊断和决策支持。

## 核心特性

- **多源日志采集**：支持主机、数据库、K8s、网络、存储等多种日志解析
- **AI 智能降噪**：规则降噪 + 时间窗口 + 频率控制，减少告警疲劳
- **双栈架构**：Java 控制面（高性能） + Python AI 引擎（灵活推理）
- **MCP 工具调用**：ReAct 模式，AI 可查询日志、指标、拓扑
- **链路追踪**：OpenTelemetry 集成，Trace-Log-Metric 关联
- **大屏可视化**：React + AntV G6，适配 4K 大屏展示
- **多租户隔离**：RBAC + 数据隔离，支持企业级部署

## 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端层 (React + TypeScript)               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  Dashboard   │  │  Topology    │  │  AI Stream Log       │  │
│  │  大屏可视化   │  │  服务拓扑图   │  │  AI 诊断实时对话      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API 网关层 (Spring Boot 3.2)               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  REST API    │  │  SSE Stream  │  │  WebSocket           │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│  Java 控制面     │  │  Python AI引擎  │  │  Kafka 消息队列      │
│  (Spring Boot)  │  │  (FastAPI)      │  │  (事件驱动)          │
│                 │  │                 │  │                     │
│  - 告警管理      │  │  - ReAct 编排   │  │  - 告警事件          │
│  - 拓扑服务      │  │  - MCP 工具     │  │  - 分析任务          │
│  - 仪表板数据    │  │  - LLM 适配     │  │  - 审计日志          │
│  - 审计日志      │  │  - RAG 检索     │  │                     │
└─────────────────┘  └─────────────────┘  └─────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│  Apache Doris   │  │  MySQL 8       │  │  Redis Cluster      │
│  日志/Trace存储  │  │  业务数据       │  │  缓存/会话           │
└─────────────────┘  └─────────────────┘  └─────────────────────┘
          │
          ▼
┌─────────────────┐
│  Milvus         │
│  向量检索 (RAG)  │
└─────────────────┘
```

## 快速启动

### 环境要求

| 组件 | 版本 |
|------|------|
| Java | 21+ (Temurin) |
| Python | 3.11+ |
| Node.js | 18+ |
| Docker | 24+ |
| Docker Compose | 2.20+ |

### 1. 启动基础设施

```bash
cd aiops-deploy/docker
docker-compose up -d
```

等待所有服务健康后继续。

### 2. 启动 Java 控制面

```bash
cd aiops-control-plane

# 使用 Maven
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 或使用环境变量
export SPRING_PROFILES_ACTIVE=dev
java -jar target/aiops-control-plane.jar
```

### 3. 启动 Python AI 引擎

```bash
cd aiops-ai-engine

# 创建虚拟环境
python -m venv venv
source venv/bin/activate  # Linux/macOS
# venv\Scripts\activate  # Windows

# 安装依赖
pip install -r requirements.txt

# 启动服务
python -m app.main
```

### 4. 启动前端大屏

```bash
cd aiops-dashboard

# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build
```

### 访问地址

| 服务 | URL | 说明 |
|------|-----|------|
| Dashboard | http://localhost:3000 | 大屏可视化 |
| Java API | http://localhost:8080 | REST API |
| API Docs | http://localhost:8080/swagger-ui.html | Swagger UI |
| AI Engine | http://localhost:8000/docs | FastAPI Docs |
| Jaeger | http://localhost:16686 | 链路追踪 |
| Grafana | http://localhost:3001 | 监控面板 |

## 项目结构

```
aiops/
├── aiops-control-plane/          # Java 控制面
│   ├── src/main/java/
│   │   ├── controller/           # API 层
│   │   ├── service/              # 业务逻辑
│   │   │   ├── alert/           # 告警服务
│   │   │   ├── topology/        # 拓扑服务
│   │   │   ├── dashboard/       # 仪表板服务
│   │   │   └── audit/           # 审计服务
│   │   ├── repository/           # 数据访问
│   │   ├── domain/               # 领域模型
│   │   ├── dto/                  # 数据传输对象
│   │   ├── config/               # 配置
│   │   └── aspect/               # AOP 切面
│   └── src/test/
│
├── aiops-ai-engine/              # Python AI 引擎
│   ├── app/
│   │   ├── core/                 # ReAct 编排
│   │   ├── llm/                  # LLM 适配器
│   │   ├── mcp/                  # MCP 工具
│   │   ├── agent/                # Agent 实现
│   │   ├── infrastructure/       # 基础设施
│   │   └── api/                  # FastAPI 路由
│   └── tests/
│
├── aiops-dashboard/              # 前端大屏
│   ├── src/
│   │   ├── components/           # 组件
│   │   │   ├── Alerts/          # 告警组件
│   │   │   ├── Charts/          # 图表组件
│   │   │   ├── Topology/        # 拓扑图组件
│   │   │   └── AIStreamLog/     # AI 流式日志
│   │   ├── pages/                # 页面
│   │   ├── services/             # API 调用
│   │   ├── hooks/                # React Hooks
│   │   ├── store/                # 状态管理
│   │   └── types/                # TypeScript 类型
│   └── public/
│
├── aiops-deploy/                 # 部署配置
│   ├── docker/                   # Docker Compose
│   ├── k8s/                      # Kubernetes manifests
│   └── helm/                     # Helm charts
│
└── docs/                         # 设计文档
    ├── 01_Architecture_Design.md
    ├── 02_API_Specification.md
    ├── 03_Database_Design.md
    ├── 04_MCP_Tools.md
    ├── 05_Java_Control_Plane.md
    ├── 06_Python_AI_Engine.md
    └── 07_Frontend_Dashboard.md
```

## 技术栈

### 后端服务

| 组件 | 技术 | 版本 |
|-----|------|------|
| Java 控制面 | Spring Boot | 3.2+ |
| Python AI 引擎 | FastAPI | 0.100+ |
| LLM 推理 | vLLM / SGLang | latest |
| 大模型 | GLM5-32B / Claude | - |
| 消息队列 | Apache Kafka | 3.6+ |
| 缓存 | Redis | 7.x |

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
| UI 库 | Ant Design 5 |
| 拓扑图 | AntV G6 |
| 图表 | AntV G2Plot |
| 状态管理 | Zustand |

### 可观测性

| 组件 | 技术 |
|-----|------|
| 链路追踪 | OpenTelemetry + Jaeger |
| 监控 | Prometheus + Grafana |
| 日志 | Logback + Doris |

## API 示例

### 获取仪表板摘要

```bash
curl "http://localhost:8080/api/v1/dashboard/summary?tenantId=default"
```

### 获取服务拓扑

```bash
curl "http://localhost:8080/api/v1/dashboard/topology?tenantId=default&depth=2"
```

### AI 分析 (SSE 流式)

```bash
curl -N "http://localhost:8000/api/v1/ai/analyze/stream?incidentId=inc-001"
```

## 开发规范

### 代码规范

- **Java**: Google Java Style + Spotless
- **Python**: Black + isort + ruff
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

# type: feat|fix|docs|style|refactor|test|chore
# 示例:
feat(alert): add noise reduction service
fix(api): resolve CORS issue for frontend
docs(readme): update deployment guide
```

## 性能基准

| 指标 | 目标 | 说明 |
|-----|------|------|
| 告警延迟 | < 200ms | 从接收到入库 |
| AI 分析 | 5-30s | 简单到复杂场景 |
| 大屏加载 | < 2s | 首次渲染 |
| 日志写入 | 50000 EPS | 单集群 |
| AI 并发 | 50 QPS | 复杂分析 |

## 安全合规

- **认证**：JWT + API Key 双模式
- **授权**：RBAC，租户数据隔离
- **审计**：操作日志永久保留
- **加密**：传输 TLS，存储 AES
- **敏感数据**：密码脱敏，SQL 隐藏

## 设计文档

| 文档 | 内容 |
|------|------|
| [架构设计](docs/01_Architecture_Design.md) | 整体架构、业务流程 |
| [API 规范](docs/02_API_Specification.md) | RESTful API、SSE 流式 |
| [数据库设计](docs/03_Database_Design.md) | Doris + MySQL 模型 |
| [MCP Tools](docs/04_MCP_Tools.md) | AI 工具调用定义 |
| [Java 控制面](docs/05_Java_Control_Plane.md) | 控制面实现细节 |
| [Python AI 引擎](docs/06_Python_AI_Engine.md) | ReAct 编排、LLM 适配 |
| [前端大屏](docs/07_Frontend_Dashboard.md) | React + AntV 可视化 |

## 参考资源

- [MCP Protocol](https://modelcontextprotocol.io/)
- [OpenTelemetry](https://opentelemetry.io/)
- [Doris Docs](https://doris.apache.org/)
- [GLM Model](https://huggingface.co/THUDM/glm-4)

---

*Generated by Claude Code + AI Agent Team*

*Last updated: 2026-03-17*
