"""Exceptions"""

class AIOpsException(Exception):
    """Base exception"""
    pass


class ToolExecutionError(AIOpsException):
    """Tool execution error"""
    pass


class AnalysisTimeoutError(AIOpsException):
    """Analysis timeout"""
    pass


class LLMError(AIOpsException):
    """LLM error"""
    pass
