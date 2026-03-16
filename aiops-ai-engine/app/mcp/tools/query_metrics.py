"""Query metrics tool - Real implementation with Prometheus"""

from typing import Dict, Any, Optional
from datetime import datetime
import structlog

from app.mcp.registry import tool_registry
from app.infrastructure.prometheus import PrometheusClient

logger = structlog.get_logger()

# Prometheus client instance
prom_client = PrometheusClient()


def build_promql(
    metric_name: str,
    service_id: Optional[str] = None,
    instance: Optional[str] = None,
    labels: Optional[Dict[str, str]] = None
) -> str:
    """Build PromQL query with label selectors"""
    selectors = []

    if service_id:
        selectors.append(f'service="{service_id}"')
    if instance:
        selectors.append(f'instance="{instance}"')
    if labels:
        for key, value in labels.items():
            selectors.append(f'{key}="{value}"')

    if selectors:
        return f'{metric_name}{{{" ,".join(selectors)}}}'
    return metric_name


@tool_registry.register(
    name="query_metrics",
    description="Query metrics from Prometheus using PromQL or metric name",
    parameters={
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "PromQL query OR metric name, e.g., 'cpu_usage' or 'rate(http_requests_total[5m])'"
            },
            "service_id": {
                "type": "string",
                "description": "Service ID for label filtering"
            },
            "instance": {
                "type": "string",
                "description": "Instance identifier"
            },
            "time_range": {
                "type": "object",
                "properties": {
                    "start": {"type": "string", "description": "ISO format start time"},
                    "end": {"type": "string", "description": "ISO format end time"},
                    "step": {"type": "string", "default": "1m", "description": "Step interval, e.g., '1m', '5m'"}
                }
            },
            "aggregation": {
                "type": "string",
                "enum": ["avg", "sum", "max", "min", "count"],
                "description": "Aggregation function for instant query"
            }
        },
        "required": ["query"]
    }
)
async def query_metrics(
    query: str,
    service_id: Optional[str] = None,
    instance: Optional[str] = None,
    time_range: Optional[Dict] = None,
    aggregation: Optional[str] = None
) -> Dict[str, Any]:
    """Query metrics from Prometheus with real implementation"""

    start_time = datetime.utcnow()

    try:
        # Build PromQL if simple metric name provided
        promql = query
        if not any(c in query for c in ['{', '(', '[', '+', '-', '*', '/']):
            # Looks like a simple metric name, add label selectors
            promql = build_promql(query, service_id, instance)
        elif service_id or instance:
            # Modify existing query to add labels (wrap with aggregation)
            label_matchers = []
            if service_id:
                label_matchers.append(f'service="{service_id}"')
            if instance:
                label_matchers.append(f'instance="{instance}"')
            if label_matchers:
                # Add label filtering via aggregation
                promql = f'{aggregation or "avg"}({query}) by (service, instance)'

        logger.info("Querying metrics",
                   promql=promql,
                   service=service_id,
                   instance=instance)

        # Parse time range
        start = None
        end = None
        step = "1m"
        if time_range:
            start = time_range.get('start')
            end = time_range.get('end')
            step = time_range.get('step', '1m')

        # Execute query
        if start and end:
            # Range query
            result = await prom_client.query_range(promql, start, end, step)
        else:
            # Instant query
            result = await prom_client.query(promql)

        if result.get('status') == 'error':
            logger.error("Prometheus query failed", error=result.get('error'))
            return {
                "status": "error",
                "error": result.get('error'),
                "query": promql,
                "timestamp": datetime.utcnow().isoformat()
            }

        # Parse result
        data = result.get('data', {})
        result_type = data.get('resultType')
        result_data = data.get('result', [])

        # Format data points
        formatted_data = []
        summary = {"avg": 0, "max": float('-inf'), "min": float('inf'), "count": 0}
        all_values = []

        for series in result_data:
            metric = series.get('metric', {})
            values = series.get('values', [])

            if not values and 'value' in series:
                # Instant query result
                values = [series['value']]

            for value in values:
                if isinstance(value, list) and len(value) == 2:
                    timestamp, val = value
                    val_float = float(val) if val else 0.0
                    formatted_data.append({
                        "timestamp": datetime.fromtimestamp(float(timestamp)).isoformat(),
                        "value": val_float
                    })
                    all_values.append(val_float)
                elif isinstance(value, str):
                    val_float = float(value) if value else 0.0
                    formatted_data.append({"value": val_float})
                    all_values.append(val_float)

        # Calculate summary statistics
        if all_values:
            summary = {
                "avg": round(sum(all_values) / len(all_values), 2),
                "max": round(max(all_values), 2),
                "min": round(min(all_values), 2),
                "current": round(all_values[-1], 2) if all_values else 0,
                "count": len(all_values)
            }
        else:
            summary = {"avg": 0, "max": 0, "min": 0, "current": 0, "count": 0}

        elapsed_ms = (datetime.utcnow() - start_time).total_seconds() * 1000

        return {
            "status": "success",
            "query": promql,
            "service_id": service_id,
            "instance": instance,
            "result_type": result_type,
            "data": formatted_data[:1000],  # Limit results
            "summary": summary,
            "series_count": len(result_data),
            "timestamp": datetime.utcnow().isoformat(),
            "elapsed_ms": round(elapsed_ms, 2)
        }

    except Exception as e:
        logger.error("Metrics query failed", error=str(e))
        return {
            "status": "error",
            "error": str(e),
            "query": query,
            "timestamp": datetime.utcnow().isoformat()
        }
