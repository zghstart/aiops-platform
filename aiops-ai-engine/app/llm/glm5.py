"""GLM5 vLLM/SGLang adapter"""

import httpx
from typing import AsyncGenerator, List, Dict, Any, Optional
import json
import structlog

logger = structlog.get_logger()


class GLM5Adapter:
    """GLM5 vLLM/SGLang adapter"""

    def __init__(self, base_url: str, api_key: Optional[str] = None):
        self.base_url = base_url.rstrip('/')
        self.api_key = api_key
        self.client = httpx.AsyncClient(
            timeout=120.0,
            limits=httpx.Limits(max_keepalive_connections=20, max_connections=50)
        )
        self.logger = logger.bind(adapter="GLM5")

    async def chat_completions(
        self,
        model: str,
        messages: List[Dict[str, str]],
        temperature: float = 0.3,
        max_tokens: int = 2048,
        stream: bool = False,
        tools: Optional[List[Dict]] = None
    ) -> Any:
        """Call GLM5 chat interface"""

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

        headers = {}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        self.logger.debug("Calling LLM", model=model, messages_count=len(messages))

        try:
            response = await self.client.post(
                f"{self.base_url}/chat/completions",
                json=payload,
                headers=headers,
                timeout=120.0
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPError as e:
            self.logger.error("LLM HTTP error", error=str(e))
            raise
        except Exception as e:
            self.logger.error("LLM call failed", error=str(e))
            raise

    async def chat_completions_stream(
        self,
        model: str,
        messages: List[Dict[str, str]],
        **kwargs
    ) -> AsyncGenerator[str, None]:
        """Streaming call"""

        payload = {
            "model": model,
            "messages": messages,
            "stream": True,
            **kwargs
        }

        headers = {}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        async with self.client.stream(
            "POST",
            f"{self.base_url}/chat/completions",
            json=payload,
            headers=headers,
            timeout=120.0
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    data = line[6:]
                    if data != "[DONE]":
                        yield data

    async def close(self):
        """Close HTTP client"""
        await self.client.aclose()
