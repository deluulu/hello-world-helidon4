# Helidon SE + Supabase Integration

A REST microservice built with [Helidon SE 4](https://helidon.io) backed by [Supabase](https://supabase.com) PostgreSQL. Demonstrates CRUD over a `profiles` table with structured JSON logging, per-request observability, and centralised error handling.

---

## Stack

| Layer | Technology |
|---|---|
| Runtime | Helidon SE 4.1.6 |
| Database | Supabase (PostgreSQL via JDBC) |
| Connection pool | HikariCP 5.1.0 |
| Logging | Logback + logstash-logback-encoder (structured JSON) |
| Build | Maven |
| Java | 21+ |

---

## Project structure

```
src/main/java/co/yogeesh/helidon/
├── Application.java                        # Entry point, routing wiring
├── exceptions/
│   └── GlobalExceptionHandler.java         # Catches all unhandled exceptions
├── filters/
│   └── ObservabilityFilter.java            # Per-request MDC + access log
├── service/
│   └── ProfileService.java                 # CRUD handlers for /profiles
└── supabase/
    └── HikariConnectionPoolProvider.java   # HikariCP pool for Helidon DbClient
```

---

## Prerequisites

- Java 21+
- Maven 3.9+
- A Supabase project with the `profiles` table (see [Database setup](#database-setup))

---


## Configuration

All configuration lives in `src/main/resources/application.yaml`. Credentials are injected via environment variables — never committed.

```yaml
server:
  port: 8080
  host: "0.0.0.0"

db:
  source: "jdbc"
  connection:
    url: "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
    username: "${DB_USER}"
    password: "${DB_PASSWORD}"
    init-pool-size: 2
    max-pool-size: 10
    connection-timeout: 10000
```

| Variable | Description |
|---|---|
| `DB_USER` | Supabase database user (e.g. `postgres.xxxx`) |
| `DB_PASSWORD` | Supabase database password |

Find both in your Supabase project under **Project Settings → Database → Connection string**.

---

## Running locally

```bash
export DB_USER=postgres.xxxxxxxxxxxx
export DB_PASSWORD=your-db-password

mvn package -q
java -jar target/helidon-supabase.jar
```

The server starts on port `8080`.

---

## Observability

### Per-request logging (`ObservabilityFilter`)

Every request is assigned trace context and logged twice — on arrival and on completion:

```
→  GET /profiles
←  GET /profiles -> 200 (12ms)
```

Headers accepted for trace propagation:

| Header | Behaviour |
|---|---|
| `X-Trace-Id` | Used as `traceId`; auto-generated UUID if absent |
| `X-Correlation-Id` | Used as `correlationId`; falls back to `traceId` if absent |
| `X-User-Id` | Included in MDC as `userId` when present |

`spanId` is always generated fresh per request.

### Structured JSON logs (Logback)

Every log line is emitted as a JSON object. MDC fields set by `ObservabilityFilter` are included automatically on every log statement within that request:

```json
{
  "@timestamp": "2026-05-17T10:00:00.000Z",
  "level": "INFO",
  "message": "GET /profiles -> 200 (12ms)",
  "app": "helidon-supabase",
  "traceId": "4d3a1b2c...",
  "spanId": "a1b2c3d4e5f6a7b8",
  "correlationId": "4d3a1b2c...",
  "userId": "user-uuid"
}
```

### Error handling (`GlobalExceptionHandler`)

| Exception | HTTP status | Client message |
|---|---|---|
| `IllegalArgumentException` | 400 | Exception message |
| `NoSuchElementException` | 404 | Exception message |
| Anything else | 500 | `"Internal server error"` (detail logged, not exposed) |

---


## Key design decisions

**HikariCP over Helidon's built-in pool** — Supabase requires SSL (`sslmode=require`) and connection-level tuning that is simpler to express through HikariCP's API. `HikariConnectionPoolProvider` plugs into Helidon's `DbClient` via the `JdbcConnectionPoolProvider` SPI.

**Logback over JUL** — `helidon-logging-slf4j` bridges Helidon's internal JUL logs to SLF4J, so `logback-classic` receives all log output. `logstash-logback-encoder` serialises every line as a single JSON object, ready for ingestion by log aggregators (Datadog, Loki, CloudWatch, etc.).

**MDC set at the filter layer** — `ObservabilityFilter` populates trace context before any handler runs and clears it in a `finally` block, guaranteeing every log statement within a request carries the full correlation metadata regardless of which code path executes.
