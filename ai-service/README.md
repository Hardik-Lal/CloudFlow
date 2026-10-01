# CloudFlow AI Service

FastAPI service that provides CloudFlow's AI and RAG capabilities: knowledge indexing into
PostgreSQL + pgvector, retrieval-augmented troubleshooting answers, deployment analysis, and
generation of Dockerfiles, environment templates, workflows, and docs, using the OpenAI API. It is
called only by the CloudFlow backend (with `X-Internal-Token`) and is never exposed to browsers.

Configuration: `AI_SERVICE_DATABASE_URL`, `AI_SERVICE_INTERNAL_TOKEN`, `AI_SERVICE_OPENAI_API_KEY`,
`AI_SERVICE_CHAT_MODEL`, `AI_SERVICE_EMBEDDING_MODEL` (see `../docs/deployment.md`). Tests need
Docker (they start a pgvector database).

```bash
uv sync
uv run uvicorn app.main:app --reload --port 8000
uv run pytest
uv run ruff check . && uv run ruff format --check .
```
