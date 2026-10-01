"""Indexing of project knowledge (called by the backend)."""

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends

from app.api.deps import get_indexing_service
from app.core.security import require_internal_token
from app.schemas.knowledge import IndexRequest, IndexResponse, KnowledgeStats
from app.services.indexing import IndexingService

router = APIRouter(
    prefix="/v1/index", tags=["knowledge"], dependencies=[Depends(require_internal_token)]
)

Service = Annotated[IndexingService, Depends(get_indexing_service)]


@router.post("/documents", response_model=IndexResponse)
def index_documents(request: IndexRequest, service: Service) -> IndexResponse:
    return service.index(request)


@router.get("/projects/{project_id}", response_model=KnowledgeStats, response_model_by_alias=True)
def project_stats(project_id: UUID, service: Service) -> KnowledgeStats:
    return service.stats(project_id)


@router.delete("/projects/{project_id}", status_code=204)
def delete_project(project_id: UUID, service: Service) -> None:
    service.delete_project(project_id)
