"""Retrieval-augmented answers, deployment analysis, and configuration generation."""

from uuid import UUID

from app.core.config import Settings
from app.rag.store import RetrievedChunk, VectorStore
from app.schemas.assistant import (
    AssistantAnswer,
    DeploymentAnalysisRequest,
    GeneratedArtifact,
    GenerationRequest,
    GenerationType,
    QueryRequest,
)
from app.services import prompts
from app.services.llm import LlmClient


class AssistantService:
    def __init__(self, store: VectorStore, llm: LlmClient, settings: Settings) -> None:
        self._store = store
        self._llm = llm
        self._settings = settings

    def query(self, request: QueryRequest) -> AssistantAnswer:
        chunks = self._retrieve(request.project_id, request.question, request.environment_id)
        result = self._llm.complete_json(
            prompts.SYSTEM_ASSISTANT,
            prompts.question_prompt(request.question, request.live_context, chunks),
            "assistant_answer",
            prompts.ANSWER_SCHEMA,
        )
        return AssistantAnswer.model_validate(result)

    def analyze_deployment(self, request: DeploymentAnalysisRequest) -> AssistantAnswer:
        # Retrieval is driven by the failure itself: its reason plus the last log lines.
        focus = (
            " ".join(
                filter(
                    None,
                    [
                        str(request.deployment.get("failureReason") or ""),
                        " ".join(request.logs[-20:]),
                    ],
                )
            )
            or "deployment configuration"
        )
        chunks = self._retrieve(request.project_id, focus[:4000], request.environment_id)
        result = self._llm.complete_json(
            prompts.SYSTEM_ASSISTANT,
            prompts.analysis_prompt(
                request.deployment,
                request.logs,
                request.configuration,
                request.previous_successful,
                request.dockerfile,
                chunks,
            ),
            "deployment_analysis",
            prompts.ANSWER_SCHEMA,
        )
        return AssistantAnswer.model_validate(result)

    def generate(self, kind: GenerationType, request: GenerationRequest) -> GeneratedArtifact:
        chunks = self._retrieve(
            request.project_id, f"{kind.value} build configuration", request.environment_id
        )
        result = self._llm.complete_json(
            prompts.SYSTEM_GENERATOR,
            prompts.generation_prompt(kind.value, request.context, request.instructions, chunks),
            "generated_artifact",
            prompts.ARTIFACT_SCHEMA,
        )
        return GeneratedArtifact.model_validate(result)

    def _retrieve(
        self, project_id: UUID, text: str, environment_id: UUID | None
    ) -> list[RetrievedChunk]:
        embedding = self._llm.embed([text])[0]
        return self._store.search(
            project_id, embedding, self._settings.retrieval_limit, environment_id
        )
