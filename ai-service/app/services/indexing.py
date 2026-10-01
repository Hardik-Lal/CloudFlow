"""Chunks, embeds, and stores project knowledge; unchanged documents are skipped."""

from uuid import UUID

from app.core.config import Settings
from app.rag.chunking import chunk_text
from app.rag.store import VectorStore, content_hash
from app.schemas.knowledge import IndexRequest, IndexResponse, KnowledgeStats
from app.services.llm import LlmClient


class IndexingService:
    def __init__(self, store: VectorStore, llm: LlmClient, settings: Settings) -> None:
        self._store = store
        self._llm = llm
        self._settings = settings

    def index(self, request: IndexRequest) -> IndexResponse:
        existing = self._store.existing_hashes(request.project_id)
        indexed = unchanged = chunk_count = 0
        for document in request.documents:
            if existing.get(document.source_ref) == content_hash(document):
                unchanged += 1
                continue
            chunks = chunk_text(
                self._contextualize(document.source_ref, document.content),
                self._settings.chunk_size,
                self._settings.chunk_overlap,
            )
            if not chunks:
                continue
            embeddings = self._llm.embed(chunks)
            self._store.replace_document(request.project_id, document, chunks, embeddings)
            indexed += 1
            chunk_count += len(chunks)

        removed = 0
        if request.replace_all:
            wanted = {document.source_ref for document in request.documents}
            removed = self._store.delete_documents(
                request.project_id, [ref for ref in existing if ref not in wanted]
            )
        return IndexResponse(
            indexed=indexed, unchanged=unchanged, removed=removed, chunks=chunk_count
        )

    def delete_project(self, project_id: UUID) -> int:
        return self._store.delete_project(project_id)

    def stats(self, project_id: UUID) -> KnowledgeStats:
        documents, chunks, last = self._store.stats(project_id)
        return KnowledgeStats(documents=documents, chunks=chunks, last_indexed_at=last)

    @staticmethod
    def _contextualize(source_ref: str, content: str) -> str:
        # Prefixing the source lets queries like "what is in the Dockerfile" match its chunks.
        return f"[{source_ref}]\n{content}"
