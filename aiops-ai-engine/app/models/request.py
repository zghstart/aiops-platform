"""Request/Response models"""

from typing import Dict, List, Any, Optional
from pydantic import BaseModel, Field
from datetime import datetime


class AnalyzeRequest(BaseModel):
    """Analysis request"""
    incident_id: str = Field(..., description="Incident ID")
    tenant_id: str = Field(..., description="Tenant ID")
    service_id: str = Field(..., description="Service ID")
    instance: Optional[str] = Field(None, description="Instance identifier")
    alert_ids: List[str] = Field(default_factory=list, description="Related alert IDs")
    time_range: Dict[str, str] = Field(..., description="Time range {start, end}")
    context: Dict[str, Any] = Field(default_factory=dict, description="Additional context")
    options: Dict[str, Any] = Field(default_factory=dict, description="Analysis options")


class AnalyzeResponse(BaseModel):
    """Analysis response"""
    incident_id: str
    status: str
    confidence: Optional[float] = None
    root_cause: Optional[str] = None
    evidence: List[str] = Field(default_factory=list)
    recommendations: List[str] = Field(default_factory=list)
    reasoning_chain: List[Dict[str, Any]] = Field(default_factory=list)
    tokens_used: Optional[int] = None
    analysis_time_sec: Optional[float] = None
    completed_at: Optional[datetime] = None

    @classmethod
    def from_result(cls, result) -> "AnalyzeResponse":
        if result is None:
            return cls(incident_id="", status="failed")

        return cls(
            incident_id=result.incident_id,
            status="completed" if hasattr(result, 'root_cause') and result.root_cause else "failed",
            confidence=getattr(result, 'confidence', None),
            root_cause=getattr(result, 'root_cause', None),
            evidence=getattr(result, 'evidence', []),
            recommendations=getattr(result, 'recommendations', []),
            reasoning_chain=[step.__dict__ if hasattr(step, '__dict__') else step
                            for step in getattr(result, 'reasoning_chain', [])],
            tokens_used=getattr(result, 'tokens_used', None),
            analysis_time_sec=getattr(result, 'analysis_time_sec', None),
            completed_at=getattr(result, 'completed_at', None)
        )


class FeedbackRequest(BaseModel):
    """Feedback request"""
    rating: int = Field(..., ge=1, le=5, description="Rating 1-5")
    correct_root_cause: Optional[str] = Field(None, description="Correct root cause if different")
    comments: Optional[str] = Field(None, description="Additional comments")
	metadata: Optional[Dict[str, Any]] = Field(default_factory=dict, description="Additional metadata")


class ToolCall(BaseModel):
    """Tool call"""
    tool_name: str
    params: Dict[str, Any]
    result: Optional[Any] = None
    error: Optional[str] = None


class StreamEvents(BaseModel):
    """Stream events"""
    type: str
    data: Dict[str, Any]
    timestamp: datetime = Field(default_factory=datetime.utcnow)
