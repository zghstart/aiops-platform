# AIOps 智能运维平台 - Python AI引擎开发指南

## 1. 项目结构

```
aiops-ai-engine/
├── app/
│   ├── __init__.py
│   ├── main.py                 # FastAPI入口
│   ├── config.py               # 配置管理
│   ├── dependency.py           # 依赖注入
│   └── middleware/
│       ├── tenant.py           # 租户隔离
│       └── logging.py          # 日志中间件
├── agent/
│   ├── __init__.py
│   ├── orchestrator.py         # ReAct编排器
│   ├── context.py              # 上下文管理
│   ├── memory.py               # 对话记忆
│   └── types.py                # 类型定义
├── llm/
│   ├── __init__.py
│   ├── client.py               # vLLM/OpenAI客户端
│   ├── glm5.py                 # GLM5专用适配
│   ├── streaming.py            # SSE流式处理
│   └── cache.py                # LLM结果缓存
├── mcp/
│   ├── __init__.py
│   ├── registry.py             # 工具注册表
│   ├── executor.py             # 工具执行器
│   ├── tools/
│   │   ├── __init__.py
│   │   ├── search_logs.py      # 日志检索
│   │   ├── query_metrics.py    # 指标查询
│   │   ├── get_trace.py        # 链路查询
│   │   ├── get_topology.py     # 拓扑查询
│   │   ├── get_service.py      # 服务详情
│   │   ├── get_incidents.py    # 故障列表
│   │   ├── search_cases.py     # 历史Case检索
│   │   └── format_time.py      # 时间格式化
│   └── prompts/
│       └── system.py           # System Prompt
├── rag/
│   ├── __init__.py
│   ├── indexer.py              # 知识库索引
│   ├── retriever.py            # 向量检索
│   └── vector_store.py         # Milvus客户端
├── models/
│   ├── __init__.py
│   ├── alert.py                # 告警模型
│   ├── incident.py             # 故障模型
│   ├── analysis.py             # 诊断结果模型
│   └── request.py              # 请求模型
├── infrastructure/
│   ├── __init__.py
│   ├── doris.py                # Doris客户端
│   ├── prometheus.py           # Prometheus客户端
│   ├── redis.py                # Redis客户端
│   └── kafka.py                # Kafka消费者
├── core/
│   ├── __init__.py
│   ├── exceptions.py           # 异常定义
│   └── utils.py                # 工具函数
├── tests/
│   ├── __init__.py
│   ├── test_agent.py
│   └── test_tools.py
├── Dockerfile
├── requirements.txt
└── pyproject.toml
```

---

## 2. 核心代码

### 2.1 FastAPI入口 (main.py)

```python
# app/main.py
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
import uvicorn

from app.api import router as api_router
from app.config import settings
from app.middleware.tenant import TenantMiddleware
from app.middleware.logging import LoggingMiddleware

app = FastAPI(
    title="AIOps AI Engine",
    description="AI-powered diagnostic engine for AIOps platform",
    version="1.0.0"
)

# 中间件
app.add_middleware(TenantMiddleware)
app.add_middleware(LoggingMiddleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)

app.include_router(api_router, prefix="/api/v1")

@app.get("/health")
async def health_check():
    return {"status": "ok", "version": "1.0.0"}

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.DEBUG,
        workers=settings.WORKERS if not settings.DEBUG else 1
    )
```

### 2.2 API路由 (api/router.py)

