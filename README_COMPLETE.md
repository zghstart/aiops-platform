# AIOps 智能运维平台 - 完整开发版

企业级 AI 智能运维平台，采用 **Java + Python 双栈架构**。

## ✅ 已完成功能

### 后端 (Java 控制面)
- ✅ 告警接收与降噪算法 (5种策略)
- ✅ 事件管理与聚类
- ✅ 拓扑管理 API
- ✅ 指标查询 Prometheus 集成
- ✅ MCP 工具调用集成

### AI 引擎 (Python)
- ✅ ReAct 编排诊断流程
- ✅ MCP 工具 (日志/指标/拓扑/案例)
- ✅ GLM5 LLM 集成
- ✅ 流式 SSE 分析

### 前端 (React)
- ✅ 告警列表与管理
- ✅ 告警详情页
- ✅ 事件列表与详情
- ✅ 拓扑可视化
- ✅ AI 流式对话

## 🚀 快速启动

```bash
# 1. 启动基础设施
cd aiops-deploy/docker
docker-compose up -d mysql redis kafka doris-fe doris-be milvus-standalone

# 2. 启动 Java 控制面
./gradlew bootRun --args='--spring.profiles.active=dev'

# 3. 启动 Python AI 引擎
pip install -r requirements.txt
python -m app.main

# 4. 启动前端
npm install && npm run dev
```

## 📚 文档

| 文档 | 内容 |
|------|------|
| [架构设计](docs/01_Architecture_Design.md) | 整体架构、业务流程 |
| [API 规范](docs/02_API_Specification.md) | RESTFUL API、SSE、Webhook |
| [MCP Tools](docs/04_MCP_Tools.md) | AI 工具调用定义 |

---

*开发完成时间: 2024-03-16*
