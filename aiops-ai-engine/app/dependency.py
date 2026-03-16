"""Dependency injection"""

from functools import lru_cache
from app.agent.orchestrator import DiagnosticOrchestrator
from app.llm.client import LLMClient
from app.llm.glm5 import GLM5Adapter
from app.mcp.executor import ToolExecutor
from app.infrastructure.redis import RedisCache


@lru_cache()
def get_llm_client() -> LLMClient:
    """Get LLM client singleton"""
    return LLMClient()


@lru_cache()
def get_glm5_adapter() -> GLM5Adapter:
    """Get GLM5 adapter singleton"""
    from app.config import settings
    return GLM5Adapter(
        base_url=settings.LLM_BASE_URL,
        api_key=settings.LLM_API_KEY
    )


@lru_cache()
def get_redis_cache() -> RedisCache:
    """Get Redis cache singleton"""
    return RedisCache()


@lru_cache()
def get_tool_executor() -> ToolExecutor:
    """Get tool executor singleton"""
    from app.config import settings
    # Import tools to register them
    from app.mcp import tools  # noqa: F401
    return ToolExecutor()


@lru_cache()
def get_orchestrator() -> DiagnosticOrchestrator:
    """Get orchestrator singleton"""
    return DiagnosticOrchestrator(
        llm_client=get_llm_client(),
        glm5_adapter=get_glm5_adapter(),
        tool_executor=get_tool_executor(),
        redis_cache=get_redis_cache()
    )
