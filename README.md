# Lexbridge Legal Case Management

Lexbridge is a production-style legal matter workspace built with Java 21, Spring Boot 3, FreeMarker, and PostgreSQL.

## Run

```bash
docker compose up --build
```

Open <http://localhost:8080>. The first startup migrates and seeds the database.

| Role | Email | Password |
|---|---|---|
| Firm admin | `admin@lexbridge.test` | `Lexbridge!2026` |
| Attorney | `attorney@lexbridge.test` | `Lexbridge!2026` |
| Paralegal | `paralegal@lexbridge.test` | `Lexbridge!2026` |
| Client | `client@lexbridge.test` | `Lexbridge!2026` |

## Architecture and trust

`channel` authenticates users and owns the browser session. It forwards only an opaque signed session token. `decision` authorizes every operation independently, resolves the token, and reloads role, scope, account state, and assignments from its own PostgreSQL tables for every request. It ignores identity or role fields supplied by clients. `core-mock` is a separate local endpoint; it accepts only its service credential plus a scope token minted by `decision`, verifies the token, and enforces its scope. No hosted services are required.

`decision` owns matters, clients, documents, versions, tasks, deadlines, notes/messages, notifications, imports, exports, API keys, webhook attempts, outbox mail, reference settings, sessions, and append-only audit events. Uploaded bytes live under the canonicalized local `uploads` directory; metadata is in PostgreSQL.

## Development

```bash
./gradlew test
./gradlew build
```

Set `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SESSION_SIGNING_KEY`, and `CORE_CREDENTIAL` outside local development. CSRF protection, FreeMarker HTML auto-escaping, secure headers, DTO-only binding, constrained uploads, BCrypt passwords, time-bound single-use reset tokens, authorization at controller and service boundaries, explicit state transition edge lists, and append-only audits are enabled.

## Main workflows and UI

Role-aware dashboards lead to matter intake/conflict review, assigned work queues, document versioning and sharing, deadlines, internal privileged notes, client messages, client upload, reporting, imports, user administration, settings, and audit review. The responsive “case ledger” visual system uses navy surfaces, cobalt actions, amber deadline cues, high-contrast typography, status text plus color, visible focus rings, and an evidence-strip activity timeline.

## Completeness

All specified local workflows are implemented. External mail, webhook, and core-system interactions are recorded local mocks rather than SaaS calls.
