from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict
import yaml
import os
from dotenv import load_dotenv

# Paths to .env files
# Resolve paths relative to the current file
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_ENV_PATH = os.path.abspath(os.path.join(BASE_DIR, "../../../.env"))
# LOCAL_ENV_PATH = os.path.abspath(os.path.join(BASE_DIR, "../../.env"))

# Explicitly load into environment variables (this makes os.getenv work)
load_dotenv(ROOT_ENV_PATH)
# load_dotenv(LOCAL_ENV_PATH, override=True) # Local settings for this service

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file_encoding="utf-8", 
        extra="ignore"
    )

    openai_api_key: str = Field(alias="OPENAI_API_KEY")
    tavily_api_key: str = Field(alias="TAVILY_API_KEY")
    
    # Mapping from root .env names
    mongodb_uri: str = Field(alias="MONGODB_URI_AI_SERVICE")
    kafka_bootstrap_servers: str = Field(alias="KAFKA_BOOTSTRAP_SERVERS")
    eureka_server: str = Field(alias="EUREKA_SERVER_URL")
    
    # Kafka topics
    socket_events_topic: str = Field(alias="SOCKET_EVENTS_TOPIC", default="socket.events")
    ai_message_save_topic: str = Field(alias="AI_MESSAGE_SAVE_TOPIC", default="ai.message.save")
    
    # Qdrant settings
    qdrant_url: str = "http://localhost:6333" # Default port for REST
    qdrant_api_key: str = ""
    qdrant_collection_name: str = "bondhub-messages"
    qdrant_ingest_collection: str = "bondhub-messages"
    qdrant_score_threshold: float = 0.7
    enable_citation_payload: bool = False

    # Ingest + embedding settings
    embedding_model: str = "text-embedding-3-small"
    excel_row_chunking_enabled: bool = True

    # AWS/S3 settings for ingest
    aws_access_key: str = Field(default="", alias="AWS_ACCESS_KEY")
    aws_secret_key: str = Field(default="", alias="AWS_SECRET_KEY")
    aws_region: str = Field(default="", alias="AWS_REGION")
    aws_bucket_name: str = Field(default="", alias="AWS_S3_BUCKET_NAME")
    
    app_name: str = "agent-service"
    app_port: int = 8082
    max_web_retries: int = 1
    chat_history_limit: int = 20
    api_gateway_url: str = "http://localhost:8080"
    message_service_url: str = "http://localhost:8081"
    
    eureka_instance_host: str = os.getenv("EUREKA_INSTANCE_HOST", "localhost")

    # Optional LangSmith tracing
    langchain_tracing_v2: bool = False
    langchain_api_key: str = ""

    # i18n
    default_locale: str = Field(default="vi", alias="DEFAULT_LOCALE")
    supported_locales: str = Field(default="vi,en", alias="SUPPORTED_LOCALES")

    @classmethod
    def from_yaml(cls, path="config.yaml"):
        conf_data = {}
        if os.path.exists(path):
            with open(path, 'r') as f:
                conf_data = yaml.safe_load(f) or {}
        
        # Pydantic Settings will automatically load from env_file defined in model_config
        return cls(**conf_data)

settings = Settings.from_yaml()
