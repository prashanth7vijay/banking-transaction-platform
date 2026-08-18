# Transaction Platform

Enterprise-style banking transaction platform (modular monolith). See `docs/architecture-design.md` and `docs/development-plan.md` for full design rationale and phase-by-phase build plan.

## Prerequisites
- Docker Desktop
- Java 21 (for local backend dev outside Docker, optional)
- Node 20 (for local frontend dev outside Docker, optional)
- OpenSSL (for key generation)

## Setup

```bash
./scripts/generate-keys.sh   # one-time: generates the RS256 key pair into ./keys
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Backend health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- MailHog UI: http://localhost:8025
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001 (admin/admin, or anonymous viewer access is enabled)
- Kibana: http://localhost:5601 (see the Phase 7 note below - one manual step required)

## Status

**Phase 0 complete:** project skeleton, Docker Compose stack, structured JSON logging, global exception handling, baseline security config, Flyway schema baseline, CI pipeline, React shell with dark/light theme and a live backend-health page.

**Phase 1 complete:** `users` + `auth` modules.
- RS256 JWT access tokens, rotating opaque refresh tokens (httpOnly cookie), Redis-backed logout denylist
- Endpoints: register, login, refresh, logout, forgot-password (MailHog email), reset-password, `GET/PATCH /users/me`, change-password
- Demo accounts seeded in `dev` profile: `admin@platform.local` / `employee@platform.local` / `customer@platform.local`, password `Password123`
- Frontend: login, register, forgot/reset password pages, auth context with silent-refresh-on-load, protected route guard, profile page (edit details + change password), auth-aware nav

**Phase 2 complete:** `accounts` module.
- `Account` (optimistic locking, balance check constraint) + `Beneficiary` entities, ownership enforced in the service layer (never trusted from a request param)
- Endpoints: list my accounts, account detail, list/add/remove beneficiaries — all `@PreAuthorize("hasRole('CUSTOMER')")`
- Demo customer seeded with a Checking ($2,500) and Savings ($10,000) account
- Frontend: dashboard with account cards, account detail page, beneficiary management (add/remove) with validation, Dashboard link in nav for customers

**Phase 3 complete:** `transactions` module — the core transfer + approval workflow.
- `Transaction` entity with a 4-state machine (PENDING → COMPLETED/FAILED/REJECTED), idempotency-key-based duplicate-submission protection, amount-positive DB constraint
- `accounts` now exposes an `AccountLookupPort` (the module-boundary interface the architecture called for) — `transactions` calls this, never `AccountRepository`/`Account` directly
- Fund movement (debit + credit) happens atomically inside `ApprovalService.approve()`, re-validating balance at approval time (not at submission time) since balance can change while a transfer is pending
- Maker-checker enforced: an employee can never approve/reject their own initiated transaction (returns 403)
- Domain events (`TransactionStatusChangedEvent`) published on completion/rejection for `audit`/`notifications` to consume from Phase 4 onward — no listener yet, that's next phase
- Demo data: a sample $150 pending transfer seeded for the demo customer, ready to approve/reject immediately
- Frontend: transfer form (account picker, destination, amount, note), transaction history with status badges, employee approval queue with approve/reject actions

**Phase 4 complete:** `audit` module.
- Append-only `AuditLog` entity (JSONB metadata via Hibernate's native JSON mapping, no extra dependency), correlation ID captured from MDC so an entry can be cross-referenced with the request logs
- `AuditEventListener` is the only consumer of `AuthActionEvent`, `UserActionEvent`, and the `transactions` module's `TransactionStatusChangedEvent` — `audit` depends on those event types (read-only, one-directional), nothing depends on `audit`
- Now-audited actions: login, logout, password reset, password change, and every transaction state change (submitted, completed, rejected, failed)
- Admin-only filterable endpoint (`actorUserId` / `action` / `entityType`) and dashboard table

**Phase 5 complete:** `notifications` module.
- `Notification` entity (in-app record) + MailHog email, both sent from one `NotificationService.notify()` call — email delivery is best-effort (a MailHog hiccup logs a warning but doesn't fail the request that triggered it)
- Added a `UserLookupPort` in `users` (same module-boundary pattern as `accounts`' `AccountLookupPort`) so `notifications` can resolve a recipient's email without touching `UserRepository` directly
- `NotificationEventListener` subscribes to the same `UserActionEvent`/`TransactionStatusChangedEvent` events `audit` already consumes — deliberately notifies only on the outcomes a customer cares about (transfer completed/rejected/failed, password changed), not on submission or login
- Endpoints: list mine, unread count, mark as read
- Frontend: notification bell in the header with unread badge, dropdown list, click-to-mark-read, closes on outside click

**Phase 6 complete:** Admin user/role management + nav/UX polish.
- `AdminUserService` (search/status/roles) + `AdminUserController` at `/api/v1/admin/users` — self-lockout guarded: an admin can never change their own status or roles (must ask another admin)
- `AdminMetricsController` at `/api/v1/admin/metrics` — a thin aggregator composing existing read methods on `AdminUserService`/`AccountService`/`TransferService`; it owns no table or repository of its own
- Frontend: Admin Overview (user/account/transaction summary cards), User Management (search, inline role-toggle chips, status dropdown), all three roles now have a complete nav (Overview/Users/Audit Log for Admin)
- Added a route-level `ErrorBoundary` around the layout's `Outlet` (resets on navigation) so one broken page doesn't blank the whole app

**Scope note, flagged rather than silently skipped:** the original plan called for an automated RBAC matrix test (every endpoint × every role). Per your instruction to prioritize building over test suites during core development, I did not write that suite now — it's deferred to Phase 8 (hardening), where it belongs alongside the rest of the real test coverage pass.

**Phase 7 complete:** Observability.
- Custom Micrometer counters wired into `auth` (`platform.auth.login{result}`) and `transactions` (`platform.transactions.created{type}`, `platform.transactions.approved{outcome}`, `platform.transactions.rejected`) — exported automatically via the Actuator `/actuator/prometheus` endpoint already exposed since Phase 0
- Prometheus scrapes the backend every 10s; Grafana auto-provisions the Prometheus datasource and a dashboard (`Transaction Platform Overview`) with HTTP request rate, JVM heap, and the four business-metric panels above — no manual dashboard setup needed
- Logs ship two ways now: console (JSON, `docker logs`) and a Logstash TCP appender → Elasticsearch, both carrying the same correlation ID field for cross-referencing
- Kibana is up and reachable, but creating the `transaction-platform-logs-*` index pattern and a saved search is a one-time manual step (see note below) — I didn't script Kibana's saved-object API for this, since doing so reliably needs Kibana to be fully initialized first and adds meaningful complexity for something a person does once in under a minute

**Manual one-time Kibana step** (to see logs / trace a request by correlation ID):
1. Open http://localhost:5601 → Stack Management → Index Patterns → Create index pattern
2. Pattern: `transaction-platform-logs-*`, time field: `@timestamp`
3. Go to Discover, pick that index pattern, and search `correlationId: "<value>"` (copy a value from the `X-Correlation-Id` response header on any API call, or from a log line) to see every log line from that single request traced across modules

**Phase 8 complete:** Hardening.

*Security:*
- Fixed a real gap while writing the RBAC test: without an explicit `AuthenticationEntryPoint`, Spring Security's default 401-vs-403 behavior for a missing/invalid token in a stateless API is easy to get wrong. Added `SecurityResponseHandlers` so a missing/invalid token always returns 401 and an authenticated-but-wrong-role request always returns 403, both in the same `ApiError` JSON shape the rest of the API uses.
- Added explicit HSTS, `X-Content-Type-Options: nosniff`, and a same-origin referrer policy alongside the CSP/frame-deny headers already in place since Phase 0.
- Dependency scanning: `.github/dependabot.yml` (Maven, npm, both Dockerfiles, GitHub Actions — weekly), a CodeQL workflow (Java + TypeScript, on push/PR/weekly schedule), and `npm audit --audit-level=high` added as a real CI gate in the frontend job.

*Test coverage (the deferred items from Phases 3, 4, and 6):*
- **RBAC matrix test** (`RbacMatrixIT`) — the item explicitly deferred from Phase 6. Covers a representative endpoint from every module (`/users/me`, `/accounts`, `/transactions/pending`, `/transactions/{id}/approve`, `/admin/users`, `/audit/logs`) across no-token / wrong-role / correct-role, asserting the security gate specifically (401/403), not what the business logic does afterward.
- **Maker-checker test** (`ApprovalServiceTest`) — proves the self-approval 403 the Phase 3 note flagged as implemented-but-not-UI-triggerable; also covers the FAILED-on-insufficient-funds path and the already-decided-transaction guard.
- **Optimistic locking test** (`AccountOptimisticLockingIT`) — real Postgres via Testcontainers (not H2, deliberately — Postgres's locking/typing semantics are what the code is actually written against), proving two concurrent balance updates on the same account produce `ObjectOptimisticLockingFailureException`, not a silent lost update.
- **JWT tests** (`JwtServiceTest`) — valid round-trip, tampered-signature rejection, expiry rejection, against a dedicated test-only RSA keypair in `src/test/resources/keys` (never used outside tests).
- **AuthService / TransferService / AdminUserService unit tests** — login success/failure metric recording, idempotent-replay behavior, same-account-transfer rejection, and the admin self-lockout guard from Phase 6.
- Added `maven-failsafe-plugin`, bound to `integration-test`/`verify` — without it, the `*IT.java` Testcontainers tests would compile but silently never run under `mvn verify` (Surefire's default include pattern doesn't match `*IT.java`). Surefire is now scoped to `*Test.java` (fast, no Docker needed) and Failsafe to `*IT.java` (needs Docker, runs in CI where it's available).

*Performance:*
- Explicit HikariCP pool sizing (`maximum-pool-size: 20`, `minimum-idle: 5`, sane timeouts) instead of relying on unstated defaults.
- N+1 check on transaction history: there isn't one to fix. `Transaction` was designed from Phase 3 with plain UUID foreign keys (`sourceAccountId`, not a `@ManyToOne Account`), so there's no lazy-association fetch to trigger N+1 in the first place — an architectural side benefit of the module-boundary/port pattern, not a query optimization bolted on afterward.

*Pagination (the deferred item from Phase 4):* the audit log endpoint now returns a real `Page` (`PageResponse<T>`, backed by a proper Spring Data `Page` with a matching count query) instead of a flat list capped at 200 — frontend has Previous/Next controls and a total-count display.

**What I did not do, and why:** I did not add a literal architecture diagram image to this README — I don't have a way to generate and verify a real diagram image in this environment, and a fabricated one would be worse than none. `docs/architecture-design.md` (copied into this repo since Phase 0) has the full ASCII component diagram and design rationale, which covers the same ground in a form I can actually stand behind. Screenshots are the same story — they need to come from your own running instance, not be invented here.

**Repository status:** all 9 phases (0–8) are complete. This is the final planned phase; the project is in the state described throughout this README, with the caveats noted phase-by-phase above (all resolved except the two noted immediately above, which need your input or your own running instance, not more code).

---

## Post-hardening addendum: account opening was missing

An audit of the `accounts` module (post Phase 8) found that **registration never created an `Account`** — only `DataSeeder` did, and only for the one hardcoded demo customer. Any real registered user landed on an empty Dashboard forever, with no way for anyone to open an account for them. This directly contradicts the original brief, which scoped **"Manage Accounts" as an Employee responsibility**.

Fixed, following the existing module-boundary conventions:
- `AccountService.openAccountForCustomer()` validates the target is actually a CUSTOMER via the existing `UserLookupPort` (extended with a `roles` field) before opening anything — `accounts` never touches `UserRepository` directly, same pattern as every other cross-module call in this codebase
- `EmployeeCustomerController` (`GET /api/v1/employee/customers?search=`) and `EmployeeAccountController` (`POST /api/v1/employee/accounts`), both `@PreAuthorize("hasRole('EMPLOYEE')")`
- A new `AccountOpenedEvent`, consumed by the existing `AuditEventListener` — same event-bus convention as every other audited action since Phase 4
- Frontend: `/employee/accounts` — search a customer, pick one, open a Checking or Savings account with a starting balance

**Deliberately not changed:** registration still creates a `User` only, no auto-created account — that's the correct behavior for an internal transaction platform (bank-teller model), not a bug. `DataSeeder`'s own account-creation logic was left untouched rather than refactored to call the new service method, to avoid risking already-verified seed behavior for a cosmetic cleanup.

---

## Enterprise evolution — Phase 1: Ledger Foundation

Additive-only, backend-only, **zero visible behavior change** — see `docs/enterprise-evolution-architecture.md` §5 and `docs/enterprise-development-plan.md` Phase 1 for the full design rationale.

- New `ledger` schema/module: `LedgerAccount` (materialized balance projection, optimistic-locked, mirrors the existing `Account` pattern), `JournalEntry`/`JournalEntryLine` (double-entry, append-only by convention, `DEBIT = CREDIT` enforced in code before anything is persisted — `LedgerService.post()` rejects an unbalanced entry with no trace left behind)
- `V8__ledger_schema.sql` — three tables, no changes to any existing table
- Every account now gets real ledger coverage: **going forward**, `AccountOpenedLedgerListener` opens one automatically whenever `AccountOpenedEvent` fires (no code change needed in `accounts` itself); **retroactively**, `LedgerBackfillRunner` (ordered to run after `DataSeeder`, idempotent, safe on every boot) backfills any pre-existing account, posting an opening-balance entry against a well-known system clearing ledger account
- `AccountLookupPort` gained one narrow addition — `findAll()` — used only by the backfill runner, so `ledger` never touches `AccountRepository` directly
- Not wired into the transfer/approval flow yet — `ApprovalService` does not post to the ledger this phase. That's Phase 2 (dual-write), the next step in the plan.

**Exit criteria met:** `docker compose up`, every existing page and flow behaves identically; querying `ledger.ledger_accounts`/`ledger.journal_entries` directly shows real, balanced ledger coverage for every seeded account.

---

## Enterprise evolution — Phase 2: Ledger Dual-Write

Backend-only. **No visible behavior change** — an approved transfer looks and works exactly as before. See `docs/enterprise-development-plan.md` Phase 2.

- `ApprovalService.approve()` now posts a real, balanced ledger entry (via the new `LedgerPostingPort`) immediately after the existing debit/credit calls, inside the same `@Transactional` method — the debit, the credit, and the ledger post all commit together or none do
- New `LedgerPostingPort`/`LedgerPostingPortImpl` (mirrors `AccountLookupPort`): `transactions` calls this, never `LedgerAccountRepository` directly
- A manual, on-demand reconciliation check (`GET /api/v1/admin/ledger/reconciliation-check`, ADMIN-only) — a deliberately lightweight precursor to the full scheduled reconciliation job planned for a later phase
- `LedgerDualWriteIT` (Testcontainers, real Postgres) proves the actual guarantee: after a real transfer approval, `accounts.accounts.balance` and `ledger.ledger_accounts.ledger_balance` are numerically identical, for both accounts involved

**Exit criteria met:** existing transfer/approval flow is unchanged from every angle except one — every completed transfer now has a real, balanced journal entry backing it, and `GET /admin/ledger/reconciliation-check` reports zero mismatches.

---

## Enterprise evolution — Phase 3: Risk Engine

Backend-heavy, one small frontend touch. Ships with deliberately permissive default limits, so **no existing transfer stops working**. See `docs/enterprise-development-plan.md` Phase 3.

- New `risk` module: `LimitPolicy` (data-driven rules — PER_TRANSACTION, DAILY_CUMULATIVE, VELOCITY_COUNT — an ops team edits these without a deployment, same reasoning as `users.roles` being a table), `RiskAssessment` (one per transaction, append-only)
- `RiskStrategy` interface + `RuleBasedRiskStrategy` (the only implementation today) — a deliberate seam for a future ML-based strategy to run alongside it later, not a "pattern showcase"
- Two new ports, one in each direction: `TransferHistoryPort` (`transactions` → `risk`, narrow aggregate reads only) and `RiskAssessmentPort` (`risk` → `transactions`) — two peer modules each needing something from the other, expressed as two one-directional contracts, never a direct service-to-service reach
- `TransferService.createTransfer()` now runs a synchronous risk check before returning — a `blocked` result short-circuits straight to `FAILED` with a clear reason; everything else proceeds exactly as before
- `RiskPolicySeeder` seeds generous defaults ($50k per-transaction, $100k daily, 100 transfers/10min) on first boot, in every profile — real config, not demo data, and never overwrites a policy an admin has since changed
- Admin CRUD (`/api/v1/admin/risk/policies`) — backend only this phase; the admin config screen is a later phase, per the plan
- **UI:** a small `RiskBadge` component (same visual language as the existing `StatusBadge`, no new design system) now shows on the Approval Queue — the one place an employee actually needs to see risk before deciding. Deliberately not added everywhere, to keep this phase's footprint small
- `RuleBasedRiskStrategyTest` — each limit type in isolation, plus a combined-rules test proving the highest-severity triggered rule wins

**Simplification noted honestly:** only `GLOBAL`-scoped policies are evaluated this phase — the schema already supports `ACCOUNT_TYPE`/`CUSTOMER`/`ACCOUNT` scopes for a later phase's most-specific-wins resolution, but that resolution logic isn't built yet.

**Exit criteria met:** every transfer now has a real risk assessment; nothing that worked before Phase 3 stops working, since the seeded policies are generous by design.

---

## Enterprise evolution — Phase 4: Transaction State Machine

Backend-heavy, one small frontend touch (type/color extensions only). See `docs/enterprise-evolution-architecture.md` §6 and `docs/enterprise-development-plan.md` Phase 4.

- `TransactionStatus` expands from four values to the full nine-state lifecycle (`DRAFT, SUBMITTED, PENDING_APPROVAL, APPROVED, PROCESSING, COMPLETED, REJECTED, FAILED, CANCELLED`) — `PENDING` is gone, replaced at creation by `SUBMITTED`
- New `TransactionStateMachine` service: `isLegal(from, to)` is a pure, static, dependency-free check against a `Map<TransactionStatus, Set<TransactionStatus>>` built once at class-load (no state-machine framework — evaluated and rejected as unnecessary weight for a table this size); `transition(...)` is what services actually call — re-validates legality, executes a DB compare-and-swap (`TransactionRepository.compareAndSetStatus`, the concurrency guard from §16), records the edge, and publishes the new `TransactionStateChangedEvent`
- New `transaction_state_history` table (`V11__transaction_state_machine.sql`), written directly by the state machine in the same DB transaction as the transition — this, not event replay, is what backs the new `GET /transactions/{id}/timeline` endpoint, since events aren't queryable after the fact
- `TransferService.createTransfer()` now creates `SUBMITTED`, then the risk check moves it to `PENDING_APPROVAL` or straight to `FAILED`; `ApprovalService.approve()` cascades `PENDING_APPROVAL → APPROVED → PROCESSING → COMPLETED/FAILED` in one still-synchronous call, `reject()` does `PENDING_APPROVAL → REJECTED`
- The old `TransactionStatusChangedEvent` is kept exactly as-is in shape and still fires only at the same points it always did (existing `audit`/`notifications` listeners needed no logic changes, just a value update — `PENDING` → `PENDING_APPROVAL` in `NotificationEventListener`'s skip-notify guard, `TRANSACTION_PENDING` → `TRANSACTION_PENDING_APPROVAL` in the audit dashboard's filter list)
- `TransactionStateMachineTest` — exhaustively parameterized over the full 9×9 = 81 `(from, to)` cross-product, plus targeted invariant tests (no self-transitions, terminal states have zero legal outgoing edges)

**Simplification noted honestly:** the `APPROVED → PROCESSING` and `PROCESSING → COMPLETED/FAILED` edges are "system" transitions per §6's table (no separate human trigger) — this codebase has no system-actor concept yet, so they're attributed to the approving employee for traceability rather than left blank. `CANCELLED` and the `DRAFT`/`*→CANCELLED` edges are implemented and tested in the transition table for completeness but have no caller yet — no cancel endpoint exists (none is planned in the enterprise plan either).

**Migration numbering note:** this phase's migration claims `V11`, which `enterprise-development-plan.md` had originally earmarked for Phase 5's `approvals_schema`. Phase 5 will use `V12` instead.

**Data-safety note:** `V11` also migrates any pre-existing `status = 'PENDING'` row to `PENDING_APPROVAL` (its exact semantic successor) — without this, a persisted docker volume from before this phase would fail `@Enumerated(EnumType.STRING)` deserialization on first read, since `PENDING` no longer maps to any `TransactionStatus` constant.

**Exit criteria met:** `docker compose up`, every existing transfer/approval/reject flow behaves identically end-to-end from the outside; `transaction_state_history` shows a complete, correctly-ordered transition log for every transaction; `TransactionStateMachineTest` proves the transition table matches §6 exactly.

---

## Product Enhancement Phase — Feature 1: Transaction 360

Reprioritized after Phase 4: rather than continuing the original enterprise-evolution phase sequence (multi-level approvals, reconciliation, etc.), the project pivoted to five product-depth features focused on operational usability. See the top-level product enhancement brief for full context.

- New `GET /api/v1/transactions/{id}/360` endpoint - a pure aggregator over `transactions`, `risk`, `ledger`, `accounts`, and `users`, composing what those modules already separately know about one transaction into a single read (summary, lifecycle, risk explainability, approval decision, ledger postings, full audit timeline). No new business logic, no new source of truth.
- Two small additive port extensions made this possible: `RiskAssessmentPort.getAssessment()` (full reasons/level/blocked, alongside the existing lightweight `getRiskLevel()`) and `LedgerPostingPort.getJournalLinesForTransaction()` (batched, no N+1).
- Frontend: new `TransactionDetailPage` at `/transactions/:id`, with a `LifecycleStepper` driven by the actual persisted `transaction_state_history` (Phase 4) rather than guessed from the current status alone - this is what correctly tells apart, e.g., a transfer FAILED at risk evaluation from one FAILED at posting.
- Existing transaction history and approval queue rows now link into this page.

**Exit criteria met:** opening any transaction shows its complete operational picture without navigating through separate risk/ledger/approval screens; nothing about the existing transfer/approval flow changed.

---

## Product Enhancement Phase — Feature 2: Exception & Investigation Management

The one genuinely new module this phase - own schema (`exceptions`), own entities, own lifecycle. Auto-triggered, not a status flag.

- New `exceptions` schema (`V12__exceptions_schema.sql`): `transaction_exceptions` (lifecycle: OPEN → ASSIGNED → INVESTIGATING → ACTION_REQUIRED → RESOLVED → CLOSED, plus priority and a computed SLA deadline) and append-only `exception_notes` (the full investigation trail - notes, assignments, status changes, actions).
- `ExceptionAutoCreationListener` subscribes to Phase 4's `TransactionStateChangedEvent` (published on every transition since Phase 4, but unused until now) and opens a case automatically whenever a transaction reaches `FAILED` - with a real, derived reason and priority that correctly distinguish a risk-blocked failure from a posting-time failure, using the actual state transition that occurred rather than guessing.
- Two new narrow ports added to `transactions` for `exceptions` to depend on - `TransactionLookupPort` (read) and `TransactionRetryPort` (the one write capability exposed: retrying a failed transfer). The dependency only ever runs one way (`exceptions` → `transactions`); `transactions` has no idea the `exceptions` module exists.
- **Retry**, done properly: re-runs the original customer's request through the entire normal pipeline again as a brand-new transaction (fresh risk assessment, fresh approval if required) - it never resurrects or mutates the failed transaction. This is the concrete implementation of the product brief's "never solve a financial problem by editing the original transaction record" principle.
- `ExceptionController` (`/api/v1/exceptions`, EMPLOYEE-only) - queue, "assigned to me", investigation workspace, and every lifecycle action (assign/claim, start investigating, request information, resolve, close, escalate, retry, add note).
- New `EmployeeDirectoryController`/`EmployeeDirectoryService` (mirrors the existing `CustomerDirectoryService` exactly) so the assignment picker in the UI is a real employee search, not a hardcoded list.
- Frontend: `ExceptionQueuePage` (Open/Mine/All views) and `ExceptionDetailPage` (the investigation workspace, with the full action set and note trail).

**Architecture decision:** Transaction 360 does not embed exception data, and the investigation workspace does not embed the full Transaction 360 - the backend dependency stays one-directional to avoid a cycle. The frontend composes the two independent reads at the page level instead (Transaction 360 conditionally calls `GET /exceptions/by-transaction/{id}` only for FAILED transactions viewed by an employee; the investigation workspace links out to `/transactions/{id}` for the full picture).

**Simplification noted honestly:** "Request Information" is internal-only (a status change + note) - no customer-facing notification flow exists for it, since building one would mean inventing a new customer communication channel outside this phase's scope.

**Exit criteria met:** a transaction that fails now automatically produces a real, prioritized, SLA-tracked case with an accurate reason; an operator can claim it, investigate it, retry the underlying transfer without ever touching the original record, and resolve/close it, with every action captured in an append-only trail.

---

## Product Enhancement Phase — Feature 3: Approval Workbench + SLA

Extends the existing maker-checker approval flow rather than changing it - every transfer still requires exactly one employee approval, same as before this phase. What's new is *explaining* and *prioritizing* that queue, not changing who has to approve what.

- `sla_due_at` (`V13__approval_sla.sql`) - set once, on `TransferService.createTransfer()`, the moment a transfer enters `PENDING_APPROVAL`, from a fixed risk-level-based window (CRITICAL 1h / HIGH 4h / MEDIUM 24h / LOW 48h - deliberately simple, explainable thresholds, not a model). Never recomputed later; SLA status (WITHIN / AT_RISK / BREACHED) is derived at read time from that fixed deadline.
- New `ApprovalWorkbenchService` - a read-only aggregator, same pattern as `Transaction360Service` - composing the existing risk assessment reasons with one new signal: how a transfer compares to the customer's own average completed transfer amount (`TransactionRepository.findAverageAmountByStatus`), surfaced as an explicit reason only when notably large (≥2×).
- **Honesty note on "why does this need approval":** this system's maker-checker control is universal - every transfer requires approval, not just ones crossing a threshold. The workbench doesn't pretend otherwise; the "why" panel always states that baseline plainly, then adds whatever risk signals or notable-amount context actually exist for that specific transfer, rather than fabricating a threshold-based justification the system doesn't actually enforce.
- New endpoints: `GET /transactions/pending/workbench` (the enriched queue) and `GET /transactions/pending/sla-summary` (team-level SLA compliance counts, for a manager's view) - both additive; the original `GET /transactions/pending` is untouched.
- **Simplification noted honestly:** no multi-level approval exists in this system (that was Phase 5 in the original enterprise-evolution plan, deprioritized in favor of these five product features) - so there's no "previous approvals" stage to show. The workbench reflects that truthfully rather than inventing an approval chain that doesn't exist.
- Frontend: `ApprovalQueuePage` rebuilt into a proper workbench view - each item shows the why-panel, risk and SLA badges (with a live countdown), and a link out to Transaction 360, plus a team SLA summary strip at the top.

**Exit criteria met:** an approver can see why every item in their queue is there and how urgent it is without leaving the page; nothing about who can approve what, or how many approvals a transfer needs, changed from before this phase.

---

## Product Enhancement Phase — Feature 4: Operations Command Center

A new, deliberately small module - `dashboard` - that owns no tables of its own and exists purely to compose what `transactions` and `exceptions` already know into one "what needs my attention right now" view.

- New `TransactionMetricsPort`/`ExceptionMetricsPort` - narrow, read-only aggregate ports added to `transactions` and `exceptions` respectively, following the exact same one-directional dependency pattern as everything else this phase (`dashboard` depends on both; neither has any idea `dashboard` exists).
- `CommandCenterController` (`GET /api/v1/dashboard/command-center`, EMPLOYEE-only) returns three groups: **Transaction Health** (today's volume/value/success rate), **Work Requiring Attention** (pending approvals, high-risk items, open exceptions, SLA breaches across both approval and exception queues), and **Operational Performance** (average approval time, average exception resolution time, historical SLA compliance rate).
- Every number is clickable and lands on the real filtered view - reusing `/approvals` and `/exceptions` as they already exist, plus one small necessary addition: a generic `GET /transactions/by-status` endpoint (backed by the same repository method `listPending()` already used internally), since there was previously no employee-facing way to browse transactions by status at all.
- **Reconciliation, handled honestly:** the existing `LedgerReconciliationCheckService` (built back in the original ledger phases) already does real balance reconciliation, but it's ADMIN-only and scans every ledger account - too expensive to fold into an auto-refreshing dashboard poll. Rather than either skip it or misuse it, the Command Center surfaces it as its own on-demand, ADMIN-only panel that calls the existing endpoint directly when clicked - reusing what exists exactly as it exists, not rebuilding it.
- **Simplification noted honestly:** all averages (approval time, resolution time) and the historical SLA compliance rate are computed in Java over the fetched rows, not pushed into SQL aggregates - correct and readable at this data volume; a real production system would move these server-side once transaction volume justified it.

**Exit criteria met:** an employee or manager can see the state of the whole operation - today's transaction health, everything waiting on someone, and how the team is performing against SLA - from one screen, with every number leading somewhere real.

---

## Product Enhancement Phase — Feature 5: Customer 360

The last of the five features, and it closes an important architectural loop: it's the first view in this phase that can safely embed exception data directly, and the code explains exactly why the others couldn't.

- New `customer360` module - schema-less, exactly like `dashboard` - depending one-directionally on `users`, `accounts`, `transactions`, `risk`, and `exceptions`.
- **Why this isn't hosted inside `users`:** `transactions`, `accounts`, and `exceptions` all already depend on `users` (for name/email resolution). Hosting the aggregation *inside* `users` would create `users → exceptions → users` - the exact cycle Transaction 360 had to work around with a frontend-composition workaround back in Feature 1/2. A brand-new, dependency-only module sidesteps the problem entirely: nothing depends on `customer360`, so it's free to depend on all five other modules safely - including `exceptions`, which none of the others could reach without a cycle. That's why Customer 360's open-exceptions section is a real, embedded part of the single API response, while Transaction 360's exception link still has to be a second, separate frontend call.
- Two small new outbound ports made this possible: `TransactionLookupPort.listForCustomerSince` (transactions) and `ExceptionLookupPort.listForTransactionIds` (exceptions, its second consumer after `dashboard`) - plus `UserSummary` picked up the extra fields (`lastName`, `status`, `createdAt`) a customer profile actually needs, at a single, safe call site.
- **Risk profile, done honestly:** this system has never computed a single customer-level risk score - only per-transaction assessments. Rather than invent one, the risk section aggregates what's real: the customer's most recent assessed transaction's level, a count-by-level breakdown across their history, and every distinct reason that's actually fired for them. No new scoring model, per the product brief's explicit "no unnecessary ML system" instruction.
- **Simplification noted honestly:** the activity timeline is built from transaction submissions and exception openings only, not also account-opening events - `AccountSummary` doesn't carry a creation timestamp, and extending it would ripple through nine existing call sites for a timeline nicety. Also: there's no "customer type/segment" or formal onboarding record in this data model, so Customer 360 doesn't display one rather than inventing a label nothing in the system actually tracks.
- Frontend: `CustomerSearchPage` (reusing the existing employee customer search) → `CustomerDetailPage`, and a "View 360" link added to the existing Manage Accounts page's search results, connecting the two flows.

**Exit criteria met:** an employee can go from a customer's name to their full relationship - accounts, 90 days of activity, real risk history, every open case - in two clicks, with every card format matching the same visual language as Transaction 360, the Approval Workbench, and the Command Center it now completes.

---

## Product Enhancement Phase — all five features complete

Transaction 360 → Exception & Investigation Management → Approval Workbench + SLA → Operations Command Center → Customer 360, in that order, each one reusing what the last one built rather than duplicating it. The full "why did this happen, what needs attention, and what's the customer's whole picture" loop the product brief asked for is now real, backed by actual data end to end, with every simplification and architectural tradeoff flagged in this changelog rather than left silent.
