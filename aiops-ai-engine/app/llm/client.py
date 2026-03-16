"""LLM Client wrapper"""

from typing import List, Dict, Any, Optional
from app.config import settings
from app.llm.glm5 import GLM5Adapter
import structlog

logger = structlog.get_logger()


class LLMClient:
    """LLM Client wrapper"""

    def __init__(self):
        self.adapter = GLM5Adapter(
            base_url=settings.LLM_BASE_URL,
            api_key=settings.LLM_API_KEY if settings.LLM_API_KEY else None
        )
        self.model = settings.LLM_MODEL
        self.temperature = settings.LLM_TEMPERATURE
        self.max_tokens = settings.LLM_MAX_TOKENS
        self.logger = logger.bind(component="LLMClient")

    async def chat(
        self,
        messages: List[Dict[str, str]],
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        """Simple chat completion"""

        try:
            response = await self.adapter.chat_completions(
                model=self.model,
                messages=messages,
                temperature=temperature or self.temperature,
                max_tokens=max_tokens or self.max_tokens
            )

            content = response.get("choices", [{}])[0].get("message", {}).get("content", "")
            return content

        except Exception as e:
            self.logger.error("Chat failed", error=str(e))
            return self._fallback_response()

    async def chat_with_tools(
        self,
        messages: List[Dict[str, str]],
        tools: List[Dict]
    ) -> Dict[str, Any]:
        """Chat with function calling"""

        try:
            response = await self.adapter.chat_completions(
                model=self.model,
                messages=messages,
                temperature=self.temperature,
                max_tokens=self.max_tokens,
                tools=tools
            )

            message = response.get("choices", [{}])[0].get("message", {})
            return message

        except Exception as e:
            self.logger.error("Chat with tools failed", error=str(e))
            return {"content": self._fallback_response()}

    def _fallback_response(self) -> str:
        """Fallback response when LLM fails"""
        return json.dumps({
            "thought": "LLM service is currently unavailable. Please check your configuration or try again later.",
            "action": "conclude",
            "conclusion": {
                "root_cause": "Unable to analyze - LLM service unavailable",
                "confidence": 0.0,
                "evidence": ["LLM API call failed"],
                "recommendations": ["Check LLM service status", "Verify API configuration"]
            }
        })

    async def close(self):
        """Close the client"""
        await self.adapter.close()
