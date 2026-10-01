"""The language-model boundary: embeddings and structured (JSON schema) completions."""

import json
import logging
from typing import Any, Protocol

from openai import APIError, OpenAI

from app.core.config import Settings

log = logging.getLogger(__name__)


class LlmUnavailableError(RuntimeError):
    """The model provider is not configured or failed; callers answer 503."""


class LlmClient(Protocol):
    def embed(self, texts: list[str]) -> list[list[float]]: ...

    def complete_json(
        self, system: str, user: str, schema_name: str, schema: dict[str, Any]
    ) -> dict[str, Any]: ...


class OpenAiLlmClient:
    """OpenAI implementation using Chat Completions with strict JSON-schema output."""

    _EMBED_BATCH = 64

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client = (
            OpenAI(api_key=settings.openai_api_key, base_url=settings.openai_base_url)
            if settings.ai_configured
            else None
        )

    def embed(self, texts: list[str]) -> list[list[float]]:
        client = self._require_client()
        vectors: list[list[float]] = []
        try:
            for start in range(0, len(texts), self._EMBED_BATCH):
                response = client.embeddings.create(
                    model=self._settings.embedding_model,
                    input=texts[start : start + self._EMBED_BATCH],
                    dimensions=self._settings.embedding_dimensions,
                )
                vectors.extend(item.embedding for item in response.data)
        except APIError as e:
            raise LlmUnavailableError(f"Embedding request failed: {e.message}") from e
        return vectors

    def complete_json(
        self, system: str, user: str, schema_name: str, schema: dict[str, Any]
    ) -> dict[str, Any]:
        client = self._require_client()
        try:
            response = client.chat.completions.create(
                model=self._settings.chat_model,
                messages=[
                    {"role": "system", "content": system},
                    {"role": "user", "content": user},
                ],
                response_format={
                    "type": "json_schema",
                    "json_schema": {"name": schema_name, "schema": schema, "strict": True},
                },
            )
        except APIError as e:
            raise LlmUnavailableError(f"Completion request failed: {e.message}") from e
        content = response.choices[0].message.content
        if not content:
            raise LlmUnavailableError("The model returned an empty response")
        return json.loads(content)

    def _require_client(self) -> OpenAI:
        if self._client is None:
            raise LlmUnavailableError(
                "AI is not configured: set AI_SERVICE_OPENAI_API_KEY for the AI service"
            )
        return self._client
