"""Tenant middleware"""

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware
import structlog

logger = structlog.get_logger()


class TenantMiddleware(BaseHTTPMiddleware):
    """Tenant context middleware"""

    async def dispatch(self, request: Request, call_next):
        # Extract tenant from header or query
        tenant_id = request.headers.get("X-Tenant-ID")
        if not tenant_id:
            tenant_id = request.query_params.get("tenant_id")
        if not tenant_id:
            tenant_id = "default"

        # Store in request state
        request.state.tenant_id = tenant_id
        request.state.tenant_context = {"tenant_id": tenant_id}

        # Add to logger context
        logger.bind(tenant_id=tenant_id)

        response = await call_next(request)
        response.headers["X-Tenant-ID"] = tenant_id
        return response
