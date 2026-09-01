# Sri Lankan News Intelligence Platform — Backend

Spring Boot REST API for the Sri Lankan News Intelligence Platform. The platform will collect and organize reporting from multiple Sri Lankan publishers while directing readers to the original journalism.

## Current Phase

**Phase 16 — Story Timeline**

Public Story pages include a deterministic chronology of currently linked publisher reports based on Article publication timestamps.

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
   $env:STORY_EMBEDDING_MODEL = "gemini-embedding-2"
   $env:STORY_EMBEDDING_DIMENSIONS = "768"
   $env:STORY_SEMANTIC_THRESHOLD = "0.82"
   $env:STORY_CROSS_LANGUAGE_SEMANTIC_THRESHOLD = "0.90"
   $env:STORY_EMBEDDING_BACKFILL_LIMIT = "50"
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
| `STORY_EMBEDDING_MODEL` | No | `gemini-embedding-2` | Provider model used for private Story-matching embeddings. |
| `STORY_EMBEDDING_DIMENSIONS` | No | `768` | Required embedding vector dimensions. |
| `STORY_EMBEDDING_MAX_INPUT_CHARACTERS` | No | `8000` | Maximum deterministic semantic metadata input length; full article content is excluded. |
| `STORY_SEMANTIC_THRESHOLD` | No | `0.82` | Minimum semantic evidence used to strengthen same-language matching. |
| `STORY_CROSS_LANGUAGE_SEMANTIC_THRESHOLD` | No | `0.90` | Conservative minimum for cross-language matching. |
| `STORY_EMBEDDING_BACKFILL_LIMIT` | No | `50` | Maximum enriched Articles embedded at startup; `0` disables embedding backfill. |
| `GEMINI_API_KEY` | Yes | None | Google AI Studio API key; never logged or exposed. |
| `GEMINI_MODEL` | Yes | None | Configurable Gemini model identifier. |
| `GEMINI_MAX_INPUT_CHARACTERS` | No | `30000` | Maximum deterministic article-content excerpt sent for enrichment. |
| `MULTILINGUAL_MODEL` | No | `GEMINI_MODEL` | Gemini model used behind the translation provider abstraction. |
| `MULTILINGUAL_PROMPT_VERSION` | No | `translation-v1` | Translation input/prompt contract version used for idempotency. |
| `MULTILINGUAL_MAX_INPUT_CHARACTERS` | No | `8000` | Maximum combined title and AI-summary translation input. |
| `MULTILINGUAL_BACKFILL_LIMIT` | No | `10` | Maximum stale/missing translations generated at startup; `0` disables backfill. |

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
GET /api/v1/stories
GET /api/v1/stories/{id}
GET /api/v1/stories/{id}/coverage
GET /api/v1/stories/{id}/timeline
GET /api/v1/articles/{id}/story
```

The Article list accepts zero-based `page`, `size`, optional `source`, `category`, and `language` filters, plus `sort=publishedAt,asc|desc`. Defaults are `page=0`, `size=20`, and newest-first publication sorting. Requests above the maximum page size of `100` are rejected.

Story coverage comparison groups assigned Articles by publisher and compares normalized
topics and entities already stored by enrichment. It makes no AI provider or network call.
"Source-specific" means only that metadata is not present in another currently available
publisher report; it does not imply intentional omission. Equality uses conservative Unicode,
whitespace, and case normalization, so equivalent terms written in different languages are
not merged. Single-source Stories return a valid response with `comparisonAvailable=false`.
Only public summaries and metadata are returned; extracted content, keywords, model/prompt
metadata, embeddings, clustering fields, and internal IDs remain private.

Story timelines order all assigned Articles by `publishedAt` and stable Article ID, then
calculate elapsed whole minutes from the earliest available report. The timeline describes
publisher-report publication chronology only; it does not establish event occurrence time or
infer discovery, copying, causation, credibility, or publisher intent. It makes no AI/provider
or network call. Single-report Stories return one event at minute zero. Timeline DTOs expose
only public summaries and attribution; private content, hashes, embeddings, processing,
clustering, model/prompt, and MongoDB metadata remain internal.

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

`GET /api/v1/articles` uses cache-aside Redis reads for normalized page, size, source, category, original language, display language, and publication-sort combinations. Only the completed public `PagedResponse<ArticleResponse>` JSON is cached; MongoDB documents and private ingestion or AI metadata are never cached.

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

After successful AI enrichment, the worker generates a private semantic embedding and then assigns the Article to one internal Story. The provider-neutral `EmbeddingProvider` currently uses Gemini's configurable `gemini-embedding-2` model with 768 dimensions by default. Because that model does not accept the embedding `taskType` field, input version `story-semantic-v2` deterministically prefixes the provider-visible text with `task: sentence similarity | query:`. The remaining input contains only title, AI summary, topics, entities, and category; `extractedContent` is never sent to the embedding endpoint.

Candidate Stories remain bounded to the configurable +/-48-hour publication window and representative Articles are batch-loaded before scoring. Active matching version `hybrid-v1` preserves the `lexical-v1` score for same-language reports and uses conservative semantic evidence to improve borderline matches. Cross-language English, Sinhala, and Tamil matching requires compatible embeddings, category compatibility, and the higher cross-language semantic threshold. Legacy `lexical-v1` Stories remain eligible candidates.

Embedding values and their model, dimensions, input version/hash, and UTC timestamp are stored only on the private Article document. A matching model/version/hash is reused on replay; changed semantic input regenerates the embedding. Embedding failures retain completed AI enrichment and enter the existing Redis retry/DLQ flow without calling generative AI again.

`Article.storyId` is internal and is not exposed by public DTOs. Story membership updates use an internal article-ID set and conditional MongoDB updates so replay cannot increment `articleCount` twice. Article assignment is an atomic set-if-null operation; the winning MongoDB assignment remains authoritative during concurrent attempts. Story-only changes do not invalidate the public Article feed cache.

Story decisions run in an Atlas transaction. The transaction first increments the private global `hybrid-v1` `story_cluster_partitions` revision, then reloads candidates, creates or selects the Story, assigns the Article, and updates membership. One global partition serializes cross-language decisions as well as same-language decisions. Concurrent decisions produce a MongoDB write conflict; the complete transaction retries at most three times with a fresh snapshot.

A bounded startup embedding backfill processes enriched Articles with missing or stale embedding metadata, then clusters unassigned Articles. The existing bounded clustering backfill remains available. Both are oldest-first and reuse persisted enrichment; neither calls generative AI. Keep the backfill limit conservative and monitor Google AI Studio free-tier quota/rate usage; recurring embedding scheduling is intentionally outside this phase.

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

## Multilingual presentation

Public Article and Story endpoints accept an optional `displayLanguage=en|si|ta` query
parameter. This is separate from the Article list's existing `language` filter: `language`
selects the publisher's original Article language, while `displayLanguage` requests a safe
presentation language. Omitting `displayLanguage` preserves original behavior. Missing or stale
translations fall back to the original title and AI summary.

After enrichment, embedding, and Story clustering, the worker translates only the Article title
and existing AI-generated summary into the other two supported languages. It never sends
`extractedContent` to the translation provider or republishes full publisher content. Original
fields remain authoritative. Private model, prompt version, deterministic input hash, and UTC time
remain internal; public DTOs expose only safe `localizedContent`.

Matching model/prompt/hash translations are reused. A bounded oldest-first startup backfill handles
existing enriched Articles; `MULTILINGUAL_BACKFILL_LIMIT=0` disables it. Translation changes advance
the feed-cache generation, while cache keys isolate Original, English, Sinhala, and Tamil. Story
titles reuse batch-loaded representative Article translations without another AI call. Coverage
topic/entity comparison remains based on original metadata, so chips can remain in mixed source
languages. Timeline ordering and relative times remain unchanged.

## Planned Next Phase

Phase 18 — Supabase Auth
