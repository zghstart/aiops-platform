"""Configuration management"""

from typing import List
from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    """Application settings"""

    # Server
    HOST: str = Field(default="0.0.0.0", description="Server host")
    PORT: int = Field(default=8000, description="Server port")
    DEBUG: bool = Field(default=False, description="Debug mode")
    WORKERS: int = Field(default=4, description="Number of workers")

    # CORS
    CORS_ORIGINS: List[str] = Field(default=["*"], description="CORS allowed origins")

    # LLM
    LLM_BASE_URL: str = Field(default="http://localhost:8001/v1", description="LLM base URL")
    LLM_API_KEY: str = Field(default="", description="LLM API key")
    LLM_MODEL: str = Field(default="glm5", description="LLM model name")
    LLM_TIMEOUT: int = Field(default=120, description="LLM timeout in seconds")
    LLM_MAX_TOKENS: int = Field(default=4096, description="Max tokens per request")
    LLM_TEMPERATURE: float = Field(default=0.3, description="LLM temperature")

    # Database
    DORIS_HOST: str = Field(default="localhost", description="Doris host")
    DORIS_PORT: int = Field(default=9030, description="Doris port")
    DORIS_DATABASE: str = Field(default="aiops", description="Doris database")
    DORIS_USER: str = Field(default="aiops", description="Doris user")
    DORIS_PASSWORD: str = Field(default="aiops123", description="Doris password")

    # Redis
    REDIS_HOST: str = Field(default="localhost", description="Redis host")
    REDIS_PORT: int = Field(default=6379, description="Redis port")
    REDIS_DB: int = Field(default=0, description="Redis database")
    REDIS_PASSWORD: str = Field(default="", description="Redis password")

    # Prometheus
    PROMETHEUS_URL: str = Field(default="http://localhost:9090", description="Prometheus URL")
    PROMETHEUS_TIMEOUT: int = Field(default=30, description="Prometheus timeout")

    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = Field(default="localhost:9092", description="Kafka bootstrap servers")
    KAFKA_TOPIC_AI_TASKS: str = Field(default="aiops.ai-tasks", description="AI tasks topic")
    KAFKA_TOPIC_AI_RESULTS: str = Field(default="aiops.ai-results", description="AI results topic")
    KAFKA_GROUP_ID: str = Field(default="ai-engine", description="Kafka consumer group ID")

    # Milvus
    MILVUS_HOST: str = Field(default="localhost", description="Milvus host")
    MILVUS_PORT: int = Field(default=19530, description="Milvus port")
    MILVUS_COLLECTION: str = Field(default="aiops_knowledge", description="Milvus collection")

    # Control Plane
    CONTROL_PLANE_URL: str = Field(default="http://localhost:8080", description="Java control plane URL")
    CONTROL_PLANE_API_KEY: str = Field(default="", description="Control plane API key")

    # Analysis
    MAX_ANALYSIS_ROUNDS: int = Field(default=5, description="Max analysis rounds")
    ANALYSIS_TIMEOUT_SEC: int = Field(default=120, description="Analysis timeout seconds")
    CONFIDENCE_THRESHOLD: float = Field(default=0.7, description="Confidence threshold")

    class Config:
        env_file = ".env"
        env_prefix = ""


settings = Settings()
