# Sri Lankan News Intelligence Platform — Backend

Spring Boot REST API for the Sri Lankan News Intelligence Platform. The platform will collect and organize reporting from multiple Sri Lankan publishers while directing readers to the original journalism.

## Current Phase

**Phase 1 — Requirements + Architecture Foundation**

This phase establishes backend conventions, configuration, error handling, request correlation, MongoDB connectivity, and test infrastructure. It intentionally contains no news business domains or ingestion behavior.

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
- MongoDB, either locally installed or run through Docker Compose
- Docker Desktop (optional, for the provided local MongoDB service)

## Local Setup

1. Copy `.env.example` to `.env` if you want a local reference for environment values. Spring Boot does not load `.env` files automatically; export the variables in your shell or configure them in your IDE.
2. Start MongoDB locally. With Docker:

   ```bash
   docker compose up -d mongodb
   ```

3. Set `MONGODB_URI` if the default local URI is not suitable.

   PowerShell:

   ```powershell
   $env:MONGODB_URI = "mongodb://localhost:27017/sri_lanka_news"
   ```

The development default is `mongodb://localhost:27017/sri_lanka_news`. It is intended only for a local MongoDB instance without authentication.

## Environment Variables

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `MONGODB_URI` | No for local development | `mongodb://localhost:27017/sri_lanka_news` | MongoDB connection URI. Use an environment-specific secret outside local development. |

Never commit real credentials or a populated `.env` file.

## Running

```bash
mvn spring-boot:run
```

The application listens on `http://localhost:8080` by default. The deliberately limited Actuator health endpoint is available at:

```text
GET /actuator/health
```

## Testing

Run the automated tests:

```bash
mvn test
```

Create the executable application package:

```bash
mvn clean package
```

Simple tests do not require a running MongoDB instance. MongoDB health checks are disabled only in the test profile.

## Architecture Conventions

- Application APIs use the `/api/v1` base path. No business endpoints exist in Phase 1.
- Packages are introduced by feature. Shared cross-cutting code belongs in `common`; application configuration belongs in `config`.
- Controllers use request/response DTOs and never expose MongoDB persistence documents directly.
- Controllers stay thin, services own business logic, and repositories only handle persistence.
- Bean Validation is applied at API boundaries.
- REST errors use one centralized structure containing a timestamp, HTTP status, stable application code, safe message, request path, request ID, and optional field details.
- Requests accept a safe `X-Request-ID` value or receive a generated one. The ID is returned in the same response header and included in application logs.
- Normal HTTP status codes and response bodies are preferred over a generic success envelope.
- List endpoints will use zero-based `page`, `size`, and `sort=field,direction`. The initial convention is `page=0`, `size=20`, with a maximum size of `100`; enforcement begins when paginated endpoints are introduced.
- Configuration is externalized through Spring Boot properties and environment variables. Secrets must not be stored in source control.

## Planned Next Phase

**Phase 2 — Source + Article Domain Foundation**

Phase 2 is documented here only; it has not been implemented.