```python
# app/api/__init__.py
from fastapi import APIRouter, Depends, BackgroundTasks
from fastapi.responses import StreamingResponse
from typing import AsyncGenerator
import asyncio

from app.agent.orchestrator import DiagnosticOrchestrator
from app.llm.streaming import StreamEvent
from app.models.request import AnalyzeRequest, AnalyzeResponse
from app.dependency import get_orchestrator

router = APIRouter()

@router.post("/ai/analyze", response_model=AnalyzeResponse)
async def analyze_incident(
    request: AnalyzeRequest,
    orchestrator: DiagnosticOrchestrator = Depends(get_orchestrator)
) -> AnalyzeResponse:
    """非流式诊断分析"""
    result = await orchestrator.analyze(request)
    return AnalyzeResponse.from_result(result)


@router.get("/ai/analyze/stream")
async def analyze_incident_stream(
    incident_id: str,
    orchestrator: DiagnosticOrchestrator = Depends(get_orchestrator)
) -> StreamingResponse:
    """SSE流式诊断分析"""

    async def event_generator() -> AsyncGenerator[str, None]:
        async for event in orchestrator.analyze_stream(incident_id):
            yield f"event: {event.type}\ndata: {event.to_json()}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.get("/ai/analysis/{analysis_id}")
async def get_analysis_result(
    analysis_id: str,
    orchestrator: DiagnosticOrchestrator = Depends(get_orchestrator)
) -> AnalyzeResponse:
    """查询诊断结果"""
    result = await orchestrator.get_result(analysis_id)
    return AnalyzeResponse.from_result(result)


@router.post("/ai/analysis/{analysis_id}/feedback")
async def submit_feedback(
    analysis_id: str,
    feedback: dict,
    orchestrator: DiagnosticOrchestrator = Depends(get_orchestrator)
):
    """提交人工反馈"""
    await orchestrator.submit_feedback(analysis_id, feedback)
    return {"status": "ok"}
```

### 2.3 ReAct编排器 (agent/orchestrator.py)

