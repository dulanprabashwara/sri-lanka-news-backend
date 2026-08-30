# Sri Lankan News Intelligence Platform — Backend

Spring Boot REST API for the Sri Lankan News Intelligence Platform. The platform will collect and organize reporting from multiple Sri Lankan publishers while directing readers to the original journalism.

## Current Phase

**Phase 6 — First End-to-End Publisher Pipeline**

This phase preserves the read-only public APIs and adds a shared-secret-protected
internal article ingestion endpoint plus an idempotent Daily Mirror source seed.

## Technology

- Java 17
- Spring Boot 3.5
- Maven
- Spring Web and Bean Validation
- Spring Data MongoDB
- Spring Boot Actuator
- JUnit 5 and Spring Boot Test

## Prerequisites

- JDK 17
- Maven 3.6.3 or later
- A MongoDB Atlas account and development cluster

## Local Setup

1. Create or select a development cluster in MongoDB Atlas.
2. Create an Atlas database user with access to the development database. Do not reuse your Atlas account password.
3. In Atlas Network Access, add the IP address of each developer who needs to connect. Avoid unrestricted network access for routine development.
4. Obtain the application connection string from Atlas and replace its username, password, and cluster-host placeholders with the database user's values.
5. Set the completed connection string locally as `MONGODB_URI`. Obtain a
   provider-neutral Redis connection URL from a development Redis service and
   set it as `REDIS_URL`. Generate a
   separate strong random value for `INGESTION_API_KEY`; configure the same
   value in the Python ingestion service. Spring Boot does not load `.env`
   files automatically, so export both variables in your shell or configure
   them in your IDE.

   PowerShell:

   ```powershell
   $env:MONGODB_URI = "mongodb+srv://<username>:<password>@<cluster-host>/sri_lanka_news?retryWrites=true&w=majority"
   $env:INGESTION_API_KEY = "<strong-random-shared-secret>"
   $env:REDIS_URL = "rediss://:<password>@<redis-host>:<port>"
   ```

6. Run the application only after `MONGODB_URI` is available in its environment.

The application intentionally has no localhost fallback. Never commit the completed Atlas URI or real database credentials.

## Environment Variables

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `MONGODB_URI` | Yes | None | MongoDB Atlas application connection string. |
| `INGESTION_API_KEY` | Yes | None | Shared secret accepted only by internal ingestion endpoints. |
| `REDIS_URL` | Yes | None | Provider-neutral `redis://` or TLS `rediss://` connection URL. |
| `REDIS_PROCESSING_ENABLED` | No | `true` | Enables Redis Streams publishing and consumption. |

Never commit real credentials or a populated `.env` file.

## Running

```bash
mvn spring-boot:run
```

The application listens on `http://localhost:8080` by default. The deliberately limited Actuator health endpoint is available at:

```text
GET /actuator/health
```

## Public API

```text
GET /api/v1/sources
GET /api/v1/sources/{slug}
GET /api/v1/articles
GET /api/v1/articles/{id}
```

The Article list accepts zero-based `page`, `size`, optional `source`, `category`, and `language` filters, plus `sort=publishedAt,asc|desc`. Defaults are `page=0`, `size=20`, and newest-first publication sorting. Requests above the maximum page size of `100` are rejected.

## Internal Ingestion API

```text
POST /api/internal/v1/articles
X-Ingestion-API-Key: <INGESTION_API_KEY>
```

The endpoint resolves `sourceSlug`, validates article metadata and bounded
cleaned `extractedContent`, and returns `201 CREATED` for a new article or
`200 OK` with `URL_DUPLICATE` or `CONTENT_DUPLICATE` duplicate information.
It is not a public
write API. Extracted content is stored only for internal processing and is
never exposed by either public Article endpoint. Lead-image metadata is not
stored or exposed.

The backend owns exact-content fingerprinting: NFC Unicode normalization and
whitespace normalization are applied before SHA-256 hashing. A sparse unique
`contentHash` index is safe for legacy documents where the field is absent.
An idempotent startup backfill hashes legacy content where possible and skips
pre-existing same-content collisions without deleting or overwriting articles.

## Asynchronous Article Processing

Newly persisted articles dispatch publication of a minimal `ARTICLE_DISCOVERED`
event to the `article-discovered` Redis Stream on a dedicated executor, so
the ingestion request does not wait for Redis. Events contain identifiers, occurrence
time, version, and retry attempt only; article content remains in MongoDB.
The `article-processing` consumer group performs a deterministic Phase 9
status transition from `PENDING` through `PROCESSING` to `COMPLETED`.

Processing failures are retried at most three times. Exhausted events are
written to `article-discovered-dlq` before the original message is
acknowledged. If retry or dead-letter publication fails, the original remains
unacknowledged. Redis connection failures never roll back a stored Article.
Articles in nonterminal processing states are republished at the next backend
startup, providing lightweight development-stage recovery without introducing
a transactional outbox. The Actuator health endpoint includes Redis availability
without exposing its URL or credentials.

At normal application startup, Daily Mirror is registered as an enabled English
RSS source if its `daily-mirror` slug does not already exist. No other source
is seeded.

## Testing

Run the automated tests:

```bash
mvn test
```

Create the executable application package:

```bash
mvn clean package
```

Automated tests require neither Atlas nor Redis. Their application test context
disables both external integrations and their health checks.

## Architecture Conventions

- Application APIs use the `/api/v1` base path.
- Packages are introduced by feature. Shared cross-cutting code belongs in `common`; application configuration belongs in `config`.
- Controllers use request/response DTOs and never expose MongoDB persistence documents directly.
- Controllers stay thin, services own business logic, and repositories only handle persistence.
- Bean Validation is applied at API boundaries.
- Source and Article documents are stored in separate `sources` and `articles` collections. MongoDB creates unique indexes for source slugs, canonical article URLs, and non-null article content hashes at application startup.
- Public controllers expose dedicated DTOs, and Article pages resolve Source attribution with one batched lookup rather than one query per Article.
- Domain services use UTC `Instant` timestamps supplied by an injectable UTC clock.
- REST errors use one centralized structure containing a timestamp, HTTP status, stable application code, safe message, request path, request ID, and optional field details.
- Requests accept a safe `X-Request-ID` value or receive a generated one. The ID is returned in the same response header and included in application logs.
- Normal HTTP status codes and response bodies are preferred over a generic success envelope.
- List endpoints will use zero-based `page`, `size`, and `sort=field,direction`. The initial convention is `page=0`, `size=20`, with a maximum size of `100`; enforcement begins when paginated endpoints are introduced.
- Configuration is externalized through Spring Boot properties and environment variables. Secrets must not be stored in source control.

## Planned Next Phase

Phase 10 — Gemini AI Processing has not been started.
