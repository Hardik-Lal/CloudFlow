"""Shared fixtures: a real pgvector database and a deterministic model double.

The model double lives only in the test suite. It turns text into bag-of-words vectors so that
similarity search behaves meaningfully without calling OpenAI.
"""

import hashlib
import math
import re
from collections.abc import Iterator
from typing import Any

import pytest
from fastapi.testclient import TestClient
from testcontainers.community.postgres import PostgresContainer

from app.core.config import get_settings
from app.db.database import create_pool, run_migrations
from app.main import create_app

INTERNAL_TOKEN = "test-internal-token"
DIMENSIONS = 1536


class RecordingLlm:
    """Deterministic embeddings plus canned structured responses, recording every prompt."""

    def __init__(self) -> None:
        self.prompts: list[str] = []
        self.response: dict[str, Any] = {}

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [self._vector(text) for text in texts]

    def complete_json(
        self, system: str, user: str, schema_name: str, schema: dict[str, Any]
    ) -> dict[str, Any]:
        self.prompts.append(user)
        return self.response

    @staticmethod
    def _vector(text: str) -> list[float]:
        vector = [0.0] * DIMENSIONS
        for word in re.findall(r"[a-z0-9_]+", text.lower()):
            bucket = int(hashlib.sha256(word.encode()).hexdigest(), 16) % DIMENSIONS
            vector[bucket] += 1.0
        norm = math.sqrt(sum(v * v for v in vector)) or 1.0
        return [v / norm for v in vector]


@pytest.fixture(scope="session")
def database_url() -> Iterator[str]:
    with PostgresContainer("pgvector/pgvector:pg17", driver=None) as postgres:
        url = postgres.get_connection_url()
        run_migrations(url)
        yield url


@pytest.fixture
def llm() -> RecordingLlm:
    return RecordingLlm()


@pytest.fixture
def client(
    database_url: str, llm: RecordingLlm, monkeypatch: pytest.MonkeyPatch
) -> Iterator[TestClient]:
    monkeypatch.setenv("AI_SERVICE_INTERNAL_TOKEN", INTERNAL_TOKEN)
    get_settings.cache_clear()
    app = create_app(manage_resources=False)
    app.state.pool = create_pool(database_url)
    app.state.llm = llm
    with TestClient(app, headers={"X-Internal-Token": INTERNAL_TOKEN}) as test_client:
        yield test_client
    app.state.pool.close()
    get_settings.cache_clear()
