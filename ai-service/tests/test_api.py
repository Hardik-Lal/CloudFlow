from uuid import uuid4

from fastapi.testclient import TestClient

from app.core.config import Settings
from app.services.llm import LlmUnavailableError, OpenAiLlmClient
from tests.conftest import RecordingLlm

ANSWER = {
    "answer": "The app crashes because DATABASE_URL is missing.",
    "likely_cause": "DATABASE_URL is not configured",
    "evidence": [
        {"source": "deployment-logs", "reference": "deployment-logs", "excerpt": "KeyError"}
    ],
    "recommended_actions": ["Add DATABASE_URL to the environment variables"],
    "confidence": "HIGH",
    "confidence_explanation": "The traceback names the missing variable.",
}


def index(client: TestClient, project_id: str, docs: list[dict], replace_all: bool = False):
    return client.post(
        "/v1/index/documents",
        json={"projectId": project_id, "documents": docs, "replaceAll": replace_all},
    )


def doc(ref: str, content: str, source_type: str = "DOCUMENTATION") -> dict:
    return {"sourceType": source_type, "sourceRef": ref, "content": content}


def test_health_reports_configuration(client: TestClient) -> None:
    body = client.get("/health").json()
    assert body["status"] == "UP"
    assert "aiConfigured" in body


def test_requires_the_internal_token(client: TestClient) -> None:
    response = client.post(
        "/v1/index/documents",
        json={"projectId": str(uuid4()), "documents": []},
        headers={"X-Internal-Token": "wrong"},
    )
    assert response.status_code == 401


def test_indexes_skips_unchanged_and_replaces_removed(client: TestClient) -> None:
    project = str(uuid4())
    first = index(client, project, [doc("file:README.md", "Setup guide"), doc("file:a", "A")])
    assert first.json()["indexed"] == 2

    second = index(client, project, [doc("file:README.md", "Setup guide")], replace_all=True)
    assert second.json() == {"indexed": 0, "unchanged": 1, "removed": 1, "chunks": 0}

    stats = client.get(f"/v1/index/projects/{project}").json()
    assert stats["documents"] == 1
    assert stats["lastIndexedAt"] is not None


def test_query_retrieves_relevant_project_knowledge_only(
    client: TestClient, llm: RecordingLlm
) -> None:
    project, other = str(uuid4()), str(uuid4())
    index(
        client,
        project,
        [
            doc("file:Dockerfile", "FROM python:3.12-slim\nEXPOSE 8000", "DOCKERFILE"),
            doc("file:README.md", "This service sends invoices by email."),
        ],
    )
    index(client, other, [doc("file:secret-plan.md", "Dockerfile python EXPOSE secret plan")])
    llm.response = ANSWER

    response = client.post(
        "/v1/assistant/query",
        json={
            "projectId": project,
            "question": "Which port does the Dockerfile expose for python?",
            "liveContext": {"latestDeployment": {"status": "FAILED"}},
        },
    )

    assert response.status_code == 200
    assert response.json()["likelyCause"] == "DATABASE_URL is not configured"
    assert response.json()["recommendedActions"] == [
        "Add DATABASE_URL to the environment variables"
    ]
    prompt = llm.prompts[-1]
    assert 'ref="S1 · file:Dockerfile"' in prompt
    assert "latestDeployment" in prompt
    assert "secret-plan" not in prompt


def test_deployment_analysis_includes_logs_and_previous_deployment(
    client: TestClient, llm: RecordingLlm
) -> None:
    llm.response = ANSWER
    response = client.post(
        "/v1/analysis/deployment",
        json={
            "projectId": str(uuid4()),
            "environmentId": str(uuid4()),
            "deployment": {"status": "FAILED", "failureReason": "Health check failed"},
            "logs": ["Traceback", "KeyError: 'DATABASE_URL'"],
            "configuration": {"template": "PYTHON"},
            "previousSuccessful": {"commitSha": "1111111"},
            "dockerfile": "FROM python:3.12-slim",
        },
    )

    assert response.status_code == 200
    assert response.json()["confidence"] == "HIGH"
    prompt = llm.prompts[-1]
    assert "KeyError: 'DATABASE_URL'" in prompt
    assert "1111111" in prompt
    assert "FROM python:3.12-slim" in prompt


def test_generates_artifacts(client: TestClient, llm: RecordingLlm) -> None:
    llm.response = {
        "file_path": "Dockerfile",
        "content": "FROM node:22-alpine",
        "explanation": "Node.js app",
    }
    response = client.post(
        "/v1/generate/DOCKERFILE",
        json={"projectId": str(uuid4()), "context": {"appType": "NODE"}},
    )

    assert response.status_code == 200
    assert response.json() == {
        "filePath": "Dockerfile",
        "content": "FROM node:22-alpine",
        "explanation": "Node.js app",
    }
    assert '"appType": "NODE"' in llm.prompts[-1]


def test_deleting_a_project_removes_its_knowledge(client: TestClient) -> None:
    project = str(uuid4())
    index(client, project, [doc("file:README.md", "hello")])

    assert client.delete(f"/v1/index/projects/{project}").status_code == 204
    assert client.get(f"/v1/index/projects/{project}").json()["documents"] == 0


def test_unconfigured_openai_client_fails_clearly() -> None:
    llm = OpenAiLlmClient(Settings(openai_api_key=""))
    try:
        llm.embed(["x"])
    except LlmUnavailableError as e:
        assert "AI_SERVICE_OPENAI_API_KEY" in str(e)
    else:
        raise AssertionError("expected LlmUnavailableError")


def test_model_failures_answer_503(client: TestClient, llm: RecordingLlm) -> None:
    def fail(*_args, **_kwargs):
        raise LlmUnavailableError("provider down")

    llm.embed = fail  # type: ignore[method-assign]
    response = client.post(
        "/v1/assistant/query", json={"projectId": str(uuid4()), "question": "why?"}
    )
    assert response.status_code == 503
    assert response.json()["detail"] == "provider down"
