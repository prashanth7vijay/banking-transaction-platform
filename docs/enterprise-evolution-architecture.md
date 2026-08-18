# Enterprise Banking Operations Platform — Additive Architecture Design

**Status:** design document only — no implementation in this document. Every capability below is proposed as an **addition** to the existing, working system described in `docs/architecture-design.md` and the technical handover document. Nothing already running is replaced unless a concrete problem with it is demonstrated first, and every such case is called out explicitly.

**Grounding rule applied throughout:** every proposal below is checked against the existing codebase as it actually exists today — not an idealized version of it. Where a proposed capability directly patches a real, already-identified gap (e.g. the outbox pattern directly addresses the existing `NotificationService` email try/catch working around a coupling problem it can't fully solve today), that connection is stated explicitly, because that's what makes the addition justified rather than decorative.

---

# 1. Executive Summary

The platform becomes an **internal bank operations system**: still the same modular monolith, still Spring Boot + React, still schema-per-module Postgres — but with a real financial ledger underneath the existing account balances, a formal transaction lifecycle replacing today's flat status enum, configurable risk limits and multi-level approval policies replacing today's single-level "any employee, any amount" maker-checker, a reconciliation subsystem that continuously proves the ledger and the account balances agree, an operational exception console for when something in that chain fails, a transactional outbox for the side effects that must never be allowed to block or roll back a financial write, meaningfully hardened authentication, and an operations-grade UI (dashboards, a Transaction 360 view, an Approval Queue with SLA visibility, an Audit Explorer, an Exception Console) built on a shared design system rather than page-by-page styling.

What does **not** change: Java 21, Spring Boot, Spring Security, PostgreSQL, Redis (still narrowly scoped, not becoming a general cache), Flyway, JPA/Hibernate, React 19 + Vite + TypeScript, TanStack Query, React Hook Form + Zod, RS256 JWT with rotating opaque refresh tokens, RBAC via `@PreAuthorize`, the modular-monolith style, schema-per-module design, the port-interface cross-module contract, optimistic locking, idempotent transfer submission, Docker Compose as the deployment target, and JUnit/Mockito/Testcontainers as the testing stack. This document treats every one of those as a load-bearing existing decision, not a starting point to renegotiate.

---

# 2. Existing Architecture (unchanged, summarized)

- **Style:** modular monolith, one Spring Boot process, one Postgres instance, one schema per module (`users`, `auth`, `accounts`, `transactions`, `audit`, `notifications`).
- **Cross-module contract:** narrow `Port` interfaces (`AccountLookupPort`, `UserLookupPort`) — a module never touches another module's `Repository` or `Entity` directly. The one deliberate, named exception is the `admin` module, a stateless read-only aggregator.
- **Money:** `Account.balance` is a single mutable `NUMERIC(19,4)` column, protected by `@Version` optimistic locking and a `CHECK (balance >= 0)` constraint. There is currently no way to derive that balance from a transaction history — it is the *only* record of itself.
- **Transaction lifecycle today:** `Transaction.status ∈ {PENDING, REJECTED, COMPLETED, FAILED}`. Submission creates `PENDING` with no fund movement. A single `ApprovalService.approve()` call does the debit, the credit, and the status flip to `COMPLETED`/`FAILED` all in one step — there is no separate "approved but not yet posted" state.
- **Maker-checker today:** exactly one approval, by any user with role `EMPLOYEE`, with a single guard: the approver cannot be the same person as the initiator. No value-based routing, no seniority requirement, no multi-step approval.
- **Events today:** Spring's in-process, synchronous `ApplicationEventPublisher`/`@EventListener` — `TransactionStatusChangedEvent`, `AuthActionEvent`, `UserActionEvent`, `AccountOpenedEvent`, consumed by `AuditEventListener` and `NotificationEventListener`, in the **same thread and the same database transaction** as the code that raised the event. No outbox table, no message broker, no retry, no dead-letter handling.
- **Security today:** RS256 JWT (15-minute access token), opaque refresh tokens (SHA-256 hashed at rest, rotated on every use), a Redis-backed logout denylist keyed by `jti`. No MFA, no rate limiting, no automated account lockout (the `LOCKED` status exists but nothing sets it), no device/session tracking, no refresh-token-reuse detection.
- **Observability today:** correlation IDs via MDC, structured JSON logs shipped to console + Logstash → Elasticsearch, Micrometer counters for login attempts and transaction outcomes, a provisioned Grafana dashboard. No distributed tracing.
- **Testing today:** JUnit 5 + Mockito + AssertJ unit tests; Testcontainers-backed Postgres integration tests for optimistic locking and the RBAC matrix; zero frontend tests despite the tooling being installed.
- **Deployment today:** Docker Compose, ten services, one host, no orchestrator.

Every item above is preserved. What follows is additive.

---

# 3. Target Architecture

```
                                    ┌──────────────────────────┐
                                    │         Nginx             │
                                    └────────────┬──────────────┘
                                                  │
                     ┌────────────────────────────┼─────────────────────────────┐
                     │                             │                             │
            ┌────────▼────────┐           ┌────────▼─────────┐          ┌───────▼───────┐
            │  React SPA        │           │  Spring Boot App   │          │  Actuator /    │
            │  (Operations UI)   │           │  (modular monolith, │          │  Prometheus /  │
            │                    │           │   unchanged process │          │  OTel (opt.)   │
            └────────────────────┘           │   boundary)         │          └───────┬────────┘
                                              └─────────┬───────────┘                  │
                                                        │                              │
   Existing modules (unchanged internal design):        │        New modules (this document):
   auth · users · accounts · transactions ·             │        ledger · risk · approvals ·
   audit · notifications · admin · shared               │        reconciliation · opsexceptions ·
                                                         │        eventing (outbox relay)
                                                         │
              ┌──────────────────────────────────────────┼───────────────────────────────┐
              │                                           │                                │
       ┌──────▼─────┐                              ┌──────▼───────┐                ┌───────▼────────┐
       │ PostgreSQL  │  (schema per module, 1       │     Redis      │                │ Prometheus /    │
       │  instance,  │   still — ledger, risk,       │ (unchanged      │                │ Grafana / ELK    │
       │  new schemas│   approvals, reconciliation,   │  scope: JWT     │                │ (extended, not    │
       │  added)     │   opsexceptions, outbox)        │  denylist,      │                │  replaced)        │
       └────────────┘                              │  reset tokens,  │                └───────────────────┘
                                                    │  NEW: idempotency│
                                                    │  cache — see §8) │
                                                    └────────────────┘
```

**New modules**, following the exact same internal layering every existing module already uses (`domain` → `repository` → `service` → `controller`, DTOs and mappers at the boundary, `Port` interfaces for anything another module needs):

| Module | Schema | Depends on (via Port) |
|---|---|---|
| `ledger` | `ledger` | `accounts` (to reconcile against), `transactions` (posting references) |
| `risk` | `risk` | `accounts`, `transactions` (read-only, via existing ports) |
| `approvals` | `approvals` | `users` (role/seniority lookups), `transactions` |
| `reconciliation` | `reconciliation` | `ledger`, `accounts` (read-only) |
| `opsexceptions` | `opsexceptions` | none required — a pure sink other modules report into |
| `eventing` | none of its own — reads outbox tables that live in each producing module's own schema | every module that produces outbox events |

No existing module's schema, entity, repository, or public API is deleted or renamed. `accounts.accounts.balance` is **not removed** — see §5 for exactly what happens to it.

## Future scalability
Exactly as today: the port-interface seam is what would let any of these new modules (most plausibly `ledger` or `risk`, given how naturally read-heavy and computation-heavy they are relative to the rest of the system) be extracted into a standalone service later, without this document's design changing shape — extraction is "swap the `@Service` implementation of a port for an HTTP client," not a redesign.

---

# 4. New Modules — Summary Table

Full detail for each is in its dedicated section (§5–§11). This table is the at-a-glance cross-reference the prompt asked for.

| Module | Purpose | Key entities | Key events produced | Key events consumed |
|---|---|---|---|---|
| `ledger` | Authoritative double-entry financial record | `LedgerAccount`, `JournalEntry`, `JournalEntryLine` | `LedgerEntryPostedEvent` | `TransactionStatusChangedEvent` (COMPLETED only) |
| `risk` | Pre-transaction limit/velocity checks and risk scoring | `LimitPolicy`, `RiskAssessment` | `RiskAssessmentCompletedEvent` | (called synchronously by `transactions`, not event-driven — see §7) |
| `approvals` | Policy-driven, multi-level approval routing | `ApprovalPolicy`, `ApprovalStep`, `ApprovalAssignment`, `ApprovalDecision` | `ApprovalDecisionRecordedEvent` | `TransactionStatusChangedEvent` (SUBMITTED/PENDING_APPROVAL) |
| `reconciliation` | Proves ledger and materialized balances agree | `ReconciliationRun`, `ReconciliationResult` | `ReconciliationMismatchDetectedEvent` | (scheduled job, not event-driven) |
| `opsexceptions` | Tracks and manages operational failures needing human attention | `OperationalException` | `OperationalExceptionOpenedEvent`, `...Resolved...` | `ReconciliationMismatchDetectedEvent`, outbox dead-letter events, various failure paths |
| `eventing` | The outbox relay — makes select events reliably, asynchronously delivered | `OutboxEvent` (one table per producing module's schema, same shape) | — (it *is* the delivery mechanism) | polls outbox tables, re-publishes as existing Spring event types |

---

# 5. Ledger Architecture

## The problem this solves
Today, `accounts.accounts.balance` is the *only* record of how much money is in an account. There is no way to answer "prove to me this number is correct" except "trust the column." A single application bug, a bad migration, or a directly-run SQL `UPDATE` (nothing currently prevents one) can silently corrupt a balance with no trace of what happened and no way to detect it. A real bank cannot operate this way — every financial system of record needs a durable, append-only, mathematically self-checking history that the current balance is *derived from*, not just *stored as*.

## What happens if we don't implement it
The system remains unauditable at the financial-truth level. `audit.audit_logs` records that "a debit of $100 happened," but nothing proves the account's current balance is the correct sum of every debit and credit that ever happened to it. Reconciliation (§9) would have nothing authoritative to reconcile *against* — it could only compare the balance to itself, which is meaningless.

## Design: hybrid, not a replacement

**Ledger = source of truth. `Account.balance` = materialized projection, kept as-is.**

This is not "add a ledger and delete the balance column." The existing column stays, for a concrete reason: every existing query that reads a balance (the customer dashboard, the transfer form's account picker, the admin metrics total) needs that number *fast* and *simple* — a single indexed column read. Recomputing a balance by summing every journal line on every read would be correct but needlessly slow for the overwhelmingly common case (read a balance), and would change the shape of `AccountLookupPort.getById()` for every existing caller. Instead:

- The **ledger is written first**, within the same database transaction as the business operation.
- The **materialized `balance` column is updated in the same transaction**, computed from the ledger entry just posted (not by re-summing history — an incremental update, exactly as today's `AccountLookupPortImpl.debit()`/`credit()` already do).
- The two can never disagree *at commit time*, because they're the same transaction. They can only drift if something writes to `balance` outside the ledger-posting path (already true today, and only fully closable by revoking direct `UPDATE` privileges on the column outside the service layer — a real, named future hardening step, not implemented here) or through a genuine bug — which is exactly what reconciliation (§9) exists to catch.

## Schema

```sql
CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE ledger.ledger_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Points at accounts.accounts.id by convention (a plain UUID column, no
    -- cross-schema FK - same deliberate tradeoff the existing schema already
    -- makes everywhere else; see the existing architecture doc's discussion).
    account_id UUID NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    ledger_balance NUMERIC(19,4) NOT NULL DEFAULT 0,   -- sum of all posted lines
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0, -- ledger_balance minus holds (see note)
    version BIGINT NOT NULL DEFAULT 0,                  -- optimistic locking, same pattern as Account
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger.journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- What real-world event caused this entry. Points at transactions.transactions.id
    -- for a transfer; nullable to allow future non-transfer postings (fees, interest).
    transaction_reference UUID,
    description VARCHAR(255) NOT NULL,
    accounting_date DATE NOT NULL,          -- the business date this entry books to
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    correlation_id VARCHAR(64)              -- ties back to the originating HTTP request
);

CREATE TABLE ledger.journal_entry_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL REFERENCES ledger.journal_entries (id),
    ledger_account_id UUID NOT NULL REFERENCES ledger.ledger_accounts (id),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),  -- always positive; direction carries the sign
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_journal_lines_entry ON ledger.journal_entry_lines (journal_entry_id);
CREATE INDEX idx_journal_lines_account ON ledger.journal_entry_lines (ledger_account_id);
CREATE INDEX idx_journal_entries_tx_ref ON ledger.journal_entries (transaction_reference);
CREATE INDEX idx_journal_entries_accounting_date ON ledger.journal_entries (accounting_date);
```

**Why three tables, not one:** a `journal_entry` is the atomic, all-or-nothing unit ("this transfer posted"); its `lines` are the individual debit/credit legs. This is the standard double-entry shape specifically because **DEBITS = CREDITS is enforced by construction**: a transfer of $100 from account A to account B posts exactly one `journal_entry` with exactly two `lines` — a $100 DEBIT on A's ledger account and a $100 CREDIT on B's ledger account — and the application-layer posting service refuses to commit a journal entry whose lines don't sum to zero (debits minus credits) per currency. This is checked in code (`LedgerService.post()`), not by a database trigger, for the same reason the rest of this codebase keeps business logic in the service layer rather than in the database — but see the "future hardening" note below for why a database-level check is worth adding later.

**Immutability:** exactly like `audit.audit_logs` today, journal entries and lines are **append-only by convention** (no service method ever issues an `UPDATE` or `DELETE` against them) — not yet by database-enforced grant. This is a deliberate, consistent choice with how the existing `audit` module already handles the identical tradeoff, not a new gap invented for the ledger. A correcting entry (a mistaken posting) is handled the way real accounting handles it: post a **reversing entry** (equal and opposite lines), never edit or delete the original. `JournalEntry` has no `updated_at` column at all — its absence is the immutability signal.

**Why `ledger_accounts` is a separate table from `accounts.accounts` rather than adding ledger columns directly onto the existing table:** keeps the ledger schema fully self-contained (consistent with the schema-per-module rule every other addition in this document follows) and keeps `ledger_balance`/`available_balance` — two numbers with real accounting meaning — from living in the same table as `accounts.accounts.balance`, which would make "which number is the real one" ambiguous at the schema level, not just conceptually.

**`available_balance` vs `ledger_balance`:** included in the schema now, but its actual computation (subtracting active holds/reservations for pending-but-not-yet-posted transactions) is **out of scope for this design** — there is no hold/reservation concept anywhere in the current transaction lifecycle (§6 doesn't introduce one either). It's included as a column because a real ledger needs the distinction eventually and adding it later would be a migration; it currently just mirrors `ledger_balance`.

## Money precision, currency, accounting dates
`NUMERIC(19,4)` throughout, identical precision to the existing `accounts.accounts.balance` and `transactions.transactions.amount` — no precision mismatch is introduced. `currency` is carried on every line (not just the account), so a future multi-currency journal entry (an FX conversion, which posts unequal amounts in two different currencies but is still "balanced" in the sense that it nets to zero *per currency*) is representable without a schema change — though multi-currency conversion logic itself is not designed here. `accounting_date` is separate from `posted_at` specifically because real accounting periodically needs to book an entry to a business date that differs from the literal insert timestamp (e.g., a same-day-but-after-cutoff transaction booking to the next business day) — not used by any flow in this design yet, but its absence would be a real migration later, so it's included now at near-zero cost.

## Consistency guarantees
Within one Spring `@Transactional` boundary (the same guarantee the existing `ApprovalService.approve()` already relies on for its debit+credit+status-update atomicity): the journal entry, its lines, the `ledger_accounts.ledger_balance` update, and the existing `accounts.accounts.balance` update all commit together or none do. Postgres's own ACID guarantees are the entire mechanism — no distributed transaction, no two-phase commit, no saga, because this is still one database.

## Performance implications
One additional `INSERT` (the journal entry) and two additional `INSERT`s (the lines) per transfer, plus one additional `UPDATE` (the ledger account's materialized balance) — roughly doubling the write volume of a completed transfer's approval step. At this system's actual scale (a portfolio/interview project, not a live bank), this is immeasurable. At real scale, `ledger.journal_entry_lines` is the fastest-growing table in the system and is the first candidate for date-range partitioning (exactly the same recommendation the existing handover document already makes for `transactions.transactions` — this reinforces rather than adds a new performance concern).

## Reconciliation strategy
Covered in full in §9 — the short version: `ledger_accounts.ledger_balance` and `accounts.accounts.balance` should always be numerically identical after every completed transfer, since they're updated in the same transaction from the same posting event; a scheduled reconciliation job periodically proves this is still true, and any mismatch becomes an `OperationalException` (§10).

## Concurrency implications
Posting a journal entry against a `ledger_account` takes the same optimistic-locking approach as `accounts.accounts` today (`ledger_accounts.version`), for the identical reason already documented in the existing architecture: concurrent writes to the same account are expected to be rare, and a retry-on-conflict is cheaper than serializing all activity on a popular account with a row lock. **The one genuinely new concurrency scenario this introduces:** posting now touches *two* optimistically-locked rows in the same transaction (the ledger account *and* the existing `Account` row) rather than one — meaning a conflict on *either* row now aborts the whole posting, and the retry (if one is added — see the concurrency table in §16 for the honest current answer) must retry the *entire* debit+credit+ledger-post sequence, not just the row that conflicted.

## Migration strategy from the current balance model
This is the most sensitive migration in this entire document, and is handled in **five explicit stages**, each independently deployable and each leaving the application fully working:

1. **Stage 1 — additive schema only.** Ship the `ledger` schema and tables via a new Flyway migration (`V8__ledger_schema.sql`). No application code path writes to it yet. Zero behavior change; pure schema addition, safely deployable at any time.
2. **Stage 2 — backfill.** A one-time script (a Flyway "repeatable" migration or a standalone admin-triggered job, not a request-path operation) creates one `ledger_accounts` row per existing `accounts.accounts` row, and one `journal_entry` (with two balanced lines: a CREDIT to the account for its current balance, a DEBIT to a new `SYSTEM_OPENING_BALANCE` clearing ledger account) per existing account, dated to each account's `created_at`. This gives every existing account a ledger history that is *consistent with*, though not a *true replay of*, its actual transaction history — an honestly-labeled "opening balance" entry, not a fabricated transaction-by-transaction reconstruction (reconstructing true history would require re-deriving amounts from `transactions.transactions`, which is possible but explicitly out of scope for this stage — noted as a nice-to-have, not required for correctness).
3. **Stage 3 — dual-write.** `ApprovalService.approve()` is extended to *also* post the corresponding ledger entry, in the same transaction, alongside its existing debit/credit calls — **both the ledger and the existing `Account.balance` column are updated by the same code path, atomically**. This is "dual-write" in the sense that two representations are updated, but it is not the classically risky kind of dual-write (two separate systems, no shared transaction) — it's one transaction, one commit, so there is no window where they can disagree. No feature flag is needed *specifically because* it's the same transaction — either both succeed or both roll back.
4. **Stage 4 — validate.** Run the reconciliation job (§9) continuously for a defined soak period (a config value, not a hardcoded duration) comparing `ledger_accounts.ledger_balance` to `accounts.accounts.balance` for every account. Any mismatch during this period is a **backfill bug**, not a production incident — investigated and fixed in the backfill script, not treated as an operational exception yet.
5. **Stage 5 — cutover (conceptual, not executed in this design).** Once validated, the ledger is trusted as the source of truth for any *new* read path that wants it (e.g., a future "true balance" reconciliation report, regulatory export). `accounts.accounts.balance` is **never deleted** — it remains the fast-path read for every existing query, permanently, per the hybrid design above. There is no "stage 6" where the column goes away.

**Rollback strategy:** every stage above is rollback-safe *in isolation* — Stage 1 is schema-only (drop the tables if needed, though Flyway migrations are not designed to be reversed in place; the standard answer is a new forward migration that drops them, not an actual rollback). Stage 3 (dual-write) can be rolled back by reverting the code change; because `Account.balance` was never made dependent on the ledger for its own updates (it's still updated the same way it always was, just *alongside* the new ledger write), reverting Stage 3's code leaves `Account.balance` exactly as correct as it is today, with no cleanup required — the ledger tables simply stop receiving new rows.

---

# 6. Transaction State Machine

## The problem this solves
Today's four flat statuses (`PENDING`, `REJECTED`, `COMPLETED`, `FAILED`) conflate several genuinely distinct real-world moments into one field with no explicit transition rules — "submitted but not yet risk-checked," "risk-checked and awaiting approval," "approved but not yet posted to the ledger" are all currently just `PENDING`. As soon as this document adds risk scoring (§7) and multi-level approval (§8), those moments need to be individually observable, individually query-able (an ops dashboard needs to show "how many transactions are stuck in risk evaluation" separately from "how many are waiting on a second approver"), and individually authorization-gated (only the risk engine may move a transaction out of risk evaluation; only an assigned approver may move it out of an approval step).

## Is a formal state-machine pattern actually justified here, or just naming?
**Genuinely justified**, for one concrete reason beyond naming: **not every transition is legal from every state, and today's code has no single place that enforces that.** `ApprovalService.approve()` today checks exactly one precondition (`status == PENDING`) before acting — a state machine makes the *full* transition table explicit, machine-checkable, and testable as a single unit (§16), rather than each service method re-deriving "is this a legal thing to do right now" from scratch as the number of possible states grows from 4 to 8.

## Lifecycle

```
DRAFT ──────► SUBMITTED ──────► PENDING_APPROVAL ──────► APPROVED ──────► PROCESSING ──────► COMPLETED
  │                │                    │                    │                 │
  │                │                    │                    │                 └──► FAILED
  │                │                    └──► REJECTED         └──► CANCELLED
  │                └──► CANCELLED
  └──► CANCELLED
```

| State | Meaning | Set by |
|---|---|---|
| `DRAFT` | Not currently used by any existing flow — reserved for a future "save transfer for later" feature. Included in the model now because the state machine's transition table needs to be complete, not because a `DRAFT` UI is being built here. | customer (future) |
| `SUBMITTED` | Transfer request received, not yet risk-evaluated. **This replaces today's `PENDING` at the moment of creation.** | customer, via `TransferService.createTransfer()` |
| `PENDING_APPROVAL` | Risk evaluation passed (or requires human judgment); routed to one or more approval steps. | `risk` module, automatically, immediately after `SUBMITTED` |
| `APPROVED` | The last required approval step has recorded an APPROVE decision. Funds have **not** moved yet — this is the state today's design collapses into the single `approve()` call. | `approvals` module |
| `PROCESSING` | The ledger-posting operation has been handed off (still synchronous in this design — see the strongly-vs-eventually-consistent discussion in §11 — but modeled as its own state so a genuinely async posting step could be introduced later without another state-machine redesign). | `transactions`/`ledger` |
| `COMPLETED` | Ledger posted, materialized balances updated. Terminal. | `ledger` |
| `REJECTED` | An approver declined at any approval step. Terminal. | `approvals` |
| `FAILED` | Risk evaluation blocked it, or posting failed (e.g. insufficient funds re-checked at posting time — **the existing, already-correct behavior of re-validating balance at approval time, not submission time, is fully preserved**). Terminal. | `risk` or `ledger` |
| `CANCELLED` | The customer withdrew the request before a terminal decision. Terminal. | customer |

## Valid transitions (the enforced table)

| From | To | Trigger | Who may trigger |
|---|---|---|---|
| `DRAFT` | `SUBMITTED` | customer submits | customer (owner only) |
| `DRAFT` | `CANCELLED` | customer discards | customer (owner only) |
| `SUBMITTED` | `PENDING_APPROVAL` | risk evaluation completes, no auto-block | system (`risk` module) |
| `SUBMITTED` | `FAILED` | risk evaluation blocks (e.g. over a hard limit) | system (`risk` module) |
| `SUBMITTED` | `CANCELLED` | customer withdraws before approval starts | customer (owner only) |
| `PENDING_APPROVAL` | `APPROVED` | final required approval step approves | assigned approver(s), per policy (§8) |
| `PENDING_APPROVAL` | `REJECTED` | any required approval step rejects | assigned approver, per policy |
| `PENDING_APPROVAL` | `CANCELLED` | customer withdraws | customer (owner only) — **only** while still pending, never after `APPROVED` |
| `APPROVED` | `PROCESSING` | posting begins | system, immediately (no human trigger) |
| `PROCESSING` | `COMPLETED` | ledger posts successfully | system (`ledger`) |
| `PROCESSING` | `FAILED` | posting fails (insufficient funds re-check, ledger error) | system |

**Every transition not in this table is invalid and rejected.** Concretely: `REJECTED`, `FAILED`, `COMPLETED`, and `CANCELLED` are terminal — no code path may transition a transaction *out* of any of them, ever (this is enforceable at the type level, not just by convention — see the implementation note below).

## Authorization rules per transition
Layered exactly the way the existing system already layers authorization (URL/role gate + method-level ownership/business check): `@PreAuthorize` continues to enforce *role* ("must be a CUSTOMER to submit," "must be an EMPLOYEE to approve"); the state machine itself enforces *legality of the transition regardless of role* (an EMPLOYEE with a valid token still cannot "approve" a transaction sitting in `SUBMITTED`, because `PENDING_APPROVAL → APPROVED` is the only approve-shaped transition that exists, and `SUBMITTED` has no such edge). The existing maker-checker self-approval guard (§8 generalizes it) continues to apply on every approval-shaped transition.

## Audit events
Every transition publishes a `TransactionStateChangedEvent(transactionId, fromState, toState, actorUserId, timestamp)` — a direct generalization of today's `TransactionStatusChangedEvent`, kept as an **additive new event type** rather than breaking the existing one's shape (existing listeners that only care about the four original statuses keep working unmodified; new listeners, like `reconciliation` and `opsexceptions`, subscribe to the richer new event).

## Side effects, retry behavior, idempotency, concurrency
- **Side effects** per transition are exactly the module boundaries already drawn: `SUBMITTED → PENDING_APPROVAL` triggers risk evaluation (§7); `APPROVED → PROCESSING → COMPLETED` triggers ledger posting (§5); every transition triggers an audit record (synchronous, same transaction — a financial state change is never "eventually" audited) and, for customer-visible terminal states, a notification (via the outbox — §11, since email is explicitly allowed to lag).
- **Retry behavior:** transitions themselves are not retried automatically anywhere in this design — a `FAILED` transaction is terminal and requires a **new** transfer submission (a fresh `SUBMITTED`), never a retry-in-place of the same row. This mirrors real banking practice (a failed payment is resubmitted, not silently retried against the same reference) and avoids a whole category of "was this retried once or five times" ambiguity.
- **Idempotency:** the existing idempotency-key mechanism on transfer *creation* is fully preserved and now specifically guards the `(none) → DRAFT/SUBMITTED` transition. Approval/rejection actions get their **own** idempotency guard, newly needed once multi-level approval (§8) exists: an approval decision is keyed by `(transaction_id, approval_step_id, approver_user_id)`, unique-constrained, so a double-submitted "Approve" click (a network retry, a double-tap) cannot record two decisions.
- **Concurrency:** see the dedicated concurrency table in §16 — the state machine's core protection is that every transition is implemented as `UPDATE transactions SET status = :to WHERE id = :id AND status = :from` (an explicit precondition on the *current* state in the `WHERE` clause, on top of — not instead of — the existing `@Version` optimistic lock), so two concurrent attempts to make the *same* transition race safely (one wins, one gets zero rows affected and a clean "already in that state" response) and two attempts to make *different, mutually exclusive* transitions from the same starting state race safely for the identical reason.

## Implementation note: why this doesn't need a new framework dependency
Java 21's `sealed` interfaces plus an `enum` are sufficient to make illegal transitions a compile-time-adjacent concern rather than a runtime one: `TransactionStatus` becomes a richer enum, and a small `TransactionStateMachine` service (not a generic third-party state-machine library — Spring State Machine or similar was evaluated and rejected as unnecessary weight for a transition table this size) owns exactly one method, `transition(Transaction, TransactionStatus from, TransactionStatus to, actor)`, which validates against a `Map<TransactionStatus, Set<TransactionStatus>>` built once at startup. This keeps the pattern's benefit (one place, exhaustively tested, that owns "is this legal") without adopting a framework whose configuration surface would be larger than the problem it's solving.

---

# 7. Risk Architecture

## The problem this solves
Today, the only check on a transfer amount is `amount > 0.01` — there is no per-transaction cap, no daily cumulative cap, no velocity check (e.g. "five transfers in ten minutes"), nothing that distinguishes a routine $50 transfer from a $50,000 one. A real bank operations system needs a **deterministic, configurable, explainable** first line of defense before a transaction is even offered for human approval — both to protect customers and to make the approval queue itself scale (a human approver should see transactions that genuinely need judgment, not every single transfer regardless of size).

## Design: rule-based engine, explicitly not ML — with an explicit seam for ML later

The prompt is direct that ML is out of scope *now* but the abstraction should not preclude it *later*. The design:

```java
public interface RiskStrategy {
    RiskAssessment assess(TransferContext context);
}
```

`TransferContext` is a read-only, fully-populated snapshot (amount, source account, destination, customer's recent transfer history summary, account age, etc.) — deliberately **not** a live handle back into the database, so a `RiskStrategy` implementation cannot accidentally cause side effects or N+1 queries. Today, exactly one implementation exists: `RuleBasedRiskStrategy`, which evaluates an ordered list of configurable `LimitPolicy` rows. A future `MachineLearningRiskStrategy` would implement the same interface and could run **alongside** the rule-based one (a `CompositeRiskStrategy` taking the higher of the two assessed risk levels) rather than replacing it — this is the Strategy pattern, and it is justified here specifically because the requirement ("deterministic today, pluggable-ML tomorrow") is exactly the shape Strategy solves; it is not used anywhere else in this design where a simpler `if/else` would do (see §14 for the explicit list of patterns evaluated and rejected).

## Rule categories and schema

```sql
CREATE SCHEMA IF NOT EXISTS risk;

CREATE TABLE risk.limit_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    scope VARCHAR(20) NOT NULL CHECK (scope IN ('GLOBAL', 'ACCOUNT_TYPE', 'CUSTOMER', 'ACCOUNT')),
    scope_reference UUID,               -- nullable; e.g. a specific account/customer ID when scope isn't GLOBAL
    limit_type VARCHAR(20) NOT NULL CHECK (limit_type IN ('PER_TRANSACTION', 'DAILY_CUMULATIVE', 'VELOCITY_COUNT')),
    max_amount NUMERIC(19,4),           -- used by PER_TRANSACTION / DAILY_CUMULATIVE
    max_count INT,                      -- used by VELOCITY_COUNT
    window_minutes INT,                 -- used by VELOCITY_COUNT (e.g. 10)
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE risk.risk_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL UNIQUE,   -- one assessment per transaction
    risk_level VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    triggered_policy_ids UUID[],           -- which rules fired, for explainability
    reasons TEXT[],                        -- human-readable reason per triggered rule
    blocked BOOLEAN NOT NULL DEFAULT false, -- CRITICAL + a hard-block rule -> true -> SUBMITTED -> FAILED
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_assessments_tx ON risk.risk_assessments (transaction_id);
CREATE INDEX idx_limit_policies_scope ON risk.limit_policies (scope, scope_reference) WHERE active;
```

**Why `risk_level` is stored, not just a boolean pass/fail:** the risk level is what drives *which* approval policy applies (§8) — `risk` and `approvals` are deliberately separate modules with a narrow, explicit contract between them (`RiskAssessment` is what `approvals` reads), rather than one module doing both jobs, so that approval-policy configuration can change without touching risk-rule configuration and vice versa.

**Why `triggered_policy_ids` and `reasons` are arrays, not a join table:** this is written once at assessment time and never updated (append-only, same immutability convention as everywhere else in this design) and always read as a whole — a Postgres array is the right fit for "a small, fixed-at-write-time list attached to one row," and avoids an unnecessary child table for data that's never queried by its individual elements.

## Risk decision flow
```
Transfer submitted (SUBMITTED)
      |
      v
RiskAssessmentService.assess(context)   [synchronous - see "why synchronous" below]
      |
      +-- evaluates all active LimitPolicy rows in scope (GLOBAL, then ACCOUNT_TYPE,
      |    then CUSTOMER, then ACCOUNT - most specific wins on conflict)
      |
      +-- PER_TRANSACTION: amount > max_amount? 
      +-- DAILY_CUMULATIVE: sum of today's COMPLETED+PENDING_APPROVAL transfers 
      |      from this account + this amount > max_amount?
      +-- VELOCITY_COUNT: count of transfers from this account in the last 
      |      window_minutes >= max_count?
      |
      v
risk_level computed (highest severity among triggered rules; LOW if none triggered)
      |
      +-- blocked == true  -> transition SUBMITTED -> FAILED (no approval queue entry created)
      +-- blocked == false -> transition SUBMITTED -> PENDING_APPROVAL, 
                              risk_level attached for approvals' policy selection
```

**Why synchronous, not event-driven:** risk assessment gates whether a transaction can even *enter* the approval queue — it is on the strongly-consistent side of the line drawn in §11 (it must complete, successfully or with a clear failure, before the customer's submission request returns), not a decoupled side effect. It runs inside the same `@Transactional` boundary as `TransferService.createTransfer()`, exactly where the existing (much simpler) validation already runs today.

## Why a rule-based engine specifically, and why not hardcode the checks
A `LimitPolicy` table (data) rather than `if (amount > 10000)` (code) is justified by a concrete operational need this system already implicitly has: **an operations/risk team, not an engineer, needs to be able to tighten a daily limit without a deployment.** This is the same reasoning that already justifies `users.roles` being a table instead of a hardcoded enum in the existing system — consistent application of a pattern already trusted in this codebase, not a new philosophy introduced just for this module.

---

# 8. Approval Workflow (Multi-Level)

## The problem this solves
Today's maker-checker is binary and flat: any one `EMPLOYEE` (other than the initiator) can approve a transfer of any size. A real operations bank needs approval *routing* — a $50 transfer needs one junior approver; a $50,000 transfer needs a senior employee **and** a manager; a `CRITICAL`-risk transfer needs operations **and** compliance **and** a manager. Hardcoding this into `ApprovalService` (an `if riskLevel == CRITICAL` chain growing with every new policy) is exactly the anti-pattern the prompt explicitly warns against — this needs to be policy-driven, the same way risk limits are data, not code.

## Schema

```sql
CREATE SCHEMA IF NOT EXISTS approvals;

CREATE TABLE approvals.approval_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    -- Matching criteria: the first policy (by priority) whose criteria match the
    -- transaction's amount range AND risk level wins.
    min_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    max_amount NUMERIC(19,4),                        -- null = unbounded
    risk_levels VARCHAR(10)[] NOT NULL,               -- e.g. {'HIGH','CRITICAL'}
    priority INT NOT NULL,                            -- lower = evaluated first
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE approvals.approval_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id UUID NOT NULL REFERENCES approvals.approval_policies (id),
    step_order INT NOT NULL,                          -- 1, 2, 3... sequential
    required_role VARCHAR(20) NOT NULL,                -- e.g. 'EMPLOYEE', 'MANAGER' (new role, see below)
    UNIQUE (policy_id, step_order)
);

CREATE TABLE approvals.approval_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    approval_step_id UUID NOT NULL REFERENCES approvals.approval_steps (id),
    assigned_to_user_id UUID,             -- nullable: null = "any qualifying role holder", 
                                           -- non-null = specifically delegated/escalated to one person
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DECIDED','SKIPPED')),
    sla_due_at TIMESTAMPTZ,                -- see §10 for SLA design
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE approvals.approval_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_assignment_id UUID NOT NULL REFERENCES approvals.approval_assignments (id) UNIQUE,
    decided_by_user_id UUID NOT NULL,
    decision VARCHAR(8) NOT NULL CHECK (decision IN ('APPROVE', 'REJECT')),
    reason VARCHAR(255),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_one_decision_per_assignment UNIQUE (approval_assignment_id)
);

CREATE INDEX idx_approval_assignments_tx ON approvals.approval_assignments (transaction_id);
CREATE INDEX idx_approval_assignments_pending ON approvals.approval_assignments (assigned_to_user_id, status)
    WHERE status = 'PENDING';
```

**A new role, `MANAGER`, is required** — an additive change to `users.roles` (a new seeded row, exactly like adding any other role today; no change to the `Role`/`RoleName` structure itself, just a new constant and a new seed row). `COMPLIANCE` and `OPERATIONS` are similarly additive role rows for the `HIGH_RISK` policy example in the prompt. **This is the one place in this whole design that touches the existing `users` schema at all**, and it does so via the exact mechanism that schema already supports (a new row in an existing table), not a structural change.

## Example policies (data, not code)
| Policy | Amount range | Risk levels | Steps |
|---|---|---|---|
| Low value | $0–$1,000 | LOW, MEDIUM | 1: any `EMPLOYEE` |
| Medium value | $1,000–$25,000 | LOW, MEDIUM, HIGH | 1: `EMPLOYEE` with a seniority marker (see note) |
| High value | $25,000+ | any | 1: `EMPLOYEE`, 2: `MANAGER` |
| High risk | any | HIGH, CRITICAL | 1: `OPERATIONS`, 2: `COMPLIANCE`, 3: `MANAGER` |

**Note on "seniority":** the current `User`/`Role` model has no seniority/tenure concept at all. Rather than inventing one for this document (which the prompt didn't ask for and which would be a real, separate design decision about how seniority is defined and maintained), the "Medium value → senior employee" requirement from the prompt is satisfied here by treating "senior" as **a distinct role** (or a boolean flag addable to `User` later) rather than a computed property — flagged explicitly as a simplification, not silently glossed over.

## Approval flow
```
Transaction enters PENDING_APPROVAL, carrying its RiskAssessment.risk_level
      |
      v
ApprovalPolicyResolver: find the first active policy (by priority) whose 
  [min_amount, max_amount) contains the transfer amount AND whose risk_levels 
  includes the assessment's risk_level
      |
      v
For each approval_step in that policy, in step_order: create one 
  approval_assignment (status PENDING, sla_due_at computed per §10)
      |
      v
Step 1 becomes visible in the Approval Queue (§13) to any qualifying-role 
  employee (or the specifically assigned/delegated person)
      |
      +-- APPROVE recorded -> step 1's assignment.status = DECIDED
      |        |
      |        +-- more steps remain -> step 2's assignment becomes visible
      |        +-- no more steps    -> transaction PENDING_APPROVAL -> APPROVED
      |
      +-- REJECT recorded -> transaction PENDING_APPROVAL -> REJECTED 
                             (immediately - a single REJECT at any step ends the 
                              whole workflow; there is no "majority vote")
```

## Separation-of-duties / self-approval prevention
Generalizes the existing single-level guard (`ApprovalService.assertNotSelfApproval`) to: **an approver at any step must not be the transaction's original initiator, and must not be the same person who decided any earlier step in the same workflow.** Enforced in `ApprovalDecisionService.recordDecision()` by checking the new decision's `decided_by_user_id` against `transaction.initiatedByUserId` **and** every existing row in `approval_decisions` for that transaction's assignments — a direct, mechanical extension of a rule this codebase already trusts and has a passing test for (`ApprovalServiceTest.approveRejectsSelfApproval`), not a new concept.

## Delegation and escalation
- **Delegation:** an `approval_assignment.assigned_to_user_id` can be set to route a specific pending item to a specific person (e.g. a manager delegating their queue while on leave) — modeled as **updating the assignment**, not creating a new one, so the SLA clock (§10) continues uninterrupted.
- **Escalation:** when `sla_due_at` passes with no decision, a scheduled job (the same kind of `@Scheduled` component the outbox relay in §11 introduces — this design's *second* use of that mechanism, not a new one invented per-module) reassigns the pending assignment to a broader/higher role (e.g. from "any `EMPLOYEE`" to "any `MANAGER`") and publishes an event `ApprovalEscalatedEvent`, consumed by `opsexceptions` (§10) to surface it in the Exception Console if it breaches SLA a second time.

## Approval limits and audit trail
"Approval limits" (a specific employee being authorized to approve only up to a personal cap, independent of the policy-level amount bands) is **explicitly not designed here** — the prompt's approval-policy examples are all amount/risk-band-based (a property of the *transaction*), and a per-*approver* cap would be a genuinely separate, additional axis of configuration. Naming this gap explicitly rather than quietly conflating it with policy amount bands. Every decision, by design, is individually durable (`approval_decisions`, one row per decision, immutable, `decided_by_user_id` always populated) — this **is** the approval audit trail, and it is also mirrored into `audit.audit_logs` via `ApprovalDecisionRecordedEvent`, exactly like every other audited action in the existing system.

---

# 9. Reconciliation

## The problem this solves
§5 established that `ledger_accounts.ledger_balance` and `accounts.accounts.balance` are updated atomically and should therefore always agree — but "should always agree because the code says so" is precisely the kind of claim a financial system must **continuously verify**, not just assert. Reconciliation is that verification, running independently of live transaction processing so it can never be the thing that slows down or blocks a customer's transfer.

## Design

```sql
CREATE SCHEMA IF NOT EXISTS reconciliation;

CREATE TABLE reconciliation.reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_type VARCHAR(30) NOT NULL DEFAULT 'ACCOUNT_LEDGER_BALANCE',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(12) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
    accounts_checked INT,
    mismatches_found INT
);

CREATE TABLE reconciliation.reconciliation_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES reconciliation.reconciliation_runs (id),
    account_id UUID NOT NULL,
    expected_balance NUMERIC(19,4) NOT NULL,   -- from the ledger
    actual_balance NUMERIC(19,4) NOT NULL,     -- from accounts.accounts
    difference NUMERIC(19,4) NOT NULL,
    severity VARCHAR(10) CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),  -- null if matched
    assigned_to_user_id UUID,
    resolution_status VARCHAR(12) DEFAULT 'OPEN' CHECK (resolution_status IN ('OPEN','INVESTIGATING','RESOLVED')),
    resolution_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_recon_results_run ON reconciliation.reconciliation_results (run_id);
CREATE INDEX idx_recon_results_open ON reconciliation.reconciliation_results (resolution_status) 
    WHERE resolution_status != 'RESOLVED';
```

**Why every account gets a result row, not just mismatches:** `reconciliation_runs.accounts_checked` and `mismatches_found` need a denominator, and a dashboard showing "reconciliation health over time" needs to distinguish "we checked 10,000 accounts and found 0 mismatches" from "we checked 10 accounts" — a run with no result rows at all is indistinguishable from a run that silently did nothing.

## Job flow
```
Scheduled trigger (e.g. hourly - a config value, not hardcoded)
      |
      v
ReconciliationJob (a new @Scheduled component - this design's third use of that
  mechanism, alongside the outbox relay and approval-SLA escalation)
      |
      +-- creates a reconciliation_run row (status RUNNING)
      +-- for each account: 
      |      expected = ledger.ledger_accounts.ledger_balance for that account
      |      actual   = accounts.accounts.balance
      |      difference = actual - expected
      |      if difference != 0: insert a reconciliation_result with severity 
      |         scaled by |difference| (e.g. <$1 -> LOW, <$100 -> MEDIUM, else -> HIGH/CRITICAL)
      |         and publish ReconciliationMismatchDetectedEvent
      +-- updates the run row (status COMPLETED, counts populated)
      |
      v
ReconciliationMismatchDetectedEvent consumed by opsexceptions (§10) -> creates 
  an OperationalException, so every mismatch automatically becomes a tracked, 
  assignable, SLA-bound operational item - reconciliation never just logs a 
  warning and hopes someone notices.
```

## Running without affecting live transaction processing
Two deliberate design choices make this safe: (1) the job reads via plain `SELECT`s with **no locking** (`READ COMMITTED`, Postgres's default isolation, is sufficient — a reconciliation snapshot that's a few milliseconds stale is exactly as useful as one that's perfectly synchronized, since the whole point is catching *persistent* drift, not momentary in-flight states); (2) it runs as a `@Scheduled` background component in the same JVM for now (no new infrastructure — see §18's honest assessment of when that would need to change), explicitly **not** wrapped in a single long-running transaction across all accounts, so it never holds a lock or a long-lived transaction that could contend with live traffic.

## Future: external settlement totals
The prompt asks for a design that can later compare against "external settlement totals" — this schema already accommodates that without a redesign: `run_type` is already a discriminator column (`ACCOUNT_LEDGER_BALANCE` today; a future `EXTERNAL_SETTLEMENT` run type would populate `expected_balance` from an external feed instead of the internal ledger, using the exact same `reconciliation_results` shape). Not designed further here since no external settlement integration exists yet to reconcile against.

---

# 10. Exception Management (Operational Exceptions)

## Naming note (a deliberate, explicit choice)
This module is named `opsexceptions` in code (package `com.platform.opsexceptions`), **not** `exceptions`, specifically to avoid collision — both in package-naming clarity and in a new engineer's mental model — with `java.lang.Exception` and the existing `com.platform.shared.exception` package, which serves a completely different purpose (HTTP-error mapping, not operational-workflow tracking). This kind of naming collision, left unaddressed, is exactly the sort of thing that causes real confusion six months into a project — worth deciding deliberately now rather than discovering it as a problem later.

## The problem this solves
Today, when something fails partway through a multi-step process (a ledger posting error, a notification email that can't send, a reconciliation mismatch), the *only* trace is a log line and, for a couple of cases, a caught exception that degrades gracefully (the existing `NotificationService` email try/catch, for instance). Nothing tracks "this specific failure needs a human to look at it," nothing assigns it to anyone, nothing measures how long it's been open. An operations team cannot run off log files.

## Schema

```sql
CREATE SCHEMA IF NOT EXISTS opsexceptions;

CREATE TABLE opsexceptions.operational_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exception_type VARCHAR(40) NOT NULL,   -- 'PAYMENT_FAILURE','LEDGER_POSTING_FAILURE',
                                            -- 'NOTIFICATION_FAILURE','RECONCILIATION_MISMATCH',
                                            -- 'APPROVAL_SLA_BREACH','DUPLICATE_TRANSACTION_DETECTED',
                                            -- 'INTEGRATION_FAILURE' (future external integrations)
    severity VARCHAR(10) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status VARCHAR(15) NOT NULL DEFAULT 'OPEN' 
        CHECK (status IN ('OPEN','INVESTIGATING','RETRYING','ESCALATED','RESOLVED')),
    related_entity_type VARCHAR(30),      -- 'TRANSACTION','RECONCILIATION_RESULT','NOTIFICATION', etc.
    related_entity_id VARCHAR(64),
    correlation_id VARCHAR(64),           -- ties back to the originating request, same MDC value as logs
    error_reason TEXT NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    assigned_to_user_id UUID,
    assigned_team VARCHAR(50),            -- for team-level (not yet user-level) assignment
    sla_due_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolution_notes TEXT
);

CREATE INDEX idx_ops_exceptions_open ON opsexceptions.operational_exceptions (status) 
    WHERE status != 'RESOLVED';
CREATE INDEX idx_ops_exceptions_correlation ON opsexceptions.operational_exceptions (correlation_id);
CREATE INDEX idx_ops_exceptions_related ON opsexceptions.operational_exceptions (related_entity_type, related_entity_id);
```

## Lifecycle
Both shapes the prompt described are supported by one status enum, since `ESCALATED` and `RETRYING` are not mutually exclusive with each other in practice but always resolve to the same two outcomes:
```
OPEN --> INVESTIGATING --> RETRYING --> RESOLVED
  |            |                            ^
  |            +--> ESCALATED ---------------+
  +----------------------------------------->  (directly resolved, e.g. a false positive)
```

## Design as an operational workflow, not merely an error table
The distinction the prompt draws is real and is honored here in two concrete ways: (1) every row is **assignable** (`assigned_to_user_id`/`assigned_team`) and carries an **SLA** (`sla_due_at`, populated per `exception_type`/`severity` from a small, additive `SlaPolicy` config table — same data-driven pattern as risk limits and approval policies, not a third bespoke mechanism), so the Exception Console (§13) can show "mine," "my team's," "overdue," not just "all errors ever." (2) It is a **sink other modules report into via events**, not something modules query into — `reconciliation`, the outbox relay's dead-letter path (§11), and any future failure path publish an event (`OperationalExceptionOpenedEvent` with the fields above); `opsexceptions` owns exactly one job, turning that event into a tracked row, with zero business logic about *why* the failure happened (that stays in the module that experienced it).

---

# 11. Outbox / Event Architecture

## What "reliable event delivery" actually means here, concretely
Today, `NotificationService.sendEmail()` is wrapped in a try/catch specifically because a MailHog/SMTP failure must not roll back the customer-visible transfer-approval transaction it's coupled to. That try/catch is a **symptom of exactly the problem the outbox pattern exists to solve properly** — right now, if the *whole* `NotificationEventListener.onTransactionStatusChanged()` method failed for any reason other than the one already-guarded email call (e.g. the `Notification` row's own `INSERT` failed), it *would* roll back the transfer. This design fixes that specific, real, already-identified fragility.

## Which operations move to the outbox, and which stay exactly as they are

| Consumer | Consistency requirement | Mechanism |
|---|---|---|
| Ledger posting (§5) | **Strongly consistent** - must commit or roll back with the financial write | Stays exactly as today: synchronous, same transaction, direct method call (not even event-driven - it's core business logic, not a "side effect") |
| Core audit record of a financial state change | **Strongly consistent** - losing an audit record of money moving is unacceptable | Stays synchronous, same transaction, exactly as `AuditEventListener` works today |
| Notifications (in-app row + email) | **Eventually consistent** - a customer seeing their notification 2 seconds late is fine; it must never block or fail the transfer | **Moves to the outbox** |
| Risk-analytics feed, reporting feed, future fraud-detection feed | **Eventually consistent** by nature - these are downstream consumers of history, not participants in the transaction | **New outbox consumers**, not built here, but the outbox design accommodates them without further schema change |

This is the explicit "strongly consistent financial operations vs. eventually consistent side effects" line the prompt asked for, and it is drawn using the existing codebase's own already-half-solved problem as the evidence, not an abstract argument.

## Schema (one outbox table per producing module — colocated for transactional atomicity)

```sql
-- Example: lives in the transactions schema, since transactions is the producer.
CREATE TABLE transactions.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(60) NOT NULL,        -- e.g. 'TransactionStateChangedEvent'
    payload JSONB NOT NULL,                 -- the event's fields, serialized
    correlation_id VARCHAR(64),
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','PROCESSED','DEAD_LETTER')),
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_ready ON transactions.outbox_events (next_attempt_at) 
    WHERE status = 'PENDING';
```

**Why per-module tables, not one shared `eventing.outbox_events` table:** the entire point of the outbox pattern is that the event row is written **in the exact same database transaction** as the business state change it describes. If the table lived in a different schema owned by a different (future-extractable) module, that atomicity guarantee would already be broken by the same reasoning that justifies *not* having cross-schema foreign keys elsewhere in this design — so each producing module owns its own outbox table, and the `eventing` module owns only the **relay** that polls across all of them (a small, explicit list of tables it knows about — not a cross-schema query, a sequence of per-schema queries).

## The relay (polling publisher, no new broker — and exactly why not)
```
@Scheduled (e.g. every 2 seconds - this design's fourth use of the scheduling 
  mechanism already introduced for reconciliation and SLA escalation)
      |
      v
For each known outbox table: SELECT ... WHERE status='PENDING' AND 
  next_attempt_at <= now() ORDER BY created_at LIMIT N FOR UPDATE SKIP LOCKED
      |      (SKIP LOCKED is the concurrency-safety mechanism - see §16's 
      |       "worker processing the same outbox event twice" scenario)
      v
Mark PROCESSING -> re-publish as the existing in-process Spring event type 
  (the SAME event classes and SAME @EventListener consumers that already 
  exist - NotificationEventListener does not change at all) -> on success, 
  mark PROCESSED; on failure, increment attempts, compute next_attempt_at 
  with exponential backoff, or mark DEAD_LETTER after a configured max 
  attempts and publish an OperationalExceptionOpenedEvent (§10)
```

**Why not introduce Kafka/RabbitMQ now, explicitly:** the prompt requires this justification for every technology, so, plainly — this system's actual event volume does not remotely need a durable, partitioned, multi-consumer-group message broker; the entire value being sought (atomicity with the business write, at-least-once delivery, decoupling of a failure-prone consumer from the producer's transaction) is fully delivered by a Postgres table plus a scheduled poller, with **zero new infrastructure** in the Docker Compose stack. **What a broker would add that this doesn't:** true multi-consumer fan-out across separate *processes* (not just separate in-process listeners, which this design already supports), and delivery ordering/partitioning guarantees at a scale this system isn't at. **The explicit evolution path:** if `notifications` (or any future consumer) is ever extracted into its own deployable service, the relay's "re-publish as an in-process Spring event" step becomes "publish to a broker topic" — the outbox table, the polling/locking logic, and the retry/backoff/dead-letter state machine **do not change at all**. This is precisely why the pattern is worth adopting now even without a broker: it's the same design either way, and adopting it early costs nothing extra.

## Idempotent consumers
Because the relay's retry can, in a crash-at-the-wrong-moment scenario, re-deliver an already-processed event (mark-PROCESSED and the actual side effect are two separate steps, not atomic with each other — an honest, named limitation of at-least-once delivery, as opposed to the impossible-to-cheaply-achieve exactly-once), every consumer of a relayed event must be idempotent. `NotificationEventListener` already effectively is (writing a `Notification` row twice for the same event is a minor, visible-but-not-dangerous duplicate today; hardening this fully would mean giving `Notification` its own dedupe key derived from the outbox event's `id` — named here as a concrete follow-up, not implemented in this design pass).

---

# 12. Security Architecture — Threat Model and Controls

## Threat model, mapped to controls

| Threat | Currently mitigated? | New/extended control |
|---|---|---|
| Credential stuffing / brute force | **No** (login failures are counted via a metric but nothing acts on the count) | Rate limiting on `/auth/login` and `/auth/register` (Bucket4j + Redis — Redis is already in the stack, so this is a scope *extension* of an existing dependency, not a new one); automated account lockout after N consecutive failures, actually setting `UserStatus.LOCKED` (the enum value already exists and is currently dead) |
| Token theft (access token) | **Partially** — short 15-minute lifetime limits the blast radius, in-memory-only storage (never `localStorage`) closes the most common XSS exfiltration path | No change needed — the existing design is already sound here; the real remaining gap is XSS itself, not token storage |
| Token theft (refresh token) | **Partially** — httpOnly cookie prevents JS exfiltration; rotation prevents replay of an already-used token | **Refresh-token family tracking + reuse detection**: tag every refresh token with a `family_id` (the same value across every rotation descending from one original login); if a token from a family is presented **after** a later token in the same family has already been used (detected via `revoked_at IS NOT NULL` on the presented token), treat this as a signal of theft and revoke the **entire family**, forcing re-authentication — this is the single highest-value addition to the existing refresh mechanism, and it requires only one new column (`family_id`) on the existing `auth.refresh_tokens` table, not a redesign |
| Session hijacking | Access token in memory only mitigates persistence; no device-binding exists | **Session/device management**: a new `auth.sessions` table (one row per login, `family_id`, `device_label` from the `User-Agent` header, `ip_address`, `last_seen_at`) lets a user see and individually revoke "my devices" — additive, does not change how tokens themselves work |
| Replay attacks | Access tokens are short-lived and signature-verified; refresh rotation already prevents refresh-token replay | Covered by the above; no further change |
| Privilege escalation | `@PreAuthorize` + JWT-embedded roles, re-synced from the DB on every refresh (already limits a revoked role to a 15-minute window) | No structural change; the state-machine transition table (§6) closes a *related* gap (an authenticated-but-wrong-state action, as opposed to wrong-role) |
| Unauthorized approval | Single self-approval guard today | Generalized separation-of-duties across every step of a multi-level workflow (§8) |
| Insider threat (a legitimate employee acting maliciously) | Audit log exists but has no database-enforced immutability | Named, not solved, in this design — a database-level `REVOKE UPDATE, DELETE` grant on `audit.audit_logs` and the new `ledger.*` tables for the application's own DB role is the concrete next step, explicitly flagged as not yet implemented |
| Parameter tampering | Ownership checks in the service layer already prevent acting on another user's resource by ID substitution | No change needed |
| IDOR | Same as above — already correctly guarded (`AccountService.getOwnedAccount`, etc.) | No change needed; the RBAC matrix test (§16) already covers this class of check and would extend naturally to new endpoints |
| Duplicate transaction submission | Idempotency key on transfer creation | Extended to approval decisions (§8), and to outbox event delivery (§11's idempotent-consumer discussion) |
| Audit tampering | Convention-only immutability today | Same gap as "insider threat" above — named, not yet solved |
| Malicious direct database access | Not mitigated by the application at all (a DB-level concern) | Out of scope for the application layer; the concrete control is DB role/grant hardening, named as a deployment/ops concern, not an application-code one |
| Event replay (outbox) | N/A (no outbox exists today) | At-least-once delivery is accepted, not "solved" — mitigated by consumer idempotency, explicitly named as a limitation in §11, not glossed over |

## MFA
Added as a new optional-then-mandatory-for-EMPLOYEE/ADMIN-roles step in the login flow: after password verification succeeds, if the user has MFA enabled, `AuthService.login()` returns a distinct `MFA_REQUIRED` response (not a token) containing a short-lived challenge ID; a second endpoint verifies a TOTP code against that challenge before issuing the actual access/refresh token pair. This is an **additive branch** in the existing login flow, not a replacement of it — a user without MFA enabled sees no change at all.

---

# 13. Frontend Architecture (Operations UI)

## Design principles, addressing the explicit "make it look professional, not overdone" request
The existing frontend already follows a sound, minimal foundation: Tailwind with CSS-variable-driven theming, no animation library, no gradient usage, a small hand-rolled component kit rather than a heavy third-party UI framework. The additions below **extend that same foundation** rather than introducing a competing design language:

- **Typography:** one type scale, used consistently — a single sans-serif stack (system font stack, already implicit in Tailwind's defaults; no new webfont dependency), with size/weight doing the hierarchy work instead of color or decoration. Numbers (amounts, IDs, correlation IDs) render in a monospace variant for scannability — already the pattern the existing Audit Explorer prototype uses for `metadata`, generalized.
- **Density over whitespace:** operations users scan tables and queues all day — the existing card-heavy dashboard style (large padding, big rounded corners) is appropriate for the *customer-facing* pages and stays exactly as-is; the *new* operations pages (Approval Queue, Transaction 360, Audit Explorer, Exception Console) use a denser table-first layout, more rows visible per screen, smaller vertical padding — a deliberate, explicit **second density mode** within the same design system, not a redesign of the existing customer pages.
- **Color used semantically, never decoratively:** exactly four status colors, reused everywhere (risk badges, SLA badges, exception severity, transaction status) — green (healthy/approved/resolved), amber (pending/approaching-SLA/medium risk), red (rejected/breached/high-critical risk/failed), gray (neutral/informational). No gradient fills, no drop shadows beyond the existing subtle card border, no decorative icons beyond the existing `lucide-react` set already in use.
- **No animation beyond what already exists** (the existing theme-toggle transition and standard browser focus states) — no page-transition animation, no skeleton shimmer effects beyond a plain pulsing opacity (the existing `DashboardPage` skeleton pattern, reused, not elaborated on).
- **Persistent, role-aware navigation:** the existing `AppLayout` header nav pattern (role-conditional `<Link>`s) is preserved and extended, not replaced with a sidebar — consistent with "don't redesign what's working," and the current user count of nav items per role stays manageable enough that a header nav remains the right choice (a sidebar becomes justified only if the operations nav grows meaningfully larger than the ~4-6 items per role it has today — named as a future trigger, not implemented preemptively).

## New pages, each following the existing feature-folder convention (`features/<name>/{api,pages,types}`)

**Operations Dashboard** (`features/operations/pages/OperationsDashboardPage.tsx`) — a denser variant of the existing `AdminOverviewPage` pattern (stat cards + breakdown cards), extended with: pending-approval count, high-risk transaction count, open exception count, reconciliation status (last run's mismatch count), SLA breach count. Every stat card links directly to the filtered list view it summarizes (the Approval Queue pre-filtered to "high risk," the Exception Console pre-filtered to "open") — a concrete, low-cost interaction pattern that makes the dashboard actionable rather than decorative.

**Approval Queue** (extends the existing `ApprovalQueuePage`, not a rewrite) — adds: a filter bar (risk level, SLA status, amount range — plain `<select>`/`<input>` elements, consistent with every existing form in the app, no new form library), a sortable table (client-side sort on the already-small result set — a full table library is not justified at this data volume, consistent with the existing handover document's own assessment of when one would be), a risk badge and SLA badge per row (the same `StatusBadge` component pattern already in use, generalized to accept a badge "kind" prop), and a slide-in **drawer** (a new, small, reusable `Drawer` component — the one genuinely new UI primitive this design introduces, because "detail panel without leaving the list" is a real, repeated need across the Approval Queue, Transaction 360 entry point, and Exception Console) showing a transaction preview before committing to a full-page review.

**Transaction 360** (`features/transactions/pages/TransactionDetailPage.tsx`) — a full-page detail view (not a drawer, since it aggregates data from five different modules and deserves its own URL/route for linking/bookmarking from an exception or audit entry): transaction fields, a **timeline** component (a new, small, reusable primitive — a vertical list of `{timestamp, actor, event}` rows, fed directly by the state-machine transition events from §6, so this component requires no new backend endpoint beyond "list state transitions for a transaction ID"), risk assessment (with its `reasons` array rendered as a plain list — no chart needed for 1-5 short strings), approval history (each step and its decision), ledger entries (the actual journal lines, rendered as a small table — this is the page where a real double-entry ledger becomes visibly worth having), and the correlation ID (a copy-to-clipboard affordance, since its entire purpose is being pasted into Kibana).

**Customer 360** (`features/admin/pages/CustomerDetailPage.tsx`, admin/employee-accessible) — profile, accounts and balances (reusing the existing `AccountResponse` shape and `formatCurrency` helper directly), transaction history, beneficiaries, applicable risk limits (§7's `LimitPolicy` rows in scope for this customer), notifications, and an audit-activity feed scoped to `actorUserId = this customer` (a filtered view of the same Audit Explorer query, not a separate backend).

**Audit Explorer** (extends the existing `AuditDashboardPage`, which already has actor/action/entity-type filtering and pagination) — adds date-range, correlation-ID, transaction-ID, customer, and severity filters (severity is new — most existing audit actions don't carry one, but the new `ledger`/`risk`/`reconciliation`/`opsexceptions` events do, so this filter is meaningful for the *new* event types even though it's a no-op for older entries, which is fine and expected, not a bug).

**Exception Console** (`features/opsexceptions/pages/ExceptionConsolePage.tsx`) — a table (open exceptions, severity, SLA countdown, assignment, retry count) with the same drawer-based detail pattern as the Approval Queue, and inline status-transition actions (Investigate, Retry, Resolve — each a small `POST`, following the exact same mutation-then-invalidate TanStack Query pattern every existing page already uses).

**System Health** (`features/operations/pages/SystemHealthPage.tsx`) — this one is **explicitly a thin wrapper linking out to Grafana**, not a rebuilt dashboard in React. Grafana already does this job well (§17); duplicating live metric visualization inside the React app would be maintaining two dashboards that inevitably drift — the React page shows only the handful of numbers an operator needs at a glance without leaving the app (API health from `/actuator/health`, outbox queue depth via a new small backend endpoint, notification-processing lag) and links out for anything deeper.

## Reusable design-system additions (the complete, deliberately short list)
`Drawer`, `Timeline`, a generalized `Badge` (subsuming today's transaction-only `StatusBadge` into risk/SLA/exception-severity variants via a `kind` prop, not four separate components), a `DataTable` wrapper (a thin convenience layer over the existing plain-HTML-table pattern, adding consistent sort-header styling and an empty-state slot — **not** a new table library/dependency, an internal component). Every other page continues to use the existing `Button`/`Input`/`Alert` primitives unchanged.

## State management, API layer, routing
Unchanged patterns, extended with new feature folders: TanStack Query for all new server state, Zod + React Hook Form for the (few) new forms this design introduces (limit-policy editing, approval-policy editing — admin-only configuration screens, deliberately simple CRUD forms, not designed in further detail here since they follow the exact `UserManagementPage` pattern already in the codebase). New routes are added to the existing flat route table in `App.tsx`, gated by the existing `ProtectedRoute roles={[...]}` pattern, including the two new roles (`MANAGER`, `COMPLIANCE`/`OPERATIONS`) introduced in §8.

## Loading / empty / error / retry / partial-failure states
The existing `ErrorBoundary` (route-level, resets on navigation) and TanStack Query's built-in `isLoading`/`isError` states already give every new page the same baseline the existing pages have. The one genuinely new state this design's data shapes require: **partial failure** — a Transaction 360 page whose ledger-entries fetch fails while everything else loads fine should show every section that succeeded and a small inline "couldn't load ledger entries — retry" affordance in just that section, not a full-page error. This means Transaction 360 fires its several data fetches as independent `useQuery` calls (already how TanStack Query is used everywhere in the app) rather than one aggregated call — the pattern already implies the right failure isolation, it just needs to be an explicit, named requirement for this page rather than an accident of how the hooks happen to be called.

---

# 14. Database Changes — Consolidated

Every new table, in full, is already specified in its module's section (§5 ledger, §7 risk, §8 approvals, §9 reconciliation, §10 opsexceptions, §11 outbox). This section is the migration-ordering summary:

| Migration | Contents | Depends on |
|---|---|---|
| `V8__ledger_schema.sql` | `ledger` schema + 3 tables | none (additive) |
| `V9__ledger_backfill.sql` (repeatable, or an admin-triggered job — see §5 Stage 2) | one opening-balance journal entry per existing account | V8 |
| `V10__risk_schema.sql` | `risk` schema + 2 tables | none |
| `V11__approvals_schema.sql` | `approvals` schema + 4 tables, seed `MANAGER`/`COMPLIANCE`/`OPERATIONS` roles into the **existing** `users.roles` table | none structurally, but logically follows risk (policies reference risk levels) |
| `V12__reconciliation_schema.sql` | `reconciliation` schema + 2 tables | V8 (reconciles against ledger) |
| `V13__opsexceptions_schema.sql` | `opsexceptions` schema + 1 table | none |
| `V14__outbox_tables.sql` | one `outbox_events` table added to each producing module's **existing** schema (`transactions.outbox_events`, etc. — an addition within an existing schema, not a new schema) | none |
| `V15__auth_session_hardening.sql` | `family_id` column on existing `auth.refresh_tokens`; new `auth.sessions` table | none |

Every migration is purely additive (`CREATE SCHEMA`, `CREATE TABLE`, or a single nullable `ALTER TABLE ... ADD COLUMN`) — **none of them drop or rename an existing column, table, or schema.** This is the concrete, checkable evidence that the "additive extension, not a rewrite" constraint is honored at the database level, not just asserted in prose.

---

# 15. API Changes

**New endpoints** (grouped by module; full request/response contracts would follow the exact existing `ApiError`/DTO-record conventions already used everywhere — not re-specified field-by-field here to avoid duplicating §11's/§5's schema definitions, which already imply the DTO shapes directly):

- `risk`: `GET /api/v1/admin/risk/policies`, `POST/PATCH /api/v1/admin/risk/policies/{id}` (ADMIN) — CRUD on `LimitPolicy`, following the exact pattern `AdminUserController` already establishes for admin-managed configuration.
- `approvals`: `GET /api/v1/admin/approval-policies` (+ CRUD, ADMIN); `GET /api/v1/approvals/my-queue` (any of `EMPLOYEE`/`MANAGER`/`COMPLIANCE`/`OPERATIONS` — returns assignments matching the caller's role, generalizing today's `GET /transactions/pending`, which becomes a thin wrapper/redirect once this exists); `POST /api/v1/approvals/{assignmentId}/decide`.
- `reconciliation`: `GET /api/v1/admin/reconciliation/runs`, `GET /api/v1/admin/reconciliation/runs/{id}/results` (ADMIN).
- `opsexceptions`: `GET /api/v1/ops/exceptions` (filterable, paginated — following the exact `AuditController` pagination pattern already built), `PATCH /api/v1/ops/exceptions/{id}` (status/assignment changes).
- `transactions` (extended, not replaced): `GET /api/v1/transactions/{id}/timeline` (state-machine transition history, feeding the Transaction 360 timeline), `GET /api/v1/transactions/{id}/ledger-entries`.
- `auth` (extended): `POST /api/v1/auth/mfa/verify`, `GET/DELETE /api/v1/auth/sessions` (list/revoke own devices).

**Changes to existing endpoints:** `POST /api/v1/transactions/transfer`'s response now reflects the richer state-machine status (`SUBMITTED` instead of today's `PENDING` as the initial state — a **response value change**, not a shape change, so existing frontend code that just displays the status string keeps working, though the `TransactionStatus` TypeScript union type in `features/transactions/types` needs its literal values extended, a one-line change). `POST /api/v1/transactions/{id}/approve` and `/reject` are **not removed** — for the "Low value" policy (§8), a single-step approval is still exactly what happens, so these endpoints remain the correct, simple path for the common case, internally now going through the new `ApprovalDecisionService` rather than the old flat `ApprovalService`, with identical external behavior for a single-approver policy.

---

# 16. Testing Strategy

## Unit
- **Risk rules:** `RuleBasedRiskStrategyTest` — each `LimitPolicy` type (per-transaction, daily cumulative, velocity) tested in isolation with a mocked `TransferContext`; a combined-rules test proving the highest-severity triggered rule wins.
- **Approval policies:** `ApprovalPolicyResolverTest` — policy matching by amount range and risk level, including boundary conditions (amount exactly at a range edge) and the "first matching policy by priority wins" rule.
- **State machine:** `TransactionStateMachineTest` — exhaustively parameterized over every `(from, to)` pair in the full state cross-product, asserting the valid ones succeed and **every single invalid one throws** — this is the test that makes the transition table in §6 a verified fact, not just a diagram.
- **Ledger balancing:** `LedgerServiceTest` — asserts `LedgerService.post()` rejects any journal entry whose lines don't sum to zero per currency; a happy-path test asserting a $100 transfer produces exactly one entry with a $100 DEBIT and a $100 CREDIT line.
- **Idempotency:** extends the existing `TransferServiceTest` idempotency test to the new approval-decision idempotency key (§8); a new outbox-relay test asserting a `PROCESSED` event is never re-published.
- **Reconciliation:** `ReconciliationJobTest` — given seeded ledger/account balances with a deliberate mismatch, asserts exactly one `reconciliation_result` row with the correct `severity` and that `ReconciliationMismatchDetectedEvent` fires.
- **Exception workflow:** `OperationalExceptionServiceTest` — lifecycle transition validity (mirroring the state-machine test's structure at a smaller scale).

## Integration
Following the existing `*IT.java` + Testcontainers + real Postgres convention exactly (never H2, for the same reasons already established in the existing test suite):
- `LedgerPostingIT` — a real transfer approval, asserting the `Account.balance` and `ledger_accounts.ledger_balance` end up numerically identical, against a real Postgres instance with real Flyway migrations applied.
- `OutboxRelayIT` — asserts an event written to an outbox table is picked up, delivered, and marked `PROCESSED`; a second test using `FOR UPDATE SKIP LOCKED` with two concurrent relay-poll calls to prove no double-delivery (directly testing the scenario in the concurrency table below).
- `ApprovalWorkflowIT` — a full multi-step policy end to end: submit -> risk assessment -> two sequential approval steps -> `APPROVED`.
- `SecurityIT` (extends the existing `RbacMatrixIT` pattern) — the new roles (`MANAGER`, `COMPLIANCE`, `OPERATIONS`) and new endpoints added to the existing representative-endpoint matrix.

## Concurrency tests (explicit scenarios, per the prompt's requirement)

| Scenario | Race condition | Protection | DB behavior | App behavior | User-visible result |
|---|---|---|---|---|---|
| Two employees approve the same transaction simultaneously | Both read `PENDING_APPROVAL`, both attempt to record a decision | `UNIQUE (approval_assignment_id)` on `approval_decisions` (§8) | Second `INSERT` violates the unique constraint | Caught, translated to a `409 Conflict` | Second employee sees "already decided" |
| Two transactions attempt to spend the same balance concurrently | Both read the same `Account.version`, both attempt to debit | Existing `@Version` optimistic lock on `Account` (unchanged), now also on `ledger_accounts.version` (§5) | Second `UPDATE`'s `WHERE version = ?` matches zero rows | `ObjectOptimisticLockingFailureException` propagates — **currently uncaught with a retry**, same honest gap the existing handover document already names for the pre-ledger system; this design does not silently fix it, it inherits and re-flags it | Second request currently surfaces a generic 500 — a named follow-up (a bounded retry-with-backoff wrapper around the whole debit+credit+ledger-post sequence) is recommended but not implemented in this design pass |
| Duplicate API request (customer double-clicks Submit) | Two identical `POST /transfer` calls | Existing idempotency-key mechanism (unchanged) | Second call's `findByIdempotencyKeyAndInitiatedByUserId` finds the first row | No new `INSERT`; the existing transaction is returned | Customer sees one transfer, not two |
| Retry after timeout | Client times out waiting for a response, retries with the *same* idempotency key | Same as above | Same as above | Same as above | Same as above — this is precisely the scenario idempotency keys exist for |
| Worker processes the same outbox event twice | Two relay instances (or one instance's overlapping scheduled runs) poll at the same moment | `SELECT ... FOR UPDATE SKIP LOCKED` (§11) | The second poller's query simply doesn't see rows already locked by the first | No special handling needed — the database does the work | No duplicate delivery, assuming consumer-side idempotency also holds (§11's honest caveat) for the rarer crash-between-mark-and-deliver case |
| Concurrent reconciliation runs | Two scheduled triggers overlap (e.g. a slow run still going when the next interval fires) | A simple advisory lock (`pg_try_advisory_lock`) around the job's entry point, or a `RUNNING`-status check against `reconciliation_runs` before starting a new one | The second attempt's lock acquisition fails / sees an existing `RUNNING` row | Second invocation exits immediately, logs a skip | No duplicate reconciliation results; next scheduled run proceeds normally |
| Concurrent account updates (general case) | Any two writers touching the same `Account` row | Existing `@Version` optimistic lock (unchanged) | As above | As above | As above |

## Frontend tests
**Currently zero exist** (an honest, unresolved gap already named in the existing handover document) — this design does not silently continue that gap for its *new* pages. At minimum: the `Drawer`/`Timeline`/generalized `Badge` components (genuinely new, reusable primitives) get component tests via the already-installed-but-unused Vitest + React Testing Library; the Approval Queue's filter-bar interaction gets a test. Full coverage parity with the backend is **not** claimed here — named as a real, sized scope decision (a handful of new-component tests, not a retroactive full suite for the entire existing frontend), consistent with this document's overall instruction to be honest about what's actually proposed versus what's aspirational.

## E2E (Playwright)
One true end-to-end scenario, exactly as the prompt specifies: customer submits a transfer -> risk evaluation (LOW, auto-routes) -> single-step approval by an employee -> ledger posting -> `COMPLETED` -> notification appears in the bell -> audit entry appears in the Audit Explorer, all driven through the real browser UI against a real (Testcontainers-backed, or a dedicated `e2e` Docker Compose profile) backend — not mocked. A second E2E scenario covers the multi-step, higher-value path (two sequential approvers). Playwright is a new dependency (not currently in the stack) — justified specifically because this is the one testing need (real browser, real multi-page navigation, real async waiting) that Vitest/RTL genuinely cannot cover, not adopted for its own sake.

## Failure/retry tests
`OutboxRelayIT`'s dead-letter path (force a consumer to fail repeatedly, assert the event reaches `DEAD_LETTER` status after the configured max attempts and that an `OperationalException` is opened); a ledger-posting-failure test (insufficient funds detected at posting time, asserting `FAILED` status and that the *ledger entry was never posted* — i.e., a rejected posting leaves no journal entry at all, not a posted-then-reversed one, since it never should have posted in the first place).

## Coverage / test strategy summary
Prioritizes depth on the newly-introduced riskiest logic (ledger balancing, state-machine legality, concurrency/idempotency) exactly the way the existing test suite already prioritizes depth on maker-checker and optimistic locking over broad CRUD coverage — a consistent continuation of an already-stated strategy, not a new philosophy.

---

# 17. Observability — Extended

**New business metrics** (Micrometer counters/gauges, following the exact `platform.<module>.<thing>{tag=value}` naming convention the existing `platform.auth.login`/`platform.transactions.created` counters already establish):
- `platform.risk.assessments{riskLevel=LOW|MEDIUM|HIGH|CRITICAL}` (counter)
- `platform.approvals.decisions{decision=APPROVE|REJECT,stepOrder=N}` (counter)
- `platform.reconciliation.mismatches{severity=...}` (counter)
- `platform.opsexceptions.opened{type=...}`, `platform.opsexceptions.open_count` (gauge — current open count, not just a counter of ever-opened)
- `platform.outbox.queue_depth{module=...}` (gauge — pending-row count per producing module's outbox table, the single most operationally important new metric this design adds, since a growing queue depth is the first sign the relay is falling behind or a consumer is failing)
- `platform.outbox.relay_lag_seconds` (gauge — age of the oldest still-`PENDING` row)
- `platform.approvals.sla_breach_count` (gauge)

**Dashboards:** the existing provisioned Grafana dashboard (`platform-overview.json`) gets a **new panel row**, not a replacement — consistent with "additive, not a rewrite" applied to observability config too. A second, new dashboard JSON (`operations-overview.json`) is added specifically for the operations-team audience (queue depth, SLA breaches, reconciliation health) rather than overloading the existing engineering-focused dashboard with operational-audience panels.

**Logs:** no change to the existing correlation-ID/MDC mechanism — every new module's log lines automatically inherit it, exactly as every existing module's do, since `CorrelationIdFilter` runs once per request regardless of which modules that request touches.

**Traces (OpenTelemetry) — evaluated and explicitly deferred, not adopted:** distributed tracing's core value is following one logical operation *across process boundaries*. This system, after every addition in this document, is still **one process** — correlation IDs already give the equivalent value (following one request across every module's log lines) at a fraction of the operational cost (no collector, no trace-storage backend, no sampling-strategy decisions). **The concrete trigger that would justify adopting it:** the first time any module in this design is actually extracted into a separate deployable service (§18/§19's microservice-migration ADR) — at that point, correlation IDs alone stop being sufficient (they don't capture cross-service timing/span data), and OpenTelemetry becomes the right tool, not before.

**Alerts:** not designed in schema-level detail here (Grafana/Prometheus alerting rules are configuration, not architecture), but the concrete list worth alerting on, informed by everything above: outbox queue depth exceeding a threshold, any `CRITICAL`-severity reconciliation mismatch, any SLA breach on a `HIGH`/`CRITICAL`-risk approval, the existing login-failure-rate metric (already collected, never alerted on today — a low-cost, high-value first alert to add).

---

# 18. Deployment

**No new infrastructure is required to ship this entire document.** Every new module is more Spring Boot code in the same JVM process; every new table is more Postgres schema in the same instance; the outbox relay, SLA escalation job, and reconciliation job are `@Scheduled` components in the same process (this system's *first* use of Spring's scheduler — a new but extremely low-cost capability, not a new service). `docker-compose.yml` needs **zero new service entries** — the same `backend` container, the same `postgres` container, the same `redis` container (still narrowly scoped, now additionally used as the rate-limiting counter store for §12's brute-force protection, still not a general cache) handle everything in this document.

**The one deployment-relevant question worth naming explicitly:** should the outbox relay, reconciliation job, and SLA escalation job run as `@Scheduled` methods inside the same web-serving process, or as a separate "worker" process? **Recommendation: same process, for now** — running them separately would be premature optimization for a single-instance deployment; the concrete trigger for splitting them out is the same one that would justify horizontal backend scaling in the first place (multiple web-serving replicas, which would need exactly one of them running these `@Scheduled` jobs, not all of them — solvable with a simple leader-election/advisory-lock mechanism, already partially designed in §16's "concurrent reconciliation runs" protection, generalized). Named as the concrete next step **if and only if** horizontal scaling is actually adopted — not implemented preemptively here.

**CI/CD:** the existing GitHub Actions pipeline (Surefire unit tests -> Failsafe Testcontainers integration tests -> frontend build/audit -> Docker image builds -> CodeQL -> Dependabot) needs no structural change — every new test class in §16 simply adds to the existing `*Test.java`/`*IT.java` glob patterns Surefire/Failsafe already pick up.

---

# 19. Architecture Decision Records

### ADR-01: Ledger as source of truth, materialized `Account.balance` retained as a projection
**Context:** the existing system has one mutable balance column with no independent, provable history. **Problem:** no way to audit or reconcile financial truth. **Options considered:** (a) replace `Account.balance` entirely with a computed-on-read sum of ledger lines; (b) add a ledger and keep the column as an independently-updated cache; (c) add a ledger and keep the column, updated **atomically in the same transaction** as the ledger post. **Decision:** (c). **Rationale:** (a) is correct but too slow for the dominant read pattern (every balance display); (b) risks drift if the two updates aren't coupled. **Tradeoffs:** doubles write volume per transfer; requires a careful five-stage migration (§5) rather than a simple cutover. **Consequences:** reconciliation (§9) becomes necessary as an ongoing proof that the coupling assumption holds in practice, not just in code review.

### ADR-02: Synchronous financial processing, asynchronous side effects via outbox
**Context:** all processing today is synchronous, in one transaction, including side effects (notifications) that don't need to be. **Problem:** a slow/failing side effect can currently block or fail a financial write it shouldn't be coupled to. **Options:** (a) make everything synchronous (status quo); (b) make everything asynchronous via a broker; (c) split by consistency requirement — synchronous for the financial write and its audit record, asynchronous (outbox) for everything else. **Decision:** (c). **Rationale:** directly evidenced by the existing `NotificationService` email try/catch, which is already informally implementing a weaker version of this exact split. **Tradeoffs:** introduces at-least-once (not exactly-once) delivery semantics for the async path, requiring idempotent consumers. **Consequences:** the module boundary between "must be strongly consistent" and "may be eventually consistent" must be maintained deliberately for every *future* addition too, not just the ones in this document.

### ADR-03: Outbox + polling relay, not a message broker
**Context:** reliable event delivery is needed; no broker exists today. **Problem:** would introducing Kafka/RabbitMQ now be justified? **Options:** (a) a broker now; (b) an outbox table + in-process polling relay, broker-deferred. **Decision:** (b). **Rationale:** full value delivered (atomicity, at-least-once, decoupling) with zero new infrastructure; this system's actual volume doesn't need multi-consumer-group fan-out or partitioning. **Tradeoffs:** relay latency is poll-interval-bound (seconds, not milliseconds) — acceptable for notifications, would **not** be acceptable if a future consumer needed near-real-time delivery. **Consequences:** the schema is deliberately broker-agnostic so adopting a broker later changes only the relay's publish step.

### ADR-04: Modular monolith preserved, not migrated toward microservices
**Context:** every new module in this document could, in principle, be its own service. **Problem:** should this document recommend that? **Options:** (a) extract now; (b) preserve the monolith, keep the port-interface seam ready. **Decision:** (b). **Rationale:** no operational or scale pressure exists today that a monolith can't handle; extraction adds distributed-systems complexity (network partitions, distributed transactions/sagas, per-service deployment) with no corresponding benefit yet. **Tradeoffs:** none of this document's new modules get independent deployability or independent scaling. **Consequences:** every new module still follows the port-interface rule specifically so this decision remains reversible later without a redesign.

### ADR-05: Optimistic locking retained (including for the new `ledger_accounts` table), no retry-on-conflict added
**Context:** the existing system already uses optimistic locking with no automated retry, and this design adds a second optimistically-locked table per posting. **Problem:** should a retry wrapper be added now? **Options:** (a) add bounded retry-with-backoff around the whole debit+credit+ledger-post sequence; (b) leave it as-is (conflict surfaces as a 500). **Decision:** (b), explicitly flagged as a named follow-up, not implemented in this pass. **Rationale:** honest scope discipline — this document is already large; adding a retry wrapper is a small, well-understood, independently-shippable change that doesn't need to block everything else here. **Tradeoffs:** a real (if rare, given how uncommon true concurrent-same-account writes are expected to be) user-visible ungraceful failure remains until that follow-up ships. **Consequences:** listed explicitly in §16's concurrency table and §17's alerting recommendations (a spike in this specific 500 would be a concrete, alertable signal that the follow-up has become worth prioritizing).

### ADR-06: Rule-based risk engine now, Strategy-pattern seam for ML later
**Context:** the prompt explicitly forbids ML now but requires the design not to preclude it later. **Problem:** how to keep both true without over-engineering today. **Options:** (a) hardcode rules directly in `TransferService`; (b) a `RiskStrategy` interface with one rule-based implementation today. **Decision:** (b). **Rationale:** the interface costs almost nothing today and is exactly the seam a future ML strategy needs — a textbook justified use of Strategy, not a "pattern showcase" (contrast with, e.g., not introducing a State pattern *class hierarchy* for §6, where a simple validated transition-table method was judged sufficient — see §6's implementation note). **Tradeoffs:** one small interface + one implementation, negligible added complexity. **Consequences:** a future ML strategy is additive, not a refactor.

### ADR-07: RBAC extended with new roles, not replaced with attribute-based access control
**Context:** multi-level approval needs `MANAGER`/`COMPLIANCE`/`OPERATIONS` distinctions the current three roles don't have. **Problem:** is this the point where RBAC stops being sufficient and ABAC (attribute-based rules, e.g. "approve if amount < user.approvalLimit") becomes necessary? **Options:** (a) add roles, keep `@PreAuthorize("hasRole(...)")` exactly as today; (b) introduce a full ABAC policy engine. **Decision:** (a). **Rationale:** every approval-routing requirement in this document (§8) is satisfiable by role + a **data-driven policy table** (`approval_policies`/`approval_steps`) determining which role is needed for which transaction — this is RBAC with configurable *routing*, not a need for per-user attribute rules. The one case that *would* need ABAC (a per-approver personal amount cap) was explicitly named and explicitly deferred in §8, not silently forced into the RBAC model. **Tradeoffs:** if per-approver caps are ever actually required, this decision would need revisiting. **Consequences:** the existing, well-tested `@PreAuthorize` + `RbacMatrixIT` pattern extends directly to every new endpoint with no new authorization framework.

### ADR-08: Materialized balance, not calculated-on-read balance
Covered in full as part of ADR-01; listed separately here only because the prompt named it as its own required ADR. Same context, decision, and rationale as ADR-01 — the "materialized vs. calculated" question and the "ledger vs. mutable balance" question are, in this design, the same decision viewed from two angles, not two separate ones.

---

# 20. Implementation Roadmap

Every phase below leaves the application **fully working** at its end — exactly the constraint the existing project's own phase-by-phase build already followed successfully across its original nine phases, continued here rather than abandoned.

**Phase A — Ledger foundation (schema + backfill only, §5 Stages 1-2).** Ship `V8`/`V9`. Zero behavior change. Fully reversible by simply never wiring anything to read/write these tables yet.

**Phase B — Ledger dual-write (§5 Stage 3).** `ApprovalService` posts ledger entries alongside its existing debit/credit calls. Existing behavior (from the customer's and employee's perspective) is **unchanged** — this phase is purely an internal-consistency addition, verifiable via a first, minimal version of the reconciliation job run manually/on-demand rather than scheduled yet.

**Phase C — Risk engine (§7), synchronous, LOW-risk-only initially.** Ship `risk` schema + `RuleBasedRiskStrategy`, wired into `TransferService.createTransfer()`, but seeded with permissive default policies (effectively a no-op initially) so existing transfer behavior doesn't change until real limits are deliberately configured by an admin.

**Phase D — Formal state machine (§6).** Replace the flat status enum with the full lifecycle, **including** a compatibility mapping so existing frontend code reading `status` still gets sensible values (`SUBMITTED` where it used to see `PENDING`, etc. — a one-line TypeScript type update on the frontend, as noted in §15).

**Phase E — Multi-level approvals (§8).** Ship `approvals` schema, seed the new roles, migrate the existing single-approval flow to be policy-driven (with a default "Low value, one approval" policy that reproduces today's exact behavior for every existing transaction size until new policies are deliberately configured) — **existing approval behavior is unchanged on day one of this phase**, only becoming richer as an admin actually configures higher-tier policies.

**Phase F — Outbox + relay (§11), starting with notifications only.** Move `NotificationEventListener`'s consumption to the outbox path; `AuditEventListener` stays synchronous (per the ADR-02 consistency split). This phase directly *removes* the existing email try/catch's reason for existing (the coupling problem it works around no longer exists), a concrete, demonstrable improvement.

**Phase G — Reconciliation, scheduled (§9) + Exception Console (§10).** Turn the manual reconciliation job from Phase B into a real `@Scheduled` job; ship `opsexceptions` and wire reconciliation mismatches into it.

**Phase H — Security hardening (§12).** Rate limiting, account lockout automation, refresh-token family tracking, session management, MFA — each independently shippable and independently toggleable, in the order listed (rate limiting first, as the highest-value/lowest-effort item, exactly as this document's own §17 alerting discussion already implies).

**Phase I — Operations UI (§13).** Built last, deliberately — every page in this phase is a *view* onto data that phases A–H already made real; building the UI first against not-yet-real data would risk exactly the kind of decorative, unjustified-feature problem this entire document was asked to avoid.

**Phase J — Testing/observability catch-up (§16/§17), continuous throughout, formalized here.** Exactly like the existing project's own Phase 8, a dedicated pass to close any test/metrics gaps that accumulated during Phases A–I, rather than assuming each phase's own tests were sufficient in isolation.

---

# Final Assessment

## 1. Project strengths (of this design specifically)
Every new capability is traceable to a concrete, already-observed problem in the existing system (the email try/catch motivating the outbox; the missing balance history motivating the ledger; the flat approval model motivating policy-driven routing) rather than added because it "sounds enterprise" — the one constraint the prompt weighted most heavily. The migration strategy for the single riskiest change (the ledger) is staged into five independently-reversible steps rather than a cutover. Every module extension preserves the existing port-interface and schema-per-module rules without exception.

## 2. Weaknesses (honestly, of this design)
Several real gaps are named and explicitly deferred rather than solved: no retry-on-optimistic-conflict for the posting path (ADR-05), no database-enforced (only convention-enforced) immutability for ledger/audit tables, no per-approver personal limit model, at-least-once (not exactly-once) event delivery. These are honest scope boundaries, but a reviewer should not mistake "named and deferred" for "solved."

## 3. Enterprise-grade improvements beyond this document
Database-level immutability grants/triggers on financial tables; a formal saga/compensation framework if any module is ever genuinely extracted; a real ABAC layer if per-approver limits are ever required; a proper key-management service for the RSA signing key (currently a locally-generated file) if this ever handles real money.

## 4. Features suitable for fintech products specifically
Everything in §7-§10 (risk, approvals, reconciliation, exception management) *is* the fintech-specific layer, by design — this whole document is that answer.

## 5. Scaling strategy for 1 million users, given everything in this document
The same strategy the existing handover document already lays out (read replicas, pagination everywhere, partitioning `transactions.transactions` and now also `ledger.journal_entry_lines` by date), plus one new, concrete addition: at real scale, the outbox relay and reconciliation job (§18) are the first things that need to move off the shared web-serving process, since they're the first components whose correctness depends on running as exactly one instance regardless of how many web replicas exist.

## 6. Features expected in a real digital bank, still missing after this document
Real card issuance/management, external payment-rail integration (ACH/SWIFT/RTP), a true KYC/AML pipeline (this document's `risk` module is transaction-risk scoring, not identity/AML risk), interest accrual, statement generation, and — still, as the existing handover document already concluded — a real backup and disaster-recovery strategy for the underlying data, which remains the single most important gap standing between this system and something trusted with actual money.
