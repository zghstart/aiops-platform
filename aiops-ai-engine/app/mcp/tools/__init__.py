# Import all tools to register them
try:
    from .tools import search_logs
    from .tools import query_metrics
    from .tools import get_topology
    from .tools import search_cases
except ImportError as e:
    import logging
    logging.getLogger(__name__).debug(f"Some tools not loaded: {e}")
