"""Application settings loaded from environment variables."""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration for the AI service.

    Values are read from environment variables prefixed with ``AI_SERVICE_``
    (for example ``AI_SERVICE_OPENAI_API_KEY``) and optionally from a local ``.env`` file.
    """

    model_config = SettingsConfigDict(env_prefix="AI_SERVICE_", env_file=".env", extra="ignore")

    app_name: str = "cloudflow-ai-service"
    environment: str = "local"
    log_level: str = "INFO"

    #: PostgreSQL connection string (libpq format). The service owns the ``ai`` schema.
    database_url: str = "postgresql://cloudflow:cloudflow@localhost:5432/cloudflow"

    #: Shared secret the CloudFlow backend sends in ``X-Internal-Token``.
    internal_token: str = Field(default="", repr=False)

    openai_api_key: str = Field(default="", repr=False)
    openai_base_url: str | None = None
    chat_model: str = "gpt-5.4-mini"
    embedding_model: str = "text-embedding-3-small"
    #: Must match the ``vector(...)`` column size in the ``ai`` schema.
    embedding_dimensions: int = 1536

    chunk_size: int = 1500
    chunk_overlap: int = 200
    retrieval_limit: int = 8

    @property
    def ai_configured(self) -> bool:
        return bool(self.openai_api_key)


@lru_cache
def get_settings() -> Settings:
    return Settings()
