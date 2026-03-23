from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

from app.core.constants import (
    DEFAULT_API_V1_PREFIX,
    DEFAULT_EUREKA_SERVER_URL,
    DEFAULT_PORT,
    DEFAULT_SERVICE_NAME,
)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    app_name: str = DEFAULT_SERVICE_NAME
    app_version: str = "0.1.0"
    app_env: str = "dev"
    debug: bool = True

    api_v1_prefix: str = DEFAULT_API_V1_PREFIX
    host: str = "0.0.0.0"
    port: int = DEFAULT_PORT
    log_level: str = "INFO"
    public_endpoints: list[str] = ["/health"]

    eureka_enabled: bool = True
    eureka_server_url: str = DEFAULT_EUREKA_SERVER_URL
    eureka_instance_host: str | None = None
    eureka_instance_ip: str | None = None
    eureka_instance_id: str | None = None

    qdrant_host: str = "localhost"
    qdrant_port: int = 6333
    qdrant_grpc_port: int = 6334
    qdrant_https: bool = False
    qdrant_api_key: str | None = None
    qdrant_timeout_seconds: float = 5.0
    qdrant_collection_name: str = "post_vectors"
    qdrant_user_collection_name: str = "user_vectors"
    qdrant_vector_size: int = 384

    embedding_model_name: str = "sentence-transformers/all-MiniLM-L6-v2"
    embedding_device: str = "cpu"

    kafka_enabled: bool = True
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_consumer_group_id: str = "post-recommendation-service-group"
    kafka_user_consumer_group_id: str = "post-recommendation-service-user-group"
    kafka_interaction_consumer_group_id: str = "post-recommendation-service-interaction-group"
    kafka_post_created_topic: str = "social-feed.post.created"
    kafka_post_updated_topic: str = "social-feed.post.updated"
    kafka_post_deleted_topic: str = "social-feed.post.deleted"
    kafka_user_created_topic: str = "user.created"
    kafka_user_interaction_topic: str = "user.interaction"

    social_feed_service_url: str = "http://social-feed-service"
    user_vector_alpha: float = 0.7
    user_vector_interaction_limit: int = 20
    user_vector_decay_half_life_days: float = 7.0

    mongodb_uri: str = "mongodb://localhost:27018"
    mongodb_database: str = "post_recommendation"
    mongodb_server_selection_timeout_ms: int = 3000

    health_path: str = "/health"


@lru_cache
def get_settings() -> Settings:
    return Settings()
