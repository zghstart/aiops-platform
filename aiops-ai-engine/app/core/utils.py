"""Core utilities"""

import json
from datetime import datetime
from typing import Any


class DateTimeEncoder(json.JSONEncoder):
    """JSON encoder for datetime"""

    def default(self, obj: Any) -> Any:
        if isinstance(obj, datetime):
            return obj.isoformat()
        return super().default(obj)


def to_json(data: Any) -> str:
    """Convert to JSON with datetime support"""
    return json.dumps(data, cls=DateTimeEncoder, ensure_ascii=False)


def parse_time(time_str: str) -> datetime:
    """Parse ISO time string"""
    return datetime.fromisoformat(time_str.replace('Z', '+00:00'))


def truncated_string(s: str, max_len: int = 100) -> str:
    """Truncate string"""
    if len(s) > max_len:
        return s[:max_len] + "..."
    return s
