"""API Router"""

from fastapi import APIRouter, Depends, BackgroundTasks
from fastapi.responses import StreamingResponse
from typing import AsyncGenerator
import asyncio

from app.agent.orchestrator import DiagnosticOrchestrator
from app.llm.streaming import StreamEvent
from app.models.request import AnalyzeRequest, AnalyzeResponse, FeedbackRequest
from app.dependency import get_orchestrator

router = APIRouter()


@router.post("/ai/analyze", response_model=AnalyzeResponse)
async def analyze_incident(
    request: AnalyzeRequest,
    orchestrator: DiagnosticOrchestrator = Depends(get_orchestrator)
) -> AnalyzeResponse:
    """Non-streaming diagnostic analysis"""
    result = await orchestrator.analyze(request)
    return AnalyzeResponse.from_result(result)


@router.get("/ai/analyze/stream")
async def analyze_incident_stream(
    incident_id: str,
    orchestrator: DiagnosticOrchestrator = Depends(get_orchestrator)
) -> StreamingResponse:
    """SSE streaming diagnostic analysis"""

    async def event_generator() -> AsyncGenerator[str, None]:
        async for event in orchestrator.analyze_stream(incident_id):
            yield f"event: {event.type}\ndata: {event.to_json()}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.get("/ai/analysis/{analysis_id}")
async def get_analysis_result(
    analysis_id: str,
    orchestrator: DiagnosticOrchestrator = Depends(get_orchestrator)
) -> AnalyzeResponse:
    """Query analysis result"""
    result = await orchestrator.get_result(analysis_id)
    if result is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Analysis not found")
    return AnalyzeResponse.from_result(result)


@router.post("/ai/analysis/{analysis_id}/feedback")
async def submit_feedback(
    analysis_id: str,
    feedback: FeedbackRequest,
    orchestrator: DiagnosticOrchestrator = Depends(get_orchestrator)
):
    """Submit human feedback"""
    await orchestrator.submit_feedback(analysis_id, feedback.model_dump())
    return {"status": "ok"}


@router.get("/tools")
async def list_tools():
    """List available MCP tools"""
    from app.mcp.registry import tool_registry
    return {"tools": tool_registry.list_tools()}
