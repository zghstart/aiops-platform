"""Prometheus client"""

import httpx
from typing import Dict, Any, Optional
import structlog
from app.config import settings

logger = structlog.get_logger()


class PrometheusClient:
    """Prometheus API client"""

    def __init__(self):
        self.base_url = settings.PROMETHEUS_URL.rstrip('/')
        self.timeout = settings.PROMETHEUS_TIMEOUT
        self.client = httpx.AsyncClient(timeout=self.timeout)
        self.logger = logger.bind(component="PrometheusClient")

    async def query(self, promql: str, time: Optional[str] = None) -> Dict[str, Any]:
        """Query Prometheus"""
        url = f"{self.base_url}/api/v1/query"
        params = {"query": promql}
        if time:
            params["time"] = time

        self.logger.debug("Querying Prometheus", query=promql[:100])

        try:
            response = await self.client.get(url, params=params)
            response.raise_for_status()
            return response.json()
        except httpx.HTTPError as e:
            self.logger.error("Prometheus query failed", error=str(e))
            return {"status": "error", "error": str(e)}

    async def query_range(self, promql: str, start: str, end: str, step: str) -> Dict[str, Any]:
        """Query range"""
        url = f"{self.base_url}/api/v1/query_range"
        params = {
            "query": promql,
            "start": start,
            "end": end,
            "step": step
        }

        try:
            response = await self.client.get(url, params=params)
            response.raise_for_status()
            return response.json()
        except httpx.HTTPError as e:
            self.logger.error("Prometheus range query failed", error=str(e))
            return {"status": "error", "error": str(e)}

    async def close(self):
        """Close client"""
        await self.client.aclose()
