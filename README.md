# Sri Lankan News Intelligence Platform — Backend

Spring Boot REST API for the Sri Lankan News Intelligence Platform. The platform will collect and organize reporting from multiple Sri Lankan publishers while directing readers to the original journalism.

## Current Phase

**Phase 12 — Story Clustering Foundation**

Enriched articles are assigned to internal MongoDB Story documents by a conservative, deterministic lexical matcher. Public APIs remain unchanged.

## Technology

- Java 17
- Spring Boot 3.5
- Maven
- Spring Web and Bean Validation
- Spring Data MongoDB
- Spring Data Redis
- Spring Boot Actuator
- Google Gen AI Java SDK
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
   $env:ARTICLE_FEED_CACHE_TTL_SECONDS = "60"
   $env:STORY_CLUSTER_WINDOW_HOURS = "48"
   $env:STORY_CLUSTER_THRESHOLD = "0.72"
   $env:STORY_CLUSTER_CANDIDATE_LIMIT = "200"
   $env:STORY_CLUSTER_BACKFILL_LIMIT = "100"
   $env:GEMINI_API_KEY = "<google-ai-studio-api-key>"
   $env:GEMINI_MODEL = "<gemini-model-id>"
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
| `ARTICLE_FEED_CACHE_TTL_SECONDS` | No | `60` | TTL in seconds for public Article feed cache entries. |
| `STORY_CLUSTER_WINDOW_HOURS` | No | `48` | Publication-time window on either side of an Article for Story candidates. |
| `STORY_CLUSTER_THRESHOLD` | No | `0.72` | Minimum deterministic lexical score from 0 to 1. |
| `STORY_CLUSTER_CANDIDATE_LIMIT` | No | `200` | Maximum candidate Stories scored for one Article. |
| `STORY_CLUSTER_BACKFILL_LIMIT` | No | `100` | Maximum enriched, unassigned Articles clustered at startup; `0` disables backfill. |
| `GEMINI_API_KEY` | Yes | None | Google AI Studio API key; never logged or exposed. |
| `GEMINI_MODEL` | Yes | None | Configurable Gemini model identifier. |
| `GEMINI_MAX_INPUT_CHARACTERS` | No | `30000` | Maximum deterministic article-content excerpt sent for enrichment. |

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

## Public Article Feed Cache

`GET /api/v1/articles` uses cache-aside Redis reads for normalized page, size, source, category, language, and publication-sort combinations. Only the completed public `PagedResponse<ArticleResponse>` JSON is cached; MongoDB documents and private ingestion or AI metadata are never cached.

Keys use the `news:feed:` namespace and a generation value. New article creation and successful public AI enrichment increment `news:feed:generation`; duplicate ingestion and failed processing do not. Previous generations become unreachable and expire naturally after `ARTICLE_FEED_CACHE_TTL_SECONDS` (60 seconds by default). Redis read, write, or invalidation failures are logged safely and never fail public MongoDB reads, article persistence, or enrichment.

## Asynchronous Article Processing

Newly persisted articles publish a minimal `ARTICLE_DISCOVERED` Redis Stream event. The worker loads title and internal `extractedContent` from MongoDB; article content is never placed in Redis.

One schema-constrained request through `AiProvider` and `GeminiAiProvider` returns a same-language summary, category, topics, keywords, and simple entities. The prompt forbids translation, outside knowledge, fabricated facts or quotations, and unsupported motive inference.

Structured output is validated before persistence: summaries are limited to 2,000 characters, with at most 8 topics, 15 keywords, and 20 entities plus bounded individual strings. Raw Gemini responses and prompts are not stored.

Input is a deterministic leading excerpt capped at 30,000 characters by default through `GEMINI_MAX_INPUT_CHARACTERS`. Truncation avoids splitting surrogate pairs, is disclosed to the model, and content shorter than 50 characters is rejected.

Validated enrichment stores private model, prompt version `v1`, and UTC processing time. Public Article DTOs expose only summary, topics, and category. Matching model-and-prompt replays skip Gemini.

Provider outages, timeouts, rate limits, invalid JSON, and incomplete output use the existing bounded retry and dead-letter flow. MongoDB persistence and ingestion remain successful independently.

At normal application startup, Daily Mirror is registered as an enabled English
RSS source if its `daily-mirror` slug does not already exist. No other source
is seeded.

## Story Clustering

After successful AI enrichment, the worker assigns the Article to one internal Story. Candidate Stories must overlap the configurable publication window. Matching version `lexical-v1` combines normalized title-token Jaccard overlap (70%), topics (15%), entities (10%), and category agreement (5%). Conflicting known categories and different original languages are rejected; title overlap below 0.45 is rejected before the configured threshold is applied. Equal scores prefer the most recently active Story and then the stable Story ID.

Normalization uses Unicode NFKC, locale-neutral case folding, punctuation-to-whitespace conversion, and token whitespace normalization. It does not translate or semantically rewrite text. Consequently, English, Sinhala, and Tamil reports about the same event will usually remain separate unless future multilingual or semantic matching is introduced.

`Article.storyId` is internal and is not exposed by public DTOs. Story membership updates use an internal article-ID set and conditional MongoDB updates so replay cannot increment `articleCount` twice. Article assignment is an atomic set-if-null operation; the winning MongoDB assignment remains authoritative during concurrent attempts. Story-only changes do not invalidate the public Article feed cache.

Story decisions run in an Atlas transaction. The transaction first increments a private `story_cluster_partitions` revision keyed by matching version and original language, then reloads candidates, creates or selects the Story, assigns the Article, and updates membership. Concurrent decisions in the same partition produce a MongoDB write conflict; the complete transaction retries at most three times with a fresh snapshot.

A bounded startup backfill clusters already-enriched Articles without a Story, oldest first, and never calls Gemini. If clustering fails after enrichment, the existing Redis retry/DLQ flow is used; the persisted matching enrichment causes retries to skip Gemini and retry only clustering.

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

Phase 13 — Embedding-Assisted Story Matching