```python
# app/agent/orchestrator.py
import json
import asyncio
from typing import AsyncGenerator, List, Dict, Any
from datetime import datetime
from dataclasses import dataclass, field

from app.llm.client import LLMClient
from app.llm.glm5 import GLM5Adapter
from app.llm.streaming import StreamEvent
from app.mcp.registry import tool_registry
from app.mcp.executor import ToolExecutor
from app.models.analysis import AnalysisResult, ReasoningStep
from app.infrastructure.redis import RedisCache


@dataclass
class DiagnosticContext:
    """诊断上下文"""
    incident_id: str
    tenant_id: str
    service_id: str
    instance: str
    time_range: Dict[str, str]
    topology: Dict = field(default_factory=dict)
    max_rounds: int = 5
    confidence_threshold: float = 0.7
    # 新增: 超时与成本限制
    max_analysis_time_sec: int = 60  # 总分析时间上限
    max_tokens_per_analysis: int = 8000  # 单次分析Token上限
    token_budget_warning_threshold: float = 0.8  # 预算预警阈值


class DiagnosticOrchestrator:
    """ReAct模式诊断编排器"""

    SYSTEM_PROMPT = """你是一位资深SRE专家，正在分析一个线上故障。
请通过多轮工具调用，逐步排查问题，最终给出根因分析和解决方案。

可用工具：
{tools_schema}

推理原则：
1. 先收集日志和指标，再下结论
2. 根据发现的问题，有针对性地下钻
3. 如果无法确认根因，明确说明不确定性
4. 给出置信度评分(0-1)

回复格式：
{format_instructions}
"""

    FORMAT_INSTRUCTIONS = """
回复必须是以下JSON格式：
{
    "thought": "你的思考过程",
    "action": "工具名 或 conclude",
    "action_input": "工具参数JSON字符串",
    "observation": "工具返回结果后的观察"  // 仅在action之后
}

当确认找到根因时，使用 action: "conclude"，并输出：
{
    "thought": "综合分析",
    "action": "conclude",
    "conclusion": {
        "root_cause": "根因描述",
        "confidence": 0.87,
        "evidence": ["证据1", "证据2"],
        "recommendations": ["建议1", "建议2"]
    }
}
"""

    def __init__(
        self,
        llm_client: LLMClient,
        glm5_adapter: GLM5Adapter,
        tool_executor: ToolExecutor,
        redis_cache: RedisCache
    ):
        self.llm = llm_client
        self.glm5 = glm5_adapter
        self.executor = tool_executor
        self.cache = redis_cache

    async def analyze(self, request) -> AnalysisResult:
        """同步分析（返回最终结果）"""
        result = None
        async for event in self.analyze_stream(request.incident_id):
            if event.type == "conclusion":
                result = event.data
                break
        return result

    async def analyze_stream(
        self,
        incident_id: str
    ) -> AsyncGenerator[StreamEvent, None]:
        """
        流式分析（SSE）- 安全增强版

        安全增强:
        - 总时间超时控制 (默认60秒)
        - Token预算跟踪与熔断
        - 状态持久化支持断点续跑
        """
        import asyncio
        from datetime import datetime

        analysis_start_time = datetime.utcnow()
        total_tokens_used = 0

        # 加载上下文（从持久化存储恢复）
        context = await self._load_context(incident_id)

        # 尝试恢复之前的分析状态
        saved_state = await self._restore_analysis_state(incident_id)
        if saved_state:
            reasoning_chain = saved_state.get('reasoning_chain', [])
            messages = saved_state.get('messages', [])
            total_tokens_used = saved_state.get('tokens_used', 0)
        else:
            # 构建System Prompt
            tools_schema = self._build_tools_schema()
            system_prompt = self.SYSTEM_PROMPT.format(
                tools_schema=tools_schema,
                format_instructions=self.FORMAT_INSTRUCTIONS
            )

            # 构建用户消息
            user_message = self._build_user_message(context)

            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_message}
            ]
            reasoning_chain = []

        yield StreamEvent.start(incident_id)

        try:
            # 设置总超时
            async with asyncio.timeout(context.max_analysis_time_sec):
                for round_num in range(context.max_rounds):
                    # Token预算检查
                    if total_tokens_used > context.max_tokens_per_analysis:
                        yield StreamEvent.timeout(
                            incident_id,
                            f"Token预算耗尽 (已用 {total_tokens_used})，给出部分结论"
                        )
                        break

                    # 检查剩余时间
                    elapsed = (datetime.utcnow() - analysis_start_time).total_seconds()
                    if elapsed > context.max_analysis_time_sec * 0.9:
                        yield StreamEvent.timeout(
                            incident_id,
                            f"时间即将耗尽 (已用 {elapsed:.0f}s)，给出部分结论"
                        )
                        break

                    # LLM推理
                    llm_response = await self._call_llm(messages)

                    # 跟踪Token消耗
                    tokens_this_call = self._estimate_tokens(llm_response)
                    total_tokens_used += tokens_this_call

                    parsed = self._parse_response(llm_response)
                    thought = parsed.get("thought", "")
                    action = parsed.get("action", "")
                    action_input = parsed.get("action_input", "{}")

                    # 发送思考事件
                    yield StreamEvent.reasoning(
                        incident_id=incident_id,
                        round=round_num + 1,
                        thought=thought
                    )

                    # 状态持久化（每步保存）
                    await self._persist_analysis_state(
                        incident_id=incident_id,
                        reasoning_chain=reasoning_chain,
                        messages=messages,
                        tokens_used=total_tokens_used
                    )

                    # Token预算预警
                    if total_tokens_used > context.max_tokens_per_analysis * context.token_budget_warning_threshold:
                        yield StreamEvent.reasoning(
                            incident_id=incident_id,
                            round=round_num + 1,
                            thought=f"> ⚠️ Token预算即将耗尽 (已用 {total_tokens_used}/{context.max_tokens_per_analysis})"
                        )

                    # 判断是否结束
                    if action == "conclude":
                        conclusion = parsed.get("conclusion", {})
                        result = AnalysisResult(
                            incident_id=incident_id,
                            confidence=conclusion.get("confidence", 0.5),
                            root_cause=conclusion.get("root_cause", ""),
                            evidence=conclusion.get("evidence", []),
                            recommendations=conclusion.get("recommendations", []),
                            reasoning_chain=reasoning_chain,
                            completed_at=datetime.utcnow(),
                            tokens_used=total_tokens_used,
                            analysis_time_sec=(datetime.utcnow() - analysis_start_time).total_seconds()
                        )
                        await self._save_result(result)
                        await self._clear_analysis_state(incident_id)  # 清除临时状态

                        yield StreamEvent.conclusion(incident_id, result.to_dict())
                        yield StreamEvent.complete(incident_id, len(reasoning_chain))
                        return

                    # 执行工具
                    yield StreamEvent.tool_call(
                        incident_id=incident_id,
                        round=round_num + 1,
                        action=action,
                        status="calling"
                    )

                    try:
                        tool_result = await self.executor.execute(
                            action,
                            json.loads(action_input)
                        )
                        observation = json.dumps(tool_result, ensure_ascii=False, indent=2)
                    except Exception as e:
                        observation = f"工具执行失败: {str(e)}"

                    yield StreamEvent.tool_result(
                        incident_id=incident_id,
                        round=round_num + 1,
                        action=action,
                        status="completed",
                        result_summary=self._summarize_result(observation)
                    )

                    # 记录推理步骤
                    reasoning_chain.append(ReasoningStep(
                        step=round_num + 1,
                        thought=thought,
                        action=action,
                        action_input=action_input,
                        observation=observation,
                        timestamp=datetime.utcnow()
                    ))

                    # 更新消息
                    messages.append({
                        "role": "assistant",
                        "content": json.dumps({
                            "thought": thought,
                            "action": action,
                            "action_input": action_input
                        }, ensure_ascii=False)
                    })
                    messages.append({
                        "role": "system",
                        "content": f"Observation: {observation}"
                    })

        except asyncio.TimeoutError:
            yield StreamEvent.timeout(
                incident_id,
                f"分析时间超过 {context.max_analysis_time_sec} 秒上限"
            )

        except asyncio.CancelledError:
            # 服务重启时，状态已保存，下次可以恢复
            yield StreamEvent.timeout(
                incident_id,
                "分析被中断，状态已保存，可稍后恢复"
            )
            # 5.1 LLM推理
            llm_response = await self._call_llm(messages)

            parsed = self._parse_response(llm_response)
            thought = parsed.get("thought", "")
            action = parsed.get("action", "")
            action_input = parsed.get("action_input", "{}")

            # 5.2 发送思考事件
            yield StreamEvent.reasoning(
                incident_id=incident_id,
                round=round_num + 1,
                thought=thought
            )

            # 5.3 判断动作
            if action == "conclude":
                # 结束推理
                conclusion = parsed.get("conclusion", {})

                # 保存结果
                result = AnalysisResult(
                    incident_id=incident_id,
                    confidence=conclusion.get("confidence", 0.5),
                    root_cause=conclusion.get("root_cause", ""),
                    evidence=conclusion.get("evidence", []),
                    recommendations=conclusion.get("recommendations", []),
                    reasoning_chain=reasoning_chain,
                    completed_at=datetime.utcnow()
                )
                await self._save_result(result)

                yield StreamEvent.conclusion(incident_id, result.to_dict())
                yield StreamEvent.complete(incident_id, len(reasoning_chain))
                return

            # 5.4 执行工具
            yield StreamEvent.tool_call(
                incident_id=incident_id,
                round=round_num + 1,
                action=action,
                status="calling"
            )

            try:
                tool_result = await self.executor.execute(
                    action,
                    json.loads(action_input)
                )
                observation = json.dumps(tool_result, ensure_ascii=False, indent=2)
            except Exception as e:
                observation = f"工具执行失败: {str(e)}"

            yield StreamEvent.tool_result(
                incident_id=incident_id,
                round=round_num + 1,
                action=action,
                status="completed",
                result_summary=self._summarize_result(observation)
            )

            # 5.5 记录推理步骤
            reasoning_chain.append(ReasoningStep(
                step=round_num + 1,
                thought=thought,
                action=action,
                action_input=action_input,
                observation=observation,
                timestamp=datetime.utcnow()
            ))

            # 5.6 更新消息
            messages.append({
                "role": "assistant",
                "content": json.dumps({
                    "thought": thought,
                    "action": action,
                    "action_input": action_input
                }, ensure_ascii=False)
            })
            messages.append({
                "role": "system",
                "content": f"Observation: {observation}"
            })

        # 6. 超过最大轮数，给出部分结论
        yield StreamEvent.timeout(incident_id, "超过最大推理轮数，给出部分结论")

    async def _call_llm(self, messages: List[Dict]) -> str:
        """调用GLM vLLM服务"""
        response = await self.glm5.chat.completions.create(
            model="glm5",
            messages=messages,
            temperature=0.3,
            max_tokens=2048
        )
        return response.choices[0].message.content

    def _parse_response(self, text: str) -> Dict:
        """解析LLM返回的JSON"""
        try:
            # 提取JSON部分（可能包裹在markdown中）
            if "\`\`\`json" in text:
                text = text.split("\`\`\`json")[1].split("\`\`\`")[0]
            elif "\`\`\`" in text:
                text = text.split("\`\`\`")[1]
            return json.loads(text)
        except Exception:
            # 回退到简单解析
            return {
                "thought": text[:500],
                "action": "conclude",
                "conclusion": {
                    "root_cause": "解析失败，请查看原始日志",
                    "confidence": 0.1,
                    "evidence": [],
                    "recommendations": ["联系管理员检查AI引擎"]
                }
            }

    def _build_tools_schema(self) -> str:
        """构建工具Schema"""
        tools = tool_registry.list_tools()
        return "\n".join([
            f"- {t['name']}: {t['description']}\n  参数: {t['parameters']}"
            for t in tools
        ])

    def _build_user_message(self, context: DiagnosticContext) -> str:
        return f"""请分析以下故障：

故障ID: {context.incident_id}
租户: {context.tenant_id}
服务: {context.service_id}
实例: {context.instance}
时间范围: {context.time_range['start']} 至 {context.time_range['end']}

请开始排查，同时给出你的思考过程。"""

    def _summarize_result(self, observation: str, max_len: int = 200) -> str:
        if len(observation) > max_len:
            return observation[:max_len] + "..."
        return observation

    async def _load_context(self, incident_id: str) -> DiagnosticContext:
        # 从Redis/DB加载上下文
        pass

    async def _save_result(self, result: AnalysisResult):
        # 保存到Doris
        pass

    async def _restore_analysis_state(self, incident_id: str) -> Dict:
        """从Redis恢复分析状态 - 支持断点续跑"""
        state_key = f"ai_analysis_state:{incident_id}"
        state = await self.cache.get(state_key)
        if state:
            return json.loads(state)
        return None

    async def _persist_analysis_state(
        self,
        incident_id: str,
        reasoning_chain: List,
        messages: List,
        tokens_used: int
    ):
        """持久化分析状态到Redis"""
        state_key = f"ai_analysis_state:{incident_id}"
        state = {
            "reasoning_chain": [step.__dict__ for step in reasoning_chain],
            "messages": messages,
            "tokens_used": tokens_used,
            "updated_at": datetime.utcnow().isoformat()
        }
        # 设置24小时过期，防止内存泄漏
        await self.cache.setex(state_key, 86400, json.dumps(state))

    async def _clear_analysis_state(self, incident_id: str):
        """清除已完成的分析状态"""
        state_key = f"ai_analysis_state:{incident_id}"
        await self.cache.delete(state_key)

    def _estimate_tokens(self, text: str) -> int:
        """估算文本Token数（中文按1字1token，英文按4字符1token）"""
        chinese_chars = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
        other_chars = len(text) - chinese_chars
        return chinese_chars + other_chars // 4

    async def get_result(self, analysis_id: str) -> AnalysisResult:
        # 查询结果
        pass
```

