"""Redis cache client"""

import json
from typing import Optional, Any
import redis.asyncio as redis
import structlog
from app.config import settings

logger = structlog.get_logger()


class RedisCache:
    """Redis cache client"""

    def __init__(self):
        self.redis: Optional[redis.Redis] = None
        self.host = settings.REDIS_HOST
        self.port = settings.REDIS_PORT
        self.db = settings.REDIS_DB
        self.password = settings.REDIS_PASSWORD or None
        self.logger = logger.bind(component="RedisCache")

    async def _get_redis(self) -> redis.Redis:
        """Get redis client"""
        if self.redis is None:
            self.redis = redis.Redis(
                host=self.host,
                port=self.port,
                db=self.db,
                password=self.password,
                decode_responses=True
            )
        return self.redis

    async def get(self, key: str) -> Optional[str]:
        """Get value"""
        try:
            r = await self._get_redis()
            return await r.get(key)
        except Exception as e:
            self.logger.error("Redis get failed", error=str(e), key=key)
            return None

    async def set(self, key: str, value: str, ttl: Optional[int] = None) -> bool:
        """Set value"""
        try:
            r = await self._get_redis()
            return await r.set(key, value, ex=ttl)
        except Exception as e:
            self.logger.error("Redis set failed", error=str(e), key=key)
            return False

    async def setex(self, key: str, seconds: int, value: str) -> bool:
        """Set with expiry"""
        try:
            r = await self._get_redis()
            return await r.setex(key, seconds, value)
        except Exception as e:
            self.logger.error("Redis setex failed", error=str(e), key=key)
            return False

    async def delete(self, key: str) -> int:
        """Delete key"""
        try:
            r = await self._get_redis()
            return await r.delete(key)
        except Exception as e:
            self.logger.error("Redis delete failed", error=str(e), key=key)
            return 0

    async def close(self):
        """Close connection"""
        if self.redis:
            await self.redis.close()
