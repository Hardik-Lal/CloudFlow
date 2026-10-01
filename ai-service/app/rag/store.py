"""pgvector-backed storage and similarity search for project knowledge."""

import hashlib
from dataclasses import dataclass
from uuid import UUID

import numpy as np
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool

from app.schemas.knowledge import KnowledgeDocument


@dataclass(frozen=True)
class RetrievedChunk:
    source_type: str
    source_ref: str
    content: str
    distance: float
    environment_id: UUID | None
    deployment_id: UUID | None


def content_hash(document: KnowledgeDocument) -> str:
    return hashlib.sha256(document.content.encode("utf-8")).hexdigest()


class VectorStore:
    def __init__(self, pool: ConnectionPool) -> None:
        self._pool = pool

    def existing_hashes(self, project_id: UUID) -> dict[str, str]:
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT source_ref, content_hash FROM ai.documents WHERE project_id = %s",
                (project_id,),
            ).fetchall()
        return dict(rows)

    def replace_document(
        self,
        project_id: UUID,
        document: KnowledgeDocument,
        chunks: list[str],
        embeddings: list[list[float]],
    ) -> None:
        """Stores a document and its chunks, replacing any previous version atomically."""
        with self._pool.connection() as conn, conn.transaction():
            conn.execute(
                "DELETE FROM ai.documents WHERE project_id = %s AND source_ref = %s",
                (project_id, document.source_ref),
            )
            document_id = conn.execute(
                "INSERT INTO ai.documents (project_id, source_type, source_ref, environment_id,"
                " deployment_id, content_hash, metadata) VALUES (%s, %s, %s, %s, %s, %s, %s)"
                " RETURNING id",
                (
                    project_id,
                    document.source_type.value,
                    document.source_ref,
                    document.environment_id,
                    document.deployment_id,
                    content_hash(document),
                    Jsonb(document.metadata),
                ),
            ).fetchone()[0]
            with conn.cursor() as cursor:
                cursor.executemany(
                    "INSERT INTO ai.chunks (document_id, chunk_index, content, embedding)"
                    " VALUES (%s, %s, %s, %s)",
                    [
                        (document_id, index, chunk, np.array(vector, dtype=np.float32))
                        for index, (chunk, vector) in enumerate(
                            zip(chunks, embeddings, strict=True)
                        )
                    ],
                )

    def delete_documents(self, project_id: UUID, source_refs: list[str]) -> int:
        if not source_refs:
            return 0
        with self._pool.connection() as conn:
            return conn.execute(
                "DELETE FROM ai.documents WHERE project_id = %s AND source_ref = ANY(%s)",
                (project_id, source_refs),
            ).rowcount

    def delete_project(self, project_id: UUID) -> int:
        with self._pool.connection() as conn:
            return conn.execute(
                "DELETE FROM ai.documents WHERE project_id = %s", (project_id,)
            ).rowcount

    def search(
        self,
        project_id: UUID,
        embedding: list[float],
        limit: int,
        environment_id: UUID | None = None,
    ) -> list[RetrievedChunk]:
        """Nearest chunks by cosine distance, restricted to the project (and environment).

        Environment filtering keeps project-wide sources (repository files) that have no
        environment, plus sources of the requested environment.
        """
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT d.source_type, d.source_ref, c.content, c.embedding <=> %s AS distance,"
                " d.environment_id, d.deployment_id"
                " FROM ai.chunks c JOIN ai.documents d ON d.id = c.document_id"
                " WHERE d.project_id = %s"
                " AND (%s::uuid IS NULL OR d.environment_id IS NULL OR d.environment_id = %s)"
                " ORDER BY distance LIMIT %s",
                (
                    np.array(embedding, dtype=np.float32),
                    project_id,
                    environment_id,
                    environment_id,
                    limit,
                ),
            ).fetchall()
        return [RetrievedChunk(*row) for row in rows]

    def stats(self, project_id: UUID) -> tuple[int, int, str | None]:
        with self._pool.connection() as conn:
            row = conn.execute(
                "SELECT count(DISTINCT d.id), count(c.id), max(d.indexed_at)"
                " FROM ai.documents d LEFT JOIN ai.chunks c ON c.document_id = d.id"
                " WHERE d.project_id = %s",
                (project_id,),
            ).fetchone()
        return row[0], row[1], row[2].isoformat() if row[2] else None