### 2.4 Tool Registry (mcp/registry.py)

```python
# app/mcp/registry.py
from typing import Dict, Callable, List, Any
from dataclasses import dataclass
from functools import wraps


@dataclass
class Tool:
    name: str
    description: str
    parameters: Dict[str, Any]
    handler: Callable


class ToolRegistry:
    def __init__(self):
        self.tools: Dict[str, Tool] = {}

    def register(
        self,
        name: str,
        description: str,
        parameters: Dict
    ):
        """装饰器注册工具"""
        def decorator(func: Callable):
            self.tools[name] = Tool(
                name=name,
                description=description,
                parameters=parameters,
                handler=func
            )
            return func
        return decorator

    def get(self, name: str) -> Tool:
        return self.tools.get(name)

    def list_tools(self) -> List[Dict]:
        return [
            {
                "name": t.name,
                "description": t.description,
                "parameters": t.parameters
            }
            for t in self.tools.values()
        ]


# 全局注册表工具实例
tool_registry = ToolRegistry()
```

### 2.5 Tool实现示例 (mcp/tools/search_logs.py)

```python
# app/mcp/tools/search_logs.py
from typing import Dict, Any, Optional
from datetime import datetime

from app.mcp.registry import tool_registry
from app.infrastructure.doris import DorisClient


@tool_registry.register(
    name="search_logs",
    description="从Doris检索日志，支持关键词匹配和日志级别过滤",
    parameters={
        "type": "object",
        "properties": {
            "service_id": {
                "type": "string",
                "description": "服务ID，如'payment-service'"
            },
            "level": {
                "type": "string",
                "enum": ["ERROR", "WARN", "INFO", "DEBUG"],
                "description": "日志级别"
            },
            "time_range": {
                "type": "object",
                "properties": {
                    "start": {"type": "string", "description": "ISO格式开始时间"},
                    "end": {"type": "string", "description": "ISO格式结束时间"}
                },
                "required": ["start", "end"]
            },
            "keyword": {
                "type": "string",
                "description": "关键词搜索，如'connection pool'"
            },
            "trace_id": {
                "type": "string",
                "description": "按Trace ID筛选"
            },
            "limit": {
                "type": "integer",
                "default": 100,
                "description": "返回条目数，默认100"
            }
        },
        "required": ["time_range"]
    }
)
async def search_logs(
    time_range: Dict[str, str],
    service_id: Optional[str] = None,
    level: Optional[str] = None,
    keyword: Optional[str] = None,
    trace_id: Optional[str] = None,
    limit: int = 100,
    doris: DorisClient = None
) -> Dict[str, Any]:
    """搜索日志工具"""

    # 构建WHERE条件
    conditions = [
        f"timestamp >= '{time_range['start']}'",
        f"timestamp <= '{time_range['end']}'"
    ]

    if service_id:
        conditions.append(f"service_id = '{service_id}'")
    if level:
        conditions.append(f"level = '{level}'")
    if keyword:
        conditions.append(f"message MATCH '{keyword}'")
    if trace_id:
        conditions.append(f"trace_id = '{trace_id}'")

    where_clauses = " AND ".join(conditions)

    # 主查询
    sql = f"""
    SELECT
        timestamp,
        service_id,
        level,
        message,
        trace_id,
        host_ip
    FROM logs
    WHERE {where_clauses}
    ORDER BY timestamp DESC
    LIMIT {limit}
    """

    result = await doris.query(sql)

    # 统计查询
    summary_sql = f"""
    SELECT
        level,
        COUNT(*) as count
    FROM logs
    WHERE {where_clauses}
    GROUP BY level
    """

    summary = await doris.query(summary_sql)

    error_count = sum(r['count'] for r in summary if r['level'] == 'ERROR')
    warn_count = sum(r['count'] for r in summary if r['level'] == 'WARN')

    # 找出最常见的错误
    top_errors_sql = f"""
    SELECT
        message,
        COUNT(*) as count
    FROM logs
    WHERE {where_clauses} AND level = 'ERROR'
    GROUP BY message
    ORDER BY count DESC
    LIMIT 5
    """

    top_errors = await doris.query(top_errors_sql)

    return {
        "total": len(result),
        "logs": result[:50],  # 限制返回数量
        "summary": {
            "error_count": error_count,
            "warn_count": warn_count,
            "info_count": sum(r['count'] for r in summary if r['level'] == 'INFO'),
            "top_errors": [
                {"message": r["message"][:200], "count": r["count"]}
                for r in top_errors
            ]
        }
    }
```

