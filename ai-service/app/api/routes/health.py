"""Liveness and configuration status used by Docker health checks and the backend."""

from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings

router = APIRouter(tags=["health"])


class HealthResponse(BaseModel):
    status: str
    ai_configured: bool = Field(serialization_alias="aiConfigured")


@router.get("/health", response_model=HealthResponse, response_model_by_alias=True)
def health(settings: Annotated[Settings, Depends(get_settings)]) -> HealthResponse:
    return HealthResponse(status="UP", ai_configured=settings.ai_configured)
