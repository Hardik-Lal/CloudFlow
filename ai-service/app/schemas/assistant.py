"""Models for assistant answers, deployment analysis, and generation."""

from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field


class Confidence(StrEnum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class Evidence(BaseModel):
    source: str
    reference: str
    excerpt: str


class AssistantAnswer(BaseModel):
    """Every answer states its evidence and uncertainty (see docs/requirements.md FR-7)."""

    answer: str
    likely_cause: str | None = Field(serialization_alias="likelyCause")
    evidence: list[Evidence]
    recommended_actions: list[str] = Field(serialization_alias="recommendedActions")
    confidence: Confidence
    confidence_explanation: str = Field(serialization_alias="confidenceExplanation")


class QueryRequest(BaseModel):
    project_id: UUID = Field(alias="projectId")
    environment_id: UUID | None = Field(default=None, alias="environmentId")
    question: str = Field(min_length=3, max_length=2000)
    #: Live platform state supplied by the backend (deployments, health, events).
    live_context: dict[str, Any] = Field(default_factory=dict, alias="liveContext")

    model_config = {"populate_by_name": True}


class DeploymentAnalysisRequest(BaseModel):
    project_id: UUID = Field(alias="projectId")
    environment_id: UUID = Field(alias="environmentId")
    #: The deployment, its logs, configuration, and the previous successful deployment.
    deployment: dict[str, Any]
    logs: list[str] = Field(default_factory=list, max_length=2000)
    configuration: dict[str, Any] = Field(default_factory=dict)
    previous_successful: dict[str, Any] | None = Field(default=None, alias="previousSuccessful")
    dockerfile: str | None = None

    model_config = {"populate_by_name": True}


class GenerationType(StrEnum):
    DOCKERFILE = "DOCKERFILE"
    ENV_TEMPLATE = "ENV_TEMPLATE"
    WORKFLOW = "WORKFLOW"
    DOCUMENTATION = "DOCUMENTATION"


class GenerationRequest(BaseModel):
    project_id: UUID = Field(alias="projectId")
    environment_id: UUID | None = Field(default=None, alias="environmentId")
    #: Project facts from the backend (name, app type, configuration, repository files).
    context: dict[str, Any] = Field(default_factory=dict)
    instructions: str | None = Field(default=None, max_length=2000)

    model_config = {"populate_by_name": True}


class GeneratedArtifact(BaseModel):
    #: Repository path for files; for ENV_TEMPLATE, "variables".
    file_path: str = Field(serialization_alias="filePath")
    content: str
    explanation: str