### 2.6 GLM5 vLLM客户端 (llm/glm5.py)

```python
# app/llm/glm5.py
import httpx
from typing import AsyncGenerator, List, Dict, Any, Optional
import json


class GLM5Adapter:
    """GLM5 vLLM/SGLang 适配器"""

    def __init__(self, base_url: str, api_key: str = None):
        self.base_url = base_url
        self.api_key = api_key
        self.client = httpx.AsyncClient(
            timeout=60.0,
            limits=httpx.Limits(max_keepalive_connections=20, max_connections=50)
        )

    async def chat_completions(
        self,
        model: str,
        messages: List[Dict[str, str]],
        temperature: float = 0.3,
        max_tokens: int = 2048,
        stream: bool = False,
        tools: List[Dict] = None
    ) -> Any:
        """调用GLM5聊天接口"""

        payload = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": stream
        }

        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"

        headers = {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}

        response = await self.client.post(
            f"{self.base_url}/v1/chat/completions",
            json=payload,
            headers=headers
        )
        response.raise_for_status()

        return response.json()

    async def chat_completions_stream(
        self,
        model: str,
        messages: List[Dict[str, str]],
        **kwargs
    ) -> AsyncGenerator[str, None]:
        """流式调用"""

        payload = {
            "model": model,
            "messages": messages,
            "stream": True,
            **kwargs
        }

        headers = {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}

        async with self.client.stream(
            "POST",
            f"{self.base_url}/v1/chat/completions",
            json=payload,
            headers=headers
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    data = line[6:]
                    if data != "[DONE]":
                        yield data
```

