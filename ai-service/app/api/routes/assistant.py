"""Question answering, deployment analysis, and generation (called by the backend)."""

from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.deps import get_assistant_service
from app.core.security import require_internal_token
from app.schemas.assistant import (
    AssistantAnswer,
    DeploymentAnalysisRequest,
    GeneratedArtifact,
    GenerationRequest,
    GenerationType,
    QueryRequest,
)
from app.services.assistant import AssistantService

router = APIRouter(prefix="/v1", tags=["assistant"], dependencies=[Depends(require_internal_token)])

Service = Annotated[AssistantService, Depends(get_assistant_service)]


@router.post("/assistant/query", response_model=AssistantAnswer, response_model_by_alias=True)
def query(request: QueryRequest, service: Service) -> AssistantAnswer:
    return service.query(request)


@router.post("/analysis/deployment", response_model=AssistantAnswer, response_model_by_alias=True)
def analyze_deployment(request: DeploymentAnalysisRequest, service: Service) -> AssistantAnswer:
    return service.analyze_deployment(request)


@router.post("/generate/{kind}", response_model=GeneratedArtifact, response_model_by_alias=True)
def generate(
    kind: GenerationType, request: GenerationRequest, service: Service
) -> GeneratedArtifact:
    return service.generate(kind, request)
