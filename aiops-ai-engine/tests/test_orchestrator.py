"""Tests for ReAct orchestrator"""

import pytest
import pytest_asyncio
from unittest.mock import AsyncMock, MagicMock, patch
from datetime import datetime

from app.agent.orchestrator import (
    ReActOrchestrator,
    OrchestratorState,
    ReasoningStep,
    get_orchestrator
)
from app.mcp.registry import tool_registry


class TestReActOrchestrator:
    """Test ReAct orchestrator functionality"""

    @pytest_asyncio.fixture
    async def orchestrator(self, mock_redis):
        """Create orchestrator instance with mocked dependencies"""
        orch = ReActOrchestrator()
        orch.redis = mock_redis
        orch.llm = MagicMock()
        orch.llm.chat = AsyncMock(return_value={
            "choices": [{"message": {"content": "Thought: analyze logs\nAction: search_logs\nAction Input: {}", "tool_calls": None}}]
        })
        orch.llm.chat_stream = AsyncMock(return_value=self._mock_stream())
        return orch

    async def _mock_stream(self):
        """Mock LLM stream"""
        chunks = [
            {"choices": [{"delta": {"content": "Analyzing"}}]},
            {"choices": [{"delta": {"content": "..."}}]},
        ]
        for chunk in chunks:
            yield chunk

    @pytest.mark.asyncio
    async def test_orchestrator_initialization(self, orchestrator):
        """Test orchestrator initializes correctly"""
        assert orchestrator is not None
        assert orchestrator.max_rounds == 5
        assert orchestrator.timeout_sec == 120

    @pytest.mark.asyncio
    async def test_analyze_invalid_query(self, orchestrator):
        """Test analyze with empty query"""
        with pytest.raises(ValueError, match="Query cannot be empty"):
            await orchestrator.analyze_task("", "inc-001", "payment-service")

    @pytest.mark.asyncio
    async def test_analyze_with_incident(self, orchestrator):
        """Test analyze with valid incident"""
        result = await orchestrator.analyze_task(
            query="High CPU usage in payment service",
            incident_id="inc-001",
            service_id="payment-service"
        )

        assert result["status"] in ["success", "timeout", "error"]
        assert result["query"] == "High CPU usage in payment service"
        assert result["incidentId"] == "inc-001"

    @pytest.mark.asyncio
    async def test_analyze_stores_result(self, orchestrator, mock_redis):
        """Test analyze stores result in Redis"""
        result = await orchestrator.analyze_task(
            query="Test query",
            incident_id="inc-002",
            service_id="test-service"
        )

        # Verify Redis was called to store result
        mock_redis.setex.assert_called()

    @pytest.mark.asyncio
    async def test_get_analysis_result(self, orchestrator, mock_redis):
        """Test retrieving analysis result"""
        mock_redis.get = AsyncMock(return_value=str({
            "incidentId": "inc-001",
            "status": "success",
            "rootCause": "Connection pool exhausted"
        }))

        result = await orchestrator.get_analysis_result("inc-001")

        assert result is not None
        assert result["incidentId"] == "inc-001"

    @pytest.mark.asyncio
    async def test_get_analysis_not_found(self, orchestrator, mock_redis):
        """Test retrieving non-existent analysis"""
        mock_redis.get = AsyncMock(return_value=None)

        result = await orchestrator.get_analysis_result("non-existent")
        assert result is None

    @pytest.mark.asyncio
    async def test_parse_reasoning_step_valid(self, orchestrator):
        """Test parsing valid reasoning step"""
        content = """
Thought: I need to check the logs for errors.
Action: search_logs
Action Input: {"service_id": "test", "keyword": "error"}
        """

        step = await orchestrator._parse_reasoning(content)

        assert step["thought"] == "I need to check the logs for errors."
        assert step["action"] == "search_logs"
        assert "service_id" in step["actionInput"]

    @pytest.mark.asyncio
    async def test_parse_reasoning_no_action(self, orchestrator):
        """Test parsing reasoning without action"""
        content = "I need to check the logs for errors."

        step = await orchestrator._parse_reasoning(content)

        assert step["thought"] == "I need to check the logs for errors."
        assert step["action"] == "conclude"


class TestReasoningStep:
    """Test ReasoningStep dataclass"""

    def test_reasoning_step_creation(self):
        """Test creating a reasoning step"""
        step = ReasoningStep(
            step=1,
            thought="Check logs",
            action="search_logs",
            action_input={"service_id": "test"},
            observation="Found errors",
            timestamp=datetime.now()
        )

        assert step.step == 1
        assert step.thought == "Check logs"
        assert step.action == "search_logs"

    def test_reasoning_step_to_dict(self):
        """Test converting reasoning step to dict"""
        step = ReasoningStep(
            step=1,
            thought="Check logs",
            action="search_logs",
            action_input={"service_id": "test"},
            observation="Found errors",
            timestamp=datetime.now()
        )

        result = step.to_dict()
        assert result["step"] == 1
        assert result["thought"] == "Check logs"


class TestOrchestratorState:
    """Test orchestrator state management"""

    def test_get_orchestrator_singleton(self):
        """Test orchestrator is singleton"""
        orch1 = get_orchestrator()
        orch2 = get_orchestrator()
        assert orch1 is orch2


class TestToolExecution:
    """Test tool execution in orchestrator"""

    @pytest.mark.asyncio
    async def test_execute_valid_tool(self, orchestrator):
        """Test executing a valid tool"""
        # Mock the tool
        mock_tool = AsyncMock(return_value={"total": 10, "logs": []})
        tool_registry.tools = {"test_tool": mock_tool}

        result = await orchestrator._execute_action("test_tool", {})

        assert result is not None

    @pytest.mark.asyncio
    async def test_execute_invalid_tool(self, orchestrator):
        """Test executing non-existent tool"""
        result = await orchestrator._execute_action("invalid_tool", {})

        assert "error" in result
        assert "Tool not found" in result["error"]

    @pytest.mark.asyncio
    async def test_execute_tool_with_error(self, orchestrator):
        """Test tool that throws exception"""
        mock_tool = AsyncMock(side_effect=Exception("Tool failed"))
        tool_registry.tools = {"failing_tool": mock_tool}

        result = await orchestrator._execute_action("failing_tool", {})

        assert "error" in result
        assert "Tool execution failed" in result["error"]