### 2.7 Stream Event (llm/streaming.py)

```python
# app/llm/streaming.py
import json
from dataclasses import dataclass, asdict
from typing import Optional


@dataclass
class StreamEvent:
    """SSE事件"""
    type: str
    incident_id: str
    data: dict
    timestamp: str = None

    def __post_init__(self):
        if self.timestamp is None:
            from datetime import datetime
            self.timestamp = datetime.utcnow().isoformat()

    def to_json(self) -> str:
        return json.dumps(asdict(self), ensure_ascii=False)

    @classmethod
    def start(cls, incident_id: str) -> "StreamEvent":
        return cls("start", incident_id, {"status": "started"})

    @classmethod
    def reasoning(cls, incident_id: str, round: int, thought: str) -> "StreamEvent":
        return cls("reasoning", incident_id, {
            "round": round,
            "thought": thought
        })

    @classmethod
    def tool_call(cls, incident_id: str, round: int, action: str, status: str) -> "StreamEvent":
        return cls("tool_call", incident_id, {
            "round": round,
            "action": action,
            "status": status
        })

    @classmethod
    def tool_result(cls, incident_id: str, round: int, action: str, status: str, result_summary: str) -> "StreamEvent":
        return cls("tool_result", incident_id, {
            "round": round,
            "action": action,
            "status": status,
            "result_summary": result_summary
        })

    @classmethod
    def conclusion(cls, incident_id: str, conclusion: dict) -> "StreamEvent":
        return cls("conclusion", incident_id, conclusion)

    @classmethod
    def complete(cls, incident_id: str, total_rounds: int) -> "StreamEvent":
        return cls("complete", incident_id, {"total_rounds": total_rounds})

    @classmethod
    def timeout(cls, incident_id: str, message: str) -> "StreamEvent":
        return cls("timeout", incident_id, {"message": message})

    @classmethod
    def error(cls, incident_id: str, error: str) -> "StreamEvent":
        return cls("error", incident_id, {"error": error})
```

---

## 3. 配置与依赖

### 3.1 requirements.txt

```txt
# Web框架
fastapi>=0.109.0
uvicorn[standard]>=0.27.0

# 数据库
doris-driver>=1.2.0
redis>=5.0.0
pymilvus>=2.3.0
aiomysql>=0.2.0

# LLM
openai>=1.12.0
httpx>=0.26.0

# 消息队列
aiokafka>=0.9.0

# 向量/RAG
sentence-transformers>=2.3.0
faiss-cpu>=1.7.4

# 工具
pydantic>=2.6.0
python-dotenv>=1.0.0
structlog>=24.1.0
prometheus-client>=0.20.0

# 测试
pytest>=8.0.0
pytest-asyncio>=0.23.0
httpx>=0.26.0
```

### 3.2 Dockerfile

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制代码
COPY app/ ./app/

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import httpx; httpx.get('http://localhost:8000/health')" || exit 1

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
```

---

*本文档定义了Python AI引擎的开发规范，开发者应以此为准。*
