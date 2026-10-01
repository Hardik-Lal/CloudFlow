# CloudFlow Backend

Java 21 · Spring Boot · Spring Security · Gradle · PostgreSQL. Structured as a modular monolith (see
[../docs/architecture.md](../docs/architecture.md)).

```bash
# needs PostgreSQL: cd ../infrastructure && docker compose up -d postgres
./gradlew bootRun         # http://localhost:8080
./gradlew build           # format check, compile, tests (Docker required for Testcontainers)
./gradlew spotlessApply   # format sources
```
