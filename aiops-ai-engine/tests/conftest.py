"""Pytest configuration for AIOps AI Engine tests"""

import pytest
import pytest_asyncio
from unittest.mock import AsyncMock, MagicMock
import sys
import os

# Add app to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))


@pytest.fixture
def mock_redis():
    """Mock Redis client"""
    redis = MagicMock()
    redis.get = AsyncMock(return_value=None)
    redis.set = AsyncMock(return_value=True)
    redis.setex = AsyncMock(return_value=True)
    return redis


@pytest.fixture
def mock_doris():
    """Mock Doris client"""
    doris = MagicMock()
    doris.query = AsyncMock(return_value=[])
    doris.execute = AsyncMock(return_value=0)
    return doris


@pytest.fixture
def mock_prometheus():
    """Mock Prometheus client"""
    prom = MagicMock()
    prom.query = AsyncMock(return_value={
        "status": "success",
        "data": {"result": [{"value": [1234567890, "42.5"]}]}
    })
    prom.query_range = AsyncMock(return_value={
        "status": "success",
        "data": {"result": [{"values": [[1234567890, "42.5"], [1234567900, "43.0"]]}]}
    })
    return prom


@pytest.fixture
def sample_alert():
    """Sample alert data"""
    return {
        "alertId": "alert-001",
        "incidentId": "inc-001",
        "title": "High CPU Usage",
        "description": "CPU usage exceeded 90%",
        "serviceId": "payment-service",
        "severity": "P1",
        "status": "active",
        "source": "prometheus",
        "labels": {"instance": "10.0.0.1", "env": "production"}
    }


@pytest.fixture
def sample_reasoning_chain():
    """Sample reasoning chain for ReAct"""
    return [
        {
            "step": 1,
            "thought": "The alert indicates high CPU usage. I need to check logs and metrics.",
            "action": "search_logs",
            "actionInput": {"service_id": "payment-service", "keyword": "cpu"},
            "observation": "Found 15 error logs in the last 10 minutes"
        },
        {
            "step": 2,
            "thought": "The error logs suggest a connection pool issue. Let me check metrics.",
            "action": "query_metrics",
            "actionInput": {"query": "connection_pool_active"},
            "observation": "Connection pool at 95% capacity"
        }
    ]
