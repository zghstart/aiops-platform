"""Tool Executor"""

from typing import Any, Dict
import structlog

from app.mcp.registry import tool_registry
from app.infrastructure.doris import DorisClient
from app.infrastructure.prometheus import PrometheusClient
from app.infrastructure.redis import RedisCache

logger = structlog.get_logger()


class ToolExecutor:
    """Tool executor with dependency injection"""

    def __init__(self):
        self.doris_client: DorisClient = None
        self.prometheus_client: PrometheusClient = None
        self.cache: RedisCache = None
        self.logger = logger.bind(component="ToolExecutor")

    async def execute(self, tool_name: str, params: Dict[str, Any]) -> Any:
        """Execute a tool"""
        tool = tool_registry.get(tool_name)
        if not tool:
            raise ValueError(f"Unknown tool: {tool_name}")

        self.logger.info("Executing tool", name=tool_name, params=params)

        # Inject dependencies
        if 'doris' in params:
            params = {**params, 'doris': self._get_doris()}
        if 'prometheus' in params:
            params = {**params, 'prometheus': self._get_prometheus()}
        if 'cache' in params:
            params = {**params, 'cache': self._get_cache()}

        try:
            result = await tool.handler(**params) if hasattr(tool.handler, '__call__') else tool.handler(**params)
            self.logger.info("Tool executed successfully", name=tool_name)
            return result
        except Exception as e:
            self.logger.error("Tool execution failed", name=tool_name, error=str(e))
            raise

    def _get_doris(self) -> DorisClient:
        if self.Doris_client is None:
            self.Doris_client = DorisClient()
        return self.Doris_client

    def _get_prometheus(self) -> PrometheusClient:
        if self.prometheus_client is None:
            self.prometheus_client = PrometheusClient()
        return self.prometheus_client

    def _get_cache(self) -> RedisCache:
        if self.cache is None:
            self.cache = RedisCache()
        return self.cache
