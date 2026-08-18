# Enterprise Transaction Platform — Architecture & Design Document

**Status:** Draft for review — no code has been written yet.
**Purpose:** Establish architecture, structure, and rationale before implementation begins.

---

## 1. High-Level Architecture

**Style:** Modular Monolith, deployed as a single Spring Boot process, internally partitioned into independently-testable, loosely-coupled modules that each own their own package, their own data access, and communicate with each other only through interfaces (never by reaching into another module's repository or entity).

```
                        ┌─────────────────────────┐
                        │        Nginx            │
                        │  (reverse proxy / TLS /  │
                        │   static frontend host)  │
                        └────────────┬─────────────┘
                                     │
                 ┌───────────────────┼────────────────────┐
                 │                   │                    │
        ┌────────▼────────┐ ┌────────▼─────────┐  ┌───────▼───────┐
        │   React SPA      │ │  Spring Boot App   │  │   Actuator /  │
        │ (served by nginx)│ │  (Modular Monolith) │  │   Metrics     │
        └──────────────────┘ └─────────┬───────────┘  └───────┬───────┘
                                        │                      │
                  ┌─────────────────────┼──────────────────┐   │
                  │                     │                  │   │
           ┌──────▼─────┐      ┌────────▼───────┐   ┌──────▼───▼──────┐
           │ PostgreSQL │      │      Redis      │   │ Prometheus /    │
           │ (system of │      │ (sessions, rate │   │ Grafana / ELK   │
           │  record)   │      │  limit, cache)  │   │ (observability) │
           └────────────┘      └────────────────┘   └─────────────────┘
```

**Why a modular monolith and not microservices:**
- At the scale of this project, microservices add operational cost (service discovery, distributed transactions, network failure handling) without a corresponding benefit — you'd be demonstrating infrastructure plumbing, not engineering judgment.
- A well-modularized monolith is *harder* to do well than a sloppy microservices split, and interviewers know it. It signals you understand coupling and boundaries rather than just YAML and Kubernetes.
- Each module is built so it *could* be extracted: its own package, its own DB schema (not just tables — actual Postgres schemas), no cross-module entity joins, and communication only via internal Java interfaces (a "port") rather than direct repository access. Extracting a module later becomes: swap the internal interface implementation for an HTTP/gRPC client. This is the same idea as a "modulith" (cf. Spring Modulith).

**Key architectural decisions:**
| Decision | Choice | Why |
|---|---|---|
| Module communication | Interface + Spring bean injection, no shared entities across modules | Keeps modules independently extractable |
| Transactions | Single DB, `@Transactional` at service layer, per-module schema | Avoids distributed transaction complexity while preserving logical separation |
| Async work | Spring Events (in-process) for cross-module notification (e.g., Transactions → Notifications) | Decouples modules without needing a message broker for v1; interface is broker-agnostic so Kafka/RabbitMQ can be swapped in later |
| Caching | Redis for refresh tokens, rate-limiting counters, idempotency keys | Real production concern in banking systems (replay/idempotency) |
| API style | REST + OpenAPI, versioned (`/api/v1/...`) | Simple, well understood, easy to document and test |

---

## 2. Folder Structure

```
transaction-platform/
├── backend/
│   ├── pom.xml
│   ├── src/main/java/com/platform/
│   │   ├── PlatformApplication.java
│   │   ├── shared/                     # cross-cutting, no business logic
│   │   │   ├── config/                 # SecurityConfig, WebConfig, OpenApiConfig, RedisConfig
│   │   │   ├── exception/              # GlobalExceptionHandler, ApiError, custom exceptions
│   │   │   ├── security/               # JwtService, JwtFilter, UserPrincipal
│   │   │   ├── audit/                  # AuditEvent publisher interface (impl lives in audit module)
│   │   │   ├── web/                    # CorrelationIdFilter, ApiResponse wrapper
│   │   │   └── validation/             # custom validators (e.g., @StrongPassword)
│   │   ├── auth/
│   │   │   ├── controller/
│   │   │   ├── service/                # AuthService, TokenService
│   │   │   ├── dto/                    # LoginRequest, RegisterRequest, TokenResponse
│   │   │   └── mapper/
│   │   ├── users/
│   │   │   ├── domain/                 # User, Role entities
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   └── mapper/
│   │   ├── accounts/
│   │   │   ├── domain/                 # Account, Beneficiary
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   └── mapper/
│   │   ├── transactions/
│   │   │   ├── domain/                 # Transaction, TransactionStatus, ApprovalRequest
│   │   │   ├── repository/
│   │   │   ├── service/                # TransferService, ApprovalService, LedgerService
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   └── mapper/
│   │   ├── audit/
│   │   │   ├── domain/                 # AuditLog
│   │   │   ├── repository/
│   │   │   ├── service/                # AuditService (listens to domain events)
│   │   │   └── controller/
│   │   └── notifications/
│   │       ├── service/                # NotificationService (email/log stub, pluggable)
│   │       └── listener/               # listens to TransactionCompletedEvent etc.
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-docker.yml
│   │   └── db/migration/               # Flyway: V1__init_schema.sql, V2__..., per-module prefixed
│   └── src/test/java/com/platform/     # mirrors main structure
│
├── frontend/
│   ├── src/
│   │   ├── app/                        # router, providers, layout shells
│   │   ├── features/                   # auth/, accounts/, transactions/, admin/, employee/
│   │   │   └── <feature>/{api,components,hooks,types}
│   │   ├── shared/
│   │   │   ├── components/ui/          # shadcn components
│   │   │   ├── lib/                    # axios instance, query client
│   │   │   └── hooks/
│   │   └── routes/                     # route definitions + guards
│   ├── vite.config.ts
│   └── package.json
│
├── infra/
│   ├── docker-compose.yml
│   ├── nginx/nginx.conf
│   ├── prometheus/prometheus.yml
│   ├── grafana/provisioning/
│   └── elk/{logstash.conf, elasticsearch.yml}
│
├── .github/workflows/ci.yml
└── docs/
    └── architecture-design.md   (this file)
```

**Why this structure:** feature-based packaging (`by module`, not `by layer` at the top level) means a developer working on Transactions never needs to open the Accounts folder. Within each module, layering (`domain/service/controller/dto/mapper`) is consistent, so the codebase is predictable everywhere.

---

## 3. Module Responsibilities

| Module | Owns | Does NOT own |
|---|---|---|
| **auth** | Login, registration, token issuance/refresh, logout, password reset | User profile data (delegates to `users`) |
| **users** | User entity, roles, profile, password change | Authentication mechanics |
| **accounts** | Account entities, balances, beneficiaries | Transaction execution |
| **transactions** | Transfers, approval workflow, transaction history, ledger entries | Account balance schema (calls `accounts` via interface) |
| **audit** | Immutable audit log of security & business events | Business logic — it only listens and records |
| **notifications** | Dispatching notifications on domain events | Any business rules |
| **shared** | Cross-cutting config, security primitives, exception handling, correlation IDs | Any business logic |

Cross-module calls go through a small `port` interface per module (e.g., `AccountLookupPort`) implemented by that module and injected elsewhere — this is what makes the extraction-to-microservice path realistic later.

---

## 4. Database Schema (core tables)

Using PostgreSQL, Flyway-managed, one schema per module (`auth`, `users`, `accounts`, `transactions`, `audit`).

```
users.users
  id (PK, uuid), email (unique), password_hash, first_name, last_name,
  status (enum: ACTIVE, LOCKED, DISABLED), created_at, updated_at, version (optimistic lock)

users.roles
  id (PK), name (unique: CUSTOMER, EMPLOYEE, ADMIN)

users.user_roles (join table)
  user_id (FK), role_id (FK)

auth.refresh_tokens
  id (PK), user_id (FK), token_hash (unique), expires_at, revoked_at, created_at

accounts.accounts
  id (PK, uuid), user_id (FK -> users.users), account_number (unique),
  account_type (enum: CHECKING, SAVINGS), balance (numeric(19,4)),
  status (enum), version (optimistic lock), created_at

accounts.beneficiaries
  id (PK), owner_user_id (FK), beneficiary_account_number, nickname, created_at
  UNIQUE(owner_user_id, beneficiary_account_number)

transactions.transactions
  id (PK, uuid), source_account_id (FK), destination_account_number,
  amount (numeric(19,4)), currency, status (enum: PENDING, APPROVED, REJECTED, COMPLETED, FAILED),
  type (enum: TRANSFER, DEPOSIT, WITHDRAWAL), idempotency_key (unique),
  created_at, approved_by (FK -> users.users, nullable), approved_at

audit.audit_logs
  id (PK, bigserial), correlation_id, actor_user_id, action, entity_type, entity_id,
  metadata (jsonb), created_at (indexed, append-only, no updates)
```

**Design notes:**
- `version` columns give **optimistic locking** on `accounts` and `users` — critical for balance updates under concurrency.
- `idempotency_key` on transactions prevents duplicate transfer submission (a real banking concern, e.g., client retries).
- `audit_logs` is append-only by convention (no service ever issues an UPDATE/DELETE against it) and indexed on `created_at` + `actor_user_id` for the audit dashboard.
- Money is `numeric(19,4)`, never float/double.

---

## 5. API Design

REST, versioned under `/api/v1`, resource-oriented, consistent envelope for errors.

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/forgot-password
POST   /api/v1/auth/reset-password

GET    /api/v1/users/me
PATCH  /api/v1/users/me
POST   /api/v1/users/me/change-password
GET    /api/v1/users             (ADMIN)
PATCH  /api/v1/users/{id}/status (ADMIN)

GET    /api/v1/accounts
GET    /api/v1/accounts/{id}
GET    /api/v1/accounts/{id}/transactions
POST   /api/v1/beneficiaries
GET    /api/v1/beneficiaries

POST   /api/v1/transfers                      (CUSTOMER — creates PENDING transaction)
GET    /api/v1/transactions/{id}
GET    /api/v1/transactions                   (filter by status/date/account)
POST   /api/v1/transactions/{id}/approve       (EMPLOYEE)
POST   /api/v1/transactions/{id}/reject        (EMPLOYEE)

GET    /api/v1/audit/logs                      (ADMIN, filterable)
GET    /api/v1/admin/metrics                   (ADMIN — summarized app metrics)
```

Error envelope (consistent across all endpoints):
```json
{
  "timestamp": "2026-07-28T10:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Amount must be greater than zero",
  "path": "/api/v1/transfers",
  "correlationId": "a1b2c3..."
}
```

OpenAPI (springdoc) generates docs automatically from controller annotations — no hand-maintained spec.

---

## 6. Authentication Flow

1. `POST /auth/login` → validates credentials (BCrypt) → issues:
   - **Access token** (JWT, short-lived, ~15 min, signed HS256/RS256, contains `sub`, `roles`, `jti`)
   - **Refresh token** (opaque random value, stored **hashed** in `auth.refresh_tokens`, long-lived ~7 days)
2. Access token sent as `Authorization: Bearer` header on every request; validated by a `JwtFilter` (no DB hit — stateless).
3. When access token expires, client calls `POST /auth/refresh` with the refresh token; server checks the hash against the DB, confirms not revoked/expired, rotates it (issues a new refresh token, revokes the old — refresh token rotation prevents replay of a stolen token).
4. `POST /auth/logout` revokes the refresh token server-side (access tokens simply expire; for immediate revocation, a Redis-backed denylist of `jti` is used — this is why Redis is in the stack, not just for caching).

**Why opaque refresh tokens instead of a second JWT:** refresh tokens need to be revocable server-side; JWTs are only revocable via a denylist. Storing the refresh token itself as a JWT gives no benefit and adds a second thing to validate. Hashing it at rest means a DB leak doesn't leak usable tokens (same principle as password hashing).

---

## 7. Authorization Flow

- Roles: `CUSTOMER`, `EMPLOYEE`, `ADMIN` — stored in DB, embedded as claims in the JWT at login (not re-fetched per request, for statelessness), but **re-validated from DB on refresh** so a revoked role takes effect within one access-token lifetime (15 min) rather than never.
- **Method-level security** (`@PreAuthorize`) on service methods, not just controller-level — defense in depth, and it means calling a service method directly (e.g., from another module) still enforces authorization.
- Ownership checks (a customer can only see *their own* accounts) are enforced in the service layer by comparing the authenticated principal's user ID against the resource's owner — never trusted from a request parameter.
- Employee approval actions are restricted so an employee **cannot approve a transaction they initiated** (maker-checker principle, standard in banking systems) — enforced in `ApprovalService`.

---

## 8. Logging Strategy

- **Structured JSON logging** (Logback + `logstash-logback-encoder`) — every log line is a JSON object, not free text, so ELK can index fields instead of grepping strings.
- **Correlation ID**: a servlet filter (`CorrelationIdFilter`) generates or propagates an `X-Correlation-Id` header on every request, puts it in the SLF4J MDC, so every log line during that request — across every module — carries the same ID. This is what lets you trace one customer's transfer request across auth → transactions → audit → notifications in Kibana.
- **What gets logged:** request in/out (method, path, status, duration), all `WARN`/`ERROR`, all authentication events, all state transitions on transactions (PENDING → APPROVED → COMPLETED). **What never gets logged:** passwords, full card/account numbers (masked, e.g., `****1234`), JWTs.

---

## 9. Monitoring Strategy

- **Micrometer** as the metrics facade → exported in Prometheus format via **Actuator's** `/actuator/prometheus` endpoint.
- **Custom business metrics**, not just JVM/HTTP defaults: `transactions.created.count`, `transactions.approved.count`, `transactions.failed.count`, `auth.login.failure.count` (tagged by role) — these are what make the Grafana dashboard look like a real ops dashboard rather than a generic Spring Boot template.
- **Grafana dashboards**: one for infra health (JVM heap, GC, DB pool usage via HikariCP metrics, HTTP latency percentiles), one for business metrics (transaction volume, approval queue depth, failure rate).
- **Health checks**: Actuator `/actuator/health` with custom `HealthIndicator`s for DB and Redis, used by Docker Compose healthchecks so dependent services wait for a truly-ready backend, not just a listening port.

---

## 10. Docker Architecture

Single `docker-compose.yml` at the root, one command (`docker compose up`) brings up:

| Service | Image/Build | Notes |
|---|---|---|
| `nginx` | custom, built from `infra/nginx` | serves React build, reverse-proxies `/api` to backend |
| `backend` | multi-stage Dockerfile (Maven build → slim JRE runtime) | depends_on backend healthcheck |
| `frontend` | build stage only — output copied into nginx image (no separate runtime container needed in prod-like mode; a dev-mode override file runs Vite dev server) | |
| `postgres` | official `postgres` image | named volume for data persistence, init via Flyway on app boot |
| `redis` | official `redis` image | |
| `prometheus` | official image, mounted config | scrapes backend `/actuator/prometheus` |
| `grafana` | official image, provisioned dashboards | pre-loaded datasource + dashboards, no manual clicking needed |
| `elasticsearch`, `logstash`, `kibana` | official images | backend logs shipped via Logstash TCP appender |

A `docker-compose.override.yml` is used for local dev (hot reload, exposed DB port for a local DB client, Vite dev server instead of static nginx build) so the base file stays production-shaped.

---

## 11. Deployment Architecture

Since everything must stay free/local, "deployment" here means: a reproducible, CI-validated Docker image, not an actual cloud target. The architecture is still built as if it *would* deploy to ECS/Kubernetes:
- Backend Dockerfile is multi-stage and produces a single, immutable, configuration-externalized image (all config via env vars / Spring profiles — `application-docker.yml` — nothing hardcoded).
- 12-factor: config in environment, stateless backend process (session state in Redis/JWT, not memory), logs to stdout (captured by Docker, shipped to ELK) rather than to a file.
- GitHub Actions build produces and tags the Docker image as a CI artifact, demonstrating the same pipeline a real deployment would use, without needing a paid registry (can push to GHCR for free if you want the artifact to persist).

---

## 12. Testing Strategy

| Layer | Tool | What's covered |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service layer business logic, mapper correctness, validators |
| Repository | Testcontainers (real Postgres) | Query correctness, constraints, optimistic locking behavior |
| Controller/API | `@SpringBootTest` + MockMvc / Testcontainers | Request validation, status codes, error envelope shape |
| Security | Dedicated security tests | Role enforcement (a CUSTOMER token hitting an ADMIN endpoint → 403), JWT expiry/tampering rejected |
| Frontend unit | Vitest | Hooks, utility functions, form validation (Zod schemas) |
| Frontend component | React Testing Library | Rendering, protected route redirects, form submission flows |

Testcontainers is used instead of H2 for repository/integration tests deliberately — H2 doesn't enforce Postgres-specific constraints (e.g., `numeric` precision, real FK behavior), and tests that pass on H2 but fail on Postgres are a classic false-confidence trap.

CI runs the full suite (unit + integration, since Testcontainers works fine in GitHub Actions) on every PR — tests are not "left for later," they're written alongside each module.

---

## 13. Frontend Architecture

- **Feature-folder structure** (`features/accounts`, `features/transactions`, etc.), each owning its own API calls, types, and components — mirrors backend module boundaries so a contributor can reason about one slice at a time.
- **TanStack Query** owns all server state (caching, refetch, invalidation on mutation) — no server data duplicated into a global store; **local UI state only** where needed (form state via React Hook Form, theme via context).
- **Axios instance** with an interceptor that attaches the access token, and a response interceptor that on 401 attempts a silent refresh once, then redirects to login on failure.
- **Zod schemas** shared between form validation and (optionally) generated from OpenAPI types, so frontend and backend validation rules can't silently drift.
- **Role-based routing**: a `<ProtectedRoute roles={[...]}>` wrapper checks the decoded JWT roles before rendering; unauthorized users are redirected, not just hidden via CSS.
- **UX baseline**: shadcn/ui + Tailwind for consistent design tokens, skeleton loaders for every data-fetching view (no blank-screen-then-pop-in), toast notifications for mutation outcomes, an error boundary per route so one broken widget doesn't blank the whole app, dark/light mode via a CSS-variable theme toggle persisted in localStorage.

---

## 14. Key Design Decisions — Summary Rationale

| Decision | Alternative considered | Why this choice |
|---|---|---|
| Modular monolith | Microservices | Lower operational overhead, still demonstrates boundary discipline; extractable later |
| JWT + rotating opaque refresh token | Pure JWT refresh | Server-side revocability without full statelessness loss |
| Optimistic locking on balances | Pessimistic row locks | Better throughput; correct for a system where conflicting transfers on the same account are rare, and retries are cheap |
| Idempotency keys on transfers | Relying on client not to double-submit | Real banking systems must tolerate network retries without double-charging |
| Maker-checker approval | Auto-approval | Reflects real internal banking controls, gives the Employee role genuine purpose |
| Testcontainers over H2 | In-memory DB for tests | Avoids false confidence from DB-dialect differences |
| Structured logging + correlation IDs | Plain text logs | Makes the ELK stack demonstrate something real (traceable request flow) instead of just existing |

---

## Confirmed Decisions

1. **JWT signing: RS256.** Backend generates/holds an RSA key pair (private key signs, public key verifies). Keys are mounted as a secret volume in Docker (generated once via a setup script, `.gitignored`, never committed), loaded via `application-docker.yml` paths. This also means the public key alone could later be shared with another service to verify tokens without trusting it to sign them — the actual point of asymmetric signing in a modular-monolith-to-microservices story.
2. **Notifications: MailHog + DB record.** Every notification-worthy event (registration, password reset, transaction completed, transaction rejected) does two things: (a) writes a row to a `notifications.notifications` table (so the in-app "notifications" UI has real data), and (b) sends an email via MailHog's SMTP (`localhost:1025` in Docker), viewable at MailHog's web UI (`localhost:8025`) — no real email ever leaves the machine.
3. **Seed data: dev-profile only.** A `dev` Spring profile runs a `DataSeeder` (`CommandLineRunner`, guarded so it never runs against `prod`/`docker`-prod profile by mistake) that creates: 1 admin, 2 employees, 3 customers (with realistic account numbers/balances), a mix of completed/pending/rejected sample transactions, and corresponding audit log entries — so `docker compose up` is demo-ready with zero manual steps. Seeding is idempotent (checks if data exists before inserting) so restarting the stack doesn't duplicate data.
