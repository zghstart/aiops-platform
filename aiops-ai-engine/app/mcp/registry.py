"""MCP Tool Registry"""

from typing import Dict, Callable, List, Any, Optional
from dataclasses import dataclass
from functools import wraps
import inspect
import structlog

logger = structlog.get_logger()


@dataclass
class Tool:
    """Tool definition"""
    name: str
    description: str
    parameters: Dict[str, Any]
    handler: Callable


class ToolRegistry:
    """Tool registry"""

    def __init__(self):
        self.tools: Dict[str, Tool] = {}
        self.logger = logger.bind(component="ToolRegistry")

    def register(
        self,
        name: str,
        description: str,
        parameters: Dict
    ):
        """Register a tool"""
        def decorator(func: Callable):
            self.tools[name] = Tool(
                name=name,
                description=description,
                parameters=parameters,
                handler=func
            )
            self.logger.info("Tool registered", name=name)
            return func
        return decorator

    def get(self, name: str) -> Optional[Tool]:
        """Get tool by name"""
        return self.tools.get(name)

    def list_tools(self) -> List[Dict]:
        """List all tools"""
        return [
            {
                "name": t.name,
                "description": t.description,
                "parameters": t.parameters
            }
            for t in self.tools.values()
        ]

    def call(self, name: str, **kwargs) -> Any:
        """Call a tool"""
        tool = self.get(name)
        if not tool:
            raise ValueError(f"Tool not found: {name}")
        return tool.handler(**kwargs)


# Global registry instance
tool_registry = ToolRegistry()
