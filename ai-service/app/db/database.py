"""Connection pool and schema migrations for the ``ai`` schema."""

import logging
from importlib import resources

from pgvector.psycopg import register_vector
from psycopg import Connection
from psycopg_pool import ConnectionPool

log = logging.getLogger(__name__)

# Serializes migrations when several instances start at once.
_MIGRATION_LOCK_ID = 7_401_222


def run_migrations(database_url: str) -> None:
    """Applies pending ``app/db/migrations/*.sql`` files in name order, each exactly once."""
    with Connection.connect(database_url, autocommit=True) as conn:
        conn.execute("SELECT pg_advisory_lock(%s)", (_MIGRATION_LOCK_ID,))
        try:
            # The service owns only the "ai" schema, including its migration history.
            conn.execute("CREATE SCHEMA IF NOT EXISTS ai")
            conn.execute(
                "CREATE TABLE IF NOT EXISTS ai.schema_migrations ("
                " version varchar(100) PRIMARY KEY,"
                " applied_at timestamptz NOT NULL DEFAULT now())"
            )
            applied = {row[0] for row in conn.execute("SELECT version FROM ai.schema_migrations")}
            files = sorted(
                f for f in resources.files("app.db.migrations").iterdir() if f.name.endswith(".sql")
            )
            for file in files:
                if file.name in applied:
                    continue
                log.info("Applying AI schema migration %s", file.name)
                with conn.transaction():
                    conn.execute(file.read_text(encoding="utf-8"))
                    conn.execute(
                        "INSERT INTO ai.schema_migrations (version) VALUES (%s)",
                        (file.name,),
                    )
        finally:
            conn.execute("SELECT pg_advisory_unlock(%s)", (_MIGRATION_LOCK_ID,))


def create_pool(database_url: str) -> ConnectionPool:
    """A connection pool whose connections understand the pgvector ``vector`` type."""
    return ConnectionPool(
        database_url,
        min_size=1,
        max_size=10,
        configure=register_vector,
        open=True,
        kwargs={"autocommit": True},
    )
