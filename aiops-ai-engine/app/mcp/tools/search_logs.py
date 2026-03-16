"""Search logs tool - Real implementation with Doris"""

from typing import Dict, Any, Optional
from datetime import datetime
import structlog

from app.mcp.registry import tool_registry
from app.infrastructure.doris import DorisClient

logger = structlog.get_logger()

# Doris client instance
doris_client = DorisClient()


@tool_registry.register(
    name="search_logs",
    description="Search logs from Doris, supports keyword matching and log level filtering",
    parameters={
        "type": "object",
        "properties": {
            "service_id": {
                "type": "string",
                "description": "Service ID, e.g., 'payment-service'"
            },
            "level": {
                "type": "string",
                "enum": ["ERROR", "WARN", "INFO", "DEBUG"],
                "description": "Log level"
            },
            "time_range": {
                "type": "object",
                "properties": {
                    "start": {"type": "string", "description": "ISO format start time"},
                    "end": {"type": "string", "description": "ISO format end time"}
                },
                "required": ["start", "end"]
            },
            "keyword": {
                "type": "string",
                "description": "Keyword search, e.g., 'connection pool'"
            },
            "trace_id": {
                "type": "string",
                "description": "Filter by Trace ID"
            },
            "limit": {
                "type": "integer",
                "default": 100,
                "description": "Number of results, default 100"
            }
        },
        "required": ["time_range"]
    }
)
async def search_logs(
    time_range: Dict[str, str],
    service_id: Optional[str] = None,
    level: Optional[str] = None,
    keyword: Optional[str] = None,
    trace_id: Optional[str] = None,
    limit: int = 100
) -> Dict[str, Any]:
    """Search logs from Doris with real query"""

    start_time = datetime.utcnow()

    try:
        # Build WHERE clauses
        conditions = [
            f"timestamp >= '{time_range['start']}'",
            f"timestamp <= '{time_range['end']}'"
        ]

        if service_id:
            conditions.append(f"service_id = '{escape_sql(service_id)}'")
        if level:
            conditions.append(f"level = '{level}'")
        if keyword:
            # Use full-text search if available, otherwise LIKE
            conditions.append(f"(message LIKE '%{escape_sql(keyword)}%' OR log_content LIKE '%{escape_sql(keyword)}%')")
        if trace_id:
            conditions.append(f"trace_id = '{escape_sql(trace_id)}'")

        where_clause = " AND ".join(conditions)

        # Execute log query
        sql = f"""
        SELECT
            timestamp,
            service_id,
            level,
            message,
            trace_id,
            host_ip,
            pod_name,
            log_content
        FROM logs
        WHERE {where_clause}
        ORDER BY timestamp DESC
        LIMIT {min(limit, 1000)}
        """

        logger.info("Searching logs",
                   service=service_id,
                   keyword=keyword,
                   level=level,
                   time_start=time_range['start'],
                   time_end=time_range['end'])

        rows = await doris_client.query(sql)

        # Get summary statistics
        summary_sql = f"""
        SELECT
            level,
            COUNT(*) as count
        FROM logs
        WHERE {where_clause}
        GROUP BY level
        ORDER BY count DESC
        """

        summary_rows = await doris_client.query(summary_sql)

        # Get top errors
        top_errors_sql = f"""
        SELECT
            message,
            COUNT(*) as count
        FROM logs
        WHERE {where_clause}
            AND level = 'ERROR'
        GROUP BY message
        ORDER BY count DESC
        LIMIT 5
        """

        top_errors = await doris_client.query(top_errors_sql)

        elapsed_ms = (datetime.utcnow() - start_time).total_seconds() * 1000

        # Format results
        level_counts = {row['level']: row['count'] for row in summary_rows}

        return {
            "total": len(rows),
            "logs": rows,
            "summary": {
                "error_count": level_counts.get('ERROR', 0),
                "warn_count": level_counts.get('WARN', 0),
                "info_count": level_counts.get('INFO', 0),
                "debug_count": level_counts.get('DEBUG', 0),
                "top_errors": [{"message": r['message'], "count": r['count']} for r in top_errors]
            },
            "query_meta": {
                "time_range": time_range,
                "filters": {
                    "service_id": service_id,
                    "level": level,
                    "keyword": keyword,
                    "trace_id": trace_id
                },
                "elapsed_ms": round(elapsed_ms, 2)
            }
        }

    except Exception as e:
        logger.error("Log search failed", error=str(e))
        # Fallback to mock data on error
        return {
            "total": 0,
            "logs": [],
            "summary": {
                "error_count": 0,
                "warn_count": 0,
                "info_count": 0,
                "debug_count": 0,
                "top_errors": []
            },
            "error": str(e),
            "query_meta": {
                "time_range": time_range,
                "elapsed_ms": 0
            }
        }


def escape_sql(value: str) -> str:
    """Simple SQL escape for string values"""
    return value.replace("'", "''").replace("\\", "\\\\")
