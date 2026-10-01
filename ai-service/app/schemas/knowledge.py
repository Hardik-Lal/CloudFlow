"""Request and response models for indexing project knowledge."""

from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, Field


class SourceType(StrEnum):
    README = "README"
    DOCUMENTATION = "DOCUMENTATION"
    DOCKERFILE = "DOCKERFILE"
    COMPOSE = "COMPOSE"
    CONFIGURATION = "CONFIGURATION"
    BUILD_MANIFEST = "BUILD_MANIFEST"
    WORKFLOW = "WORKFLOW"
    ENVIRONMENT = "ENVIRONMENT"
    DEPLOYMENT = "DEPLOYMENT"
    LOG = "LOG"


class KnowledgeDocument(BaseModel):
    source_type: SourceType = Field(alias="sourceType")
    #: Stable identifier within the project, e.g. "file:Dockerfile" or "deployment:<id>".
    source_ref: str = Field(alias="sourceRef", min_length=1, max_length=500)
    environment_id: UUID | None = Field(default=None, alias="environmentId")
    deployment_id: UUID | None = Field(default=None, alias="deploymentId")
    content: str = Field(max_length=200_000)
    metadata: dict[str, str] = Field(default_factory=dict)

    model_config = {"populate_by_name": True}


class IndexRequest(BaseModel):
    project_id: UUID = Field(alias="projectId")
    documents: list[KnowledgeDocument] = Field(max_length=500)
    #: Remove the project's documents that are not in this request (full re-index).
    replace_all: bool = Field(default=False, alias="replaceAll")

    model_config = {"populate_by_name": True}


class IndexResponse(BaseModel):
    indexed: int
    unchanged: int
    removed: int
    chunks: int


class KnowledgeStats(BaseModel):
    documents: int
    chunks: int
    last_indexed_at: str | None = Field(default=None, serialization_alias="lastIndexedAt")
