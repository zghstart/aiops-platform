"""LLM Client wrapper with retry logic"""

import asyncio
import json
from typing import List, Dict, Any, Optional
from functools import wraps
from tenacity import retry, wait_exponential, stop_after_attempt, retry_if_exception_type
from app.config import settings
from app.llm.glm5 import GLM5Adapter
import structlog

logger = structlog.get_logger()


def retry_llm(max_attempts=3, base_delay=1):
    """Retry decorator for LLM calls with exponential backoff"""
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            last_exception = None
            for attempt in range(1, max_attempts + 1):
                try:
                    return await func(*args, **kwargs)
                except (ConnectionError, TimeoutError) as e:
                    last_exception = e
                    if attempt < max_attempts:
                        delay = base_delay * (2 ** (attempt - 1))
                        logger.warning(f"LLM call failed, retrying in {delay}s (attempt {attempt}/{max_attempts})")
                        await asyncio.sleep(delay)
                    else:
                        logger.error(f"LLM call failed after {max_attempts} attempts: {e}")
                        raise
                except Exception as e:
                    raise
            if last_exception:
                raise last_exception
        return wrapper
    return decorator


class LLMClient:
    """LLM Client wrapper with retry and failover"""

    def __init__(self):
        self.adapter = GLM5Adapter(
            base_url=settings.LLM_BASE_URL,
            api_key=settings.LLM_API_KEY if settings.LLM_API_KEY else None
        )
        self.model = settings.LLM_MODEL
        self.temperature = settings.LLM_TEMPERATURE
        self.max_tokens = settings.LLM_MAX_TOKENS
        self.logger = logger.bind(component="LLMClient")
        self._circuit_breaker_failures = 0
        self._circuit_breaker_threshold = 5
        self._circuit_breaker_timeout = 60  # seconds
        self._circuit_breaker_last_failure = None

    def _is_circuit_open(self) -> bool:
        """Check if circuit breaker is open"""
        if self._circuit_breaker_failures < self._circuit_breaker_threshold:
            return False
        import time
        time_since_last = time.time() - self._circuit_breaker_last_failure
        if time_since_last < self._circuit_breaker_timeout:
            return True
        # Circuit timeout, reset
        self._circuit_breaker_failures = 0
        return False

    def _record_failure(self):
        """Record a failure for circuit breaker"""
        self._circuit_breaker_failures += 1
        import time
        self._circuit_breaker_last_failure = time.time()

    def _record_success(self):
        """Record a success, reset circuit breaker"""
        self._circuit_breaker_failures = 0
        self._circuit_breaker_last_failure = None

    @retry_llm(max_attempts=3, base_delay=1)
    async def chat(
        self,
        messages: List[Dict[str, str]],
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        """Simple chat completion with retry"""

        if self._is_circuit_open():
            self.logger.warning("Circuit breaker open, using fallback")
            return self._fallback_response()

        try:
            response = await self.adapter.chat_completions(
                model=self.model,
                messages=messages,
                temperature=temperature or self.temperature,
                max_tokens=max_tokens or self.max_tokens
            )

            content = response.get("choices", [{}])[0].get("message", {}).get("content", "")
            self._record_success()
            return content

        except Exception as e:
            self._record_failure()
            self.logger.error("Chat failed", error=str(e))
            # Return fallback response instead of raising
            return self._fallback_response()

    @retry_llm(max_attempts=3, base_delay=1)
    async def chat_with_tools(
        self,
        messages: List[Dict[str, str]],
        tools: List[Dict]
    ) -> Dict[str, Any]:
        """Chat with function calling and retry"""

        if self._is_circuit_open():
            self.logger.warning("Circuit breaker open, using fallback")
            return {"content": self._fallback_response()}

        try:
            response = await self.adapter.chat_completions(
                model=self.model,
                messages=messages,
                temperature=self.temperature,
                max_tokens=self.max_tokens,
                tools=tools
            )

            message = response.get("choices", [{}])[0].get("message", {})
            self._record_success()
            return message

        except Exception as e:
            self._record_failure()
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
                "evidence": ["LLM API call failed or circuit breaker open"],
                "recommendations": ["Check LLM service status", "Verify API configuration", "Check circuit breaker state"]
            }
        })

    async def close(self):
        """Close the client"""
        await self.adapter.close()
