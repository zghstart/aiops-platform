"""Diagnostic Orchestrator - ReAct pattern"""

import json
import asyncio
from typing import AsyncGenerator, List, Dict, Any, Optional
from datetime import datetime
from dataclasses import dataclass, field
import structlog

from app.llm.client import LLMClient
from app.llm.glm5 import GLM5Adapter
from app.llm.streaming import StreamEvent
from app.mcp.registry import tool_registry
from app.mcp.executor import ToolExecutor
from app.models.analysis import AnalysisResult, ReasoningStep
from app.infrastructure.redis import RedisCache
from app.config import settings

logger = structlog.get_logger()


@dataclass
class DiagnosticContext:
    """Diagnostic context"""
    incident_id: str
    tenant_id: str
    service_id: str
    instance: Optional[str] = None
    time_range: Dict[str, str] = field(default_factory=dict)
    topology: Dict = field(default_factory=dict)
    max_rounds: int = 5
    confidence_threshold: float = 0.7
    max_analysis_time_sec: int = 120
    max_tokens_per_analysis: int = 8000
    token_budget_warning_threshold: float = 0.8


class DiagnosticOrchestrator:
    """ReAct pattern diagnostic orchestrator"""

    SYSTEM_PROMPT = """You are an expert SRE (Site Reliability Engineer) analyzing an online incident.
Please diagnose the issue through multiple rounds of tool calls, gradually narrowing down the root cause.

Available Tools:
{tools_schema}

Reasoning Principles:
1. Gather logs and metrics first, draw conclusions last
2. Based on findings, drill down specifically
3. If root cause cannot be confirmed, state the uncertainty clearly
4. Provide confidence score (0-1)
5. Give specific, actionable recommendations

Response Format (MUST be JSON):
{{
    "thought": "Your reasoning process",
    "action": "tool_name OR conclude",
    "action_input": {{"param": "value"}},
}}

When you are confident about the root cause, use "action": "conclude" with:
{{
    "thought": "Comprehensive analysis",
    "action": "conclude",
    "conclusion": {{
        "root_cause": "Root cause description",
        "confidence": 0.87,
        "evidence": ["Evidence 1", "Evidence 2"],
        "recommendations": ["Action 1", "Action 2"]
    }}
}}"""

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
        self.logger = logger.bind(component="DiagnosticOrchestrator")

    async def analyze(self, request) -> Optional[AnalysisResult]:
        """Synchronous analysis (returns final result)"""
        result = None
        async for event in self.analyze_stream(request.incident_id):
            if event.type == "conclusion":
                result = await self.get_result(request.incident_id)
                break
        return result

    async def analyze_stream(
        self,
        incident_id: str
    ) -> AsyncGenerator[StreamEvent, None]:
        """Streaming analysis (SSE)"""

        analysis_start_time = datetime.utcnow()
        total_tokens_used = 0

        context = await self._load_context(incident_id)

        saved_state = await self._restore_analysis_state(incident_id)
        if saved_state:
            reasoning_chain = [ReasoningStep(**step) for step in saved_state.get('reasoning_chain', [])]
            messages = saved_state.get('messages', [])
            total_tokens_used = saved_state.get('tokens_used', 0)
        else:
            tools_schema = self._build_tools_schema()
            system_prompt = self.SYSTEM_PROMPT.format(tools_schema=tools_schema)
            user_message = self._build_user_message(context)
            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_message}
            ]
            reasoning_chain = []

        yield StreamEvent.start(incident_id)

        try:
            async with asyncio.timeout(context.max_analysis_time_sec):
                for round_num in range(context.max_rounds):
                    # Token budget check
                    if total_tokens_used > context.max_tokens_per_analysis:
                        yield StreamEvent.timeout(
                            incident_id,
                            f"Token budget exhausted (used {total_tokens_used}), providing partial conclusion"
                        )
                        break

                    elapsed = (datetime.utcnow() - analysis_start_time).total_seconds()
                    if elapsed > context.max_analysis_time_sec * 0.9:
                        yield StreamEvent.timeout(
                            incident_id,
                            f"Time running out (used {elapsed:.0f}s), providing partial conclusion"
                        )
                        break

                    # LLM inference
                    llm_response = await self.llm.chat(messages)
                    total_tokens_used += self._estimate_tokens(llm_response)

                    parsed = self._parse_response(llm_response)
                    thought = parsed.get("thought", "")
                    action = parsed.get("action", "")
                    action_input = parsed.get("action_input", {})

                    yield StreamEvent.reasoning(
                        incident_id=incident_id,
                        round=round_num + 1,
                        thought=thought
                    )

                    # Token budget warning
                    if total_tokens_used > context.max_tokens_per_analysis * context.token_budget_warning_threshold:
                        yield StreamEvent.token_warning(
                            incident_id, total_tokens_used, context.max_tokens_per_analysis
                        )

                    # Persist state
                    await self._persist_analysis_state(
                        incident_id=incident_id,
                        reasoning_chain=reasoning_chain,
                        messages=messages,
                        tokens_used=total_tokens_used
                    )

                    # Check conclusion
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
                        await self._clear_analysis_state(incident_id)

                        yield StreamEvent.conclusion(incident_id, result.to_dict())
                        yield StreamEvent.complete(incident_id, len(reasoning_chain))
                        return

                    # Execute tool
                    yield StreamEvent.tool_call(
                        incident_id=incident_id,
                        round=round_num + 1,
                        action=action,
                        status="calling"
                    )

                    try:
                        tool_result = await self.executor.execute(
                            action,
                            action_input
                        )
                        observation = json.dumps(tool_result, ensure_ascii=False, indent=2)
                    except Exception as e:
                        observation = f"Tool execution failed: {str(e)}"
                        self.logger.error("Tool execution failed", tool=action, error=str(e))

                    yield StreamEvent.tool_result(
                        incident_id=incident_id,
                        round=round_num + 1,
                        action=action,
                        status="completed",
                        result_summary=self._summarize_result(observation)
                    )

                    # Record reasoning step
                    reasoning_chain.append(ReasoningStep(
                        step=round_num + 1,
                        thought=thought,
                        action=action,
                        action_input=json.dumps(action_input, ensure_ascii=False),
                        observation=observation,
                        timestamp=datetime.utcnow()
                    ))

                    # Update messages
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

            # Max rounds reached
            yield StreamEvent.timeout(incident_id, "Max reasoning rounds reached, providing partial conclusion")

        except asyncio.TimeoutError:
            yield StreamEvent.timeout(
                incident_id,
                f"Analysis timeout ({context.max_analysis_time_sec}s exceeded)"
            )

        except asyncio.CancelledError:
            yield StreamEvent.timeout(
                incident_id,
                "Analysis interrupted, state saved, can resume later"
            )

    def _parse_response(self, text: str) -> Dict:
        """Parse LLM JSON response"""
        try:
            # Extract JSON (may be wrapped in markdown)
            if "```json" in text:
                text = text.split("```json")[1].split("```")[0]
            elif "```" in text:
                text = text.split("```")[1].split("```")[0]
            return json.loads(text.strip())
        except Exception:
            self.logger.warning("Failed to parse LLM response as JSON", response=text[:200])
            return {
                "thought": text[:500] if text else "No response from LLM",
                "action": "conclude",
                "conclusion": {
                    "root_cause": "Parse failed - check raw logs",
                    "confidence": 0.1,
                    "evidence": [],
                    "recommendations": ["Contact administrator"]
                }
            }

    def _build_tools_schema(self) -> str:
        """Build tools schema"""
        tools = tool_registry.list_tools()
        return "\n".join([
            f"- {t['name']}: {t['description']}\n  Parameters: {t['parameters']}"
            for t in tools
        ])

    def _build_user_message(self, context: DiagnosticContext) -> str:
        return f"""Please analyze the following incident:

Incident ID: {context.incident_id}
Tenant: {context.tenant_id}
Service: {context.service_id}
Instance: {context.instance or 'N/A'}
Time Range: {context.time_range.get('start', 'N/A')} to {context.time_range.get('end', 'N/A')}

Start your investigation and provide your reasoning process."""

    def _summarize_result(self, observation: str, max_len: int = 200) -> str:
        if len(observation) > max_len:
            return observation[:max_len] + "..."
        return observation

    async def _load_context(self, incident_id: str) -> DiagnosticContext:
        # Load from Redis/DB
        return DiagnosticContext(
            incident_id=incident_id,
            tenant_id="default",
            service_id="unknown"
        )

    async def _save_result(self, result: AnalysisResult):
        """Save result to Redis"""
        key = f"analysis_result:{result.incident_id}"
        await self.cache.setex(key, 86400, json.dumps(result.__dict__, default=str))

    async def _restore_analysis_state(self, incident_id: str) -> Optional[Dict]:
        """Restore from Redis"""
        key = f"analysis_state:{incident_id}"
        state = await self.cache.get(key)
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
        """Persist state to Redis"""
        key = f"analysis_state:{incident_id}"
        state = {
            "reasoning_chain": [step.__dict__ if hasattr(step, '__dict__') else step for step in reasoning_chain],
            "messages": messages,
            "tokens_used": tokens_used,
            "updated_at": datetime.utcnow().isoformat()
        }
        await self.cache.setex(key, 86400, json.dumps(state, default=str))

    async def _clear_analysis_state(self, incident_id: str):
        """Clear completed analysis state"""
        key = f"analysis_state:{incident_id}"
        await self.cache.delete(key)

    def _estimate_tokens(self, text: str) -> int:
        """Estimate token count (Chinese ~1 char = 1 token, English ~4 chars = 1 token)"""
        chinese_chars = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
        other_chars = len(text) - chinese_chars
        return chinese_chars + other_chars // 4

    async def get_result(self, analysis_id: str) -> Optional[AnalysisResult]:
        """Get analysis result"""
        key = f"analysis_result:{analysis_id}"
        data = await self.cache.get(key)
        if data:
            result_dict = json.loads(data)
            return AnalysisResult(**result_dict)
        return None

    async def submit_feedback(self, analysis_id: str, feedback: Dict):
        """Submit feedback"""
        self.logger.info("Received feedback", analysis_id=analysis_id, feedback=feedback)
        key = f"feedback:{analysis_id}"
        await self.cache.setex(key, 86400 * 30, json.dumps(feedback))
