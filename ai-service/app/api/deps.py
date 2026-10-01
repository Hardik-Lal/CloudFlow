"""Dependency providers; tests override these to inject a test database or model client."""

from typing import Annotated

from fastapi import Depends, Request

from app.core.config import Settings, get_settings
from app.rag.store import VectorStore
from app.services.assistant import AssistantService
from app.services.indexing import IndexingService
from app.services.llm import LlmClient


def get_store(request: Request) -> VectorStore:
    return VectorStore(request.app.state.pool)


def get_llm(request: Request) -> LlmClient:
    return request.app.state.llm


def get_indexing_service(
    store: Annotated[VectorStore, Depends(get_store)],
    llm: Annotated[LlmClient, Depends(get_llm)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> IndexingService:
    return IndexingService(store, llm, settings)


def get_assistant_service(
    store: Annotated[VectorStore, Depends(get_store)],
    llm: Annotated[LlmClient, Depends(get_llm)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> AssistantService:
    return AssistantService(store, llm, settings)
