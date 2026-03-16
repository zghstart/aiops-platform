"""Analysis result models"""

from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any
from datetime import datetime


@dataclass
class ReasoningStep:
    """Single reasoning step"""
    step: int
    thought: str
    action: str
    action_input: str
    observation: str
    timestamp: datetime


@dataclass
class AnalysisResult:
    """AI analysis result"""
    incident_id: str
    confidence: float
    root_cause: str
    evidence: List[str] = field(default_factory=list)
    recommendations: List[str] = field(default_factory=list)
    reasoning_chain: List[ReasoningStep] = field(default_factory=list)
    tokens_used: Optional[int] = None
    analysis_time_sec: Optional[float] = None
    completed_at: Optional[datetime] = None
    tenant_id: Optional[str] = None
    service_id: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary"""
        return {
            "incident_id": self.incident_id,
            "confidence": self.confidence,
            "root_cause": self.root_cause,
            "evidence": self.evidence,
            "recommendations": self.recommendations,
            "reasoning_chain": [self._step_to_dict(step) for step in self.reasoning_chain],
            "tokens_used": self.tokens_used,
            "analysis_time_sec": self.analysis_time_sec,
            "completed_at": self.completed_at.isoformat() if self.completed_at else None,
            "tenant_id": self.tenant_id,
            "service_id": self.service_id
        }

    def _step_to_dict(self, step) -> Dict:
        if hasattr(step, '__dict__'):
            return {
                "step": step.step,
                "thought": step.thought,
                "action": step.action,
                "action_input": step.action_input,
                "observation": step.observation,
                "timestamp": step.timestamp.isoformat() if step.timestamp else None
            }
        return step
