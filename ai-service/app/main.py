"""FastAPI application factory for the CloudFlow AI service."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routes import assistant, health, knowledge
from app.core.config import get_settings
from app.db.database import create_pool, run_migrations
from app.services.llm import LlmUnavailableError, OpenAiLlmClient

log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    run_migrations(settings.database_url)
    app.state.pool = create_pool(settings.database_url)
    app.state.llm = OpenAiLlmClient(settings)
    if not settings.ai_configured:
        log.warning("AI_SERVICE_OPENAI_API_KEY is not set; AI endpoints will answer 503")
    try:
        yield
    finally:
        app.state.pool.close()


def create_app(*, manage_resources: bool = True) -> FastAPI:
    """
    Args:
        manage_resources: open the database pool and model client on startup. Tests pass False
            and provide ``app.state.pool`` / ``app.state.llm`` themselves.
    """
    settings = get_settings()
    logging.basicConfig(level=settings.log_level)

    app = FastAPI(
        title="CloudFlow AI Service",
        version="0.1.0",
        lifespan=lifespan if manage_resources else None,
    )
    app.include_router(health.router)
    app.include_router(knowledge.router)
    app.include_router(assistant.router)

    @app.exception_handler(LlmUnavailableError)
    async def llm_unavailable(_: Request, exc: LlmUnavailableError) -> JSONResponse:
        return JSONResponse(status_code=503, content={"detail": str(exc)})

    return app


app = create_app()
