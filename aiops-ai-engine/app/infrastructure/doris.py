"""Doris client"""

import aiomysql
from typing import List, Dict, Any, Optional
import structlog
from app.config import settings

logger = structlog.get_logger()


class DorisClient:
    """Doris database client"""

    def __init__(self):
        self.pool = None
        self.host = settings.DORIS_HOST
        self.port = settings.DORIS_PORT
        self.database = settings.DORIS_DATABASE
        self.user = settings.DORIS_USER
        self.password = settings.DORIS_PASSWORD
        self.logger = logger.bind(component="DorisClient")

    async def _get_pool(self):
        """Get connection pool"""
        if self.pool is None:
            self.pool = await aiomysql.create_pool(
                host=self.host,
                port=self.port,
                user=self.user,
                password=self.password,
                db=self.database,
                minsize=1,
                maxsize=10,
                autocommit=True
            )
        return self.pool

    async def query(self, sql: str, params: Optional[tuple] = None) -> List[Dict[str, Any]]:
        """Execute query"""
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            async with conn.cursor(aiomysql.DictCursor) as cur:
                self.logger.debug("Executing query", sql=sql[:100])
                await cur.execute(sql, params)
                return await cur.fetchall()

    async def execute(self, sql: str, params: Optional[tuple] = None) -> int:
        """Execute statement"""
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(sql, params)
                return cur.rowcount

    async def close(self):
        """Close pool"""
        if self.pool:
            self.pool.close()
            await self.pool.wait_closed()
