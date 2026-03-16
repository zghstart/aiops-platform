"""Stream events"""

import json
from dataclasses import dataclass, asdict
from typing import Optional
from datetime import datetime


@dataclass
class StreamEvent:
    """SSE event"""
    type: str
    incident_id: str
    data: dict
    timestamp: str = None

    def __post_init__(self):
        if self.timestamp is None:
            self.timestamp = datetime.utcnow().isoformat()

    def to_json(self) -> str:
        return json.dumps(asdict(self), ensure_ascii=False)

    @classmethod
    def start(cls, incident_id: str) -> "StreamEvent":
        return cls("start", incident_id, {"status": "started"})

    @classmethod
    def reasoning(cls, incident_id: str, round: int, thought: str) -> "StreamEvent":
        return cls("reasoning", incident_id, {
            "round": round,
            "thought": thought
        })

    @classmethod
    def tool_call(cls, incident_id: str, round: int, action: str, status: str) -> "StreamEvent":
        return cls("tool_call", incident_id, {
            "round": round,
            "action": action,
            "status": status
        })

    @classmethod
    def tool_result(cls, incident_id: str, round: int, action: str,
                    status: str, result_summary: str) -> "StreamEvent":
        return cls("tool_result", incident_id, {
            "round": round,
            "action": action,
            "status": status,
            "result_summary": result_summary
        })

    @classmethod
    def conclusion(cls, incident_id: str, conclusion: dict) -> "StreamEvent":
        return cls("conclusion", incident_id, conclusion)

    @classmethod
    def complete(cls, incident_id: str, total_rounds: int) -> "StreamEvent":
        return cls("complete", incident_id, {"total_rounds": total_rounds})

    @classmethod
    def timeout(cls, incident_id: str, message: str) -> "StreamEvent":
        return cls("timeout", incident_id, {"message": message})

    @classmethod
    def error(cls, incident_id: str, error: str) -> "StreamEvent":
        return cls("error", incident_id, {"error": error})

    @classmethod
    def token_warning(cls, incident_id: str, used: int, limit: int) -> "StreamEvent":
        return cls("token_warning", incident_id, {
            "tokens_used": used,
            "tokens_limit": limit,
            "usage_percent": round(used / limit * 100, 1)
        })
