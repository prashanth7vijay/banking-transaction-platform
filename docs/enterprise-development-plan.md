# Development Plan — Enterprise Operations Evolution

Companion to `enterprise-evolution-architecture.md`. That document is the *design*; this is the *build order* — turning §20's ten conceptual phases into concrete, file-level scope, following the same discipline the original nine-phase build used.

## Workflow rules (carried forward, unchanged)

1. **Backend + frontend together, every phase that has a frontend-visible piece.** Several early phases here (A, B, C-partial, D) are deliberately backend-only, since they add internal consistency (a ledger, a risk check) with no new UI surface yet — that's not a rule violation, it's what those phases actually are. Frontend catches up fully in Phase 9, once there's real data behind it worth building a UI for.
2. **Every phase leaves `docker compose up` fully working**, with existing behavior unchanged unless the phase explicitly says otherwise. Most early phases are additive-and-silent by design (§20 already establishes this for Phases A, C, E).
3. **One phase per response**, split only if approaching a context limit, with an explicit "resume here" marker if that happens.
4. **No placeholders** — every migration, entity, service, and endpoint shipped in a phase is complete and functional, not a stub for a later phase to fill in.
5. **Testing stays proportional, not exhaustive, during build-out** — unit tests for the genuinely risky new logic (ledger balancing, state-machine legality, self-approval guards) as each phase ships them, matching how the original project's Phases 0–7 worked; the dedicated catch-up pass is Phase 10, matching the original project's own Phase 8.
6. **Every response states plainly which existing behavior, if any, changes** — most phases change none; the ones that do (D, E, F) say so explicitly up front, not buried in the diff.

---

## Phase 1 — Ledger Foundation *(architecture doc: Phase A)*

**Backend only. Zero behavior change.**

- New module `com.platform.ledger`: `domain` (`LedgerAccount`, `JournalEntry`, `JournalEntryLine`), `repository`, `service` (`LedgerService` — empty of callers this phase, just the posting method and its debit=credit validation), `event` (`LedgerEntryPostedEvent`, unpublished until Phase 2).
- Migration `V8__ledger_schema.sql` — the three tables from the architecture doc §5, indexes included.
- Migration `V9__ledger_backfill.sql` (or an admin-triggered one-off job, per §5 Stage 2) — one opening-balance journal entry per existing seeded account.
- Unit tests: `LedgerServiceTest` (rejects unbalanced entries, accepts balanced ones).

**Exit criteria:** `docker compose up`, existing app behaves identically in every visible way; a direct query against `ledger.journal_entries` shows one balanced opening entry per existing account.

---

## Phase 2 — Ledger Dual-Write *(architecture doc: Phase B)*

**Backend only. Zero customer/employee-visible behavior change — this is where the ledger starts actually being written to on every transfer.**

- `ApprovalService.approve()` extended: after the existing debit/credit calls, posts one balanced `JournalEntry` via `LedgerService`, in the same `@Transactional` method — same pattern as how the existing Micrometer counter increment already sits alongside the debit/credit calls.
- `AccountLookupPort` gains no new methods — the ledger posts against `ledger_accounts`, addressed by the same `accountId` the port already returns.
- A first, minimal, **manually-triggered** reconciliation check (not yet the scheduled job from Phase 7) — a small admin-only endpoint or a one-off script comparing `ledger_accounts.ledger_balance` to `accounts.accounts.balance`, used to validate this phase during a soak period per §5 Stage 4.
- Integration test: `LedgerPostingIT` (Testcontainers) — a real approval, asserting both balances end up numerically identical.

**Exit criteria:** every new transfer approval produces a real, balanced ledger entry; the manual reconciliation check shows zero mismatches after a batch of test transfers.

---

## Phase 3 — Risk Engine *(architecture doc: Phase C)*

**Backend only. Behavior change: transfers now pass through a risk check — shipped with permissive defaults, so nothing is actually blocked yet.**

- New module `com.platform.risk`: `domain` (`LimitPolicy`, `RiskAssessment`), `repository`, `service` (`RiskStrategy` interface + `RuleBasedRiskStrategy`, `RiskAssessmentService`).
- Migration `V10__risk_schema.sql`.
- `TransferService.createTransfer()` calls `RiskAssessmentService.assess()` before saving the transaction; a `blocked` result short-circuits to `FAILED` (architecture §7's flow), otherwise proceeds exactly as today.
- Seed data: a handful of deliberately generous default `LimitPolicy` rows (high per-transaction and daily caps) so no existing demo flow starts failing.
- Admin-only CRUD endpoints for `LimitPolicy` (`/api/v1/admin/risk/policies`) — backend only this phase; the admin UI for editing them comes in Phase 9.
- Unit tests: `RuleBasedRiskStrategyTest` (each limit type in isolation, combined-rules severity resolution).

**Exit criteria:** every transfer now has a `risk_assessments` row; nothing that worked before stops working, since the seeded policies are permissive by design.

---

## Phase 4 — Transaction State Machine *(architecture doc: Phase D)*

**Backend + a small, necessary frontend touch-up. Behavior change: transaction status values change shape.**

- `TransactionStatus` enum expands to the full lifecycle (§6): `DRAFT, SUBMITTED, PENDING_APPROVAL, APPROVED, PROCESSING, COMPLETED, REJECTED, FAILED, CANCELLED`.
- New `TransactionStateMachine` service (§6's implementation note — a validated transition map, not a new framework dependency) owning the one `transition(...)` method every status change now goes through.
- `TransferService.createTransfer()` now creates `SUBMITTED` (not `PENDING`); the risk check from Phase 3 transitions to `PENDING_APPROVAL` or `FAILED`.
- New `TransactionStateChangedEvent`, published on every transition, additive alongside the existing `TransactionStatusChangedEvent` (existing listeners untouched).
- New endpoint `GET /api/v1/transactions/{id}/timeline` (state-transition history), backing the Transaction 360 timeline built in Phase 9.
- **Frontend:** the `TransactionStatus` TypeScript union type (`features/transactions/types`) and the `StatusBadge` component's color map both extend to the new values — a small, contained change, not a redesign, so existing pages keep working with correct status colors immediately.
- Unit tests: `TransactionStateMachineTest`, exhaustively parameterized over the full `(from, to)` cross-product from §6's transition table.

**Exit criteria:** a transfer visibly moves through `SUBMITTED → PENDING_APPROVAL → APPROVED → PROCESSING → COMPLETED` (or a rejection/failure path) in the database and in the existing history/queue pages' status badges; every illegal transition is provably rejected by the new test suite.

---

## Phase 5 — Multi-Level Approvals *(architecture doc: Phase E)*

**Backend only this phase (the Approval Queue UI upgrade is Phase 9). Behavior change: approval now goes through policy resolution — shipped with a default policy that exactly reproduces today's single-approval behavior.**

- New module `com.platform.approvals`: `domain` (`ApprovalPolicy`, `ApprovalStep`, `ApprovalAssignment`, `ApprovalDecision`), `repository`, `service` (`ApprovalPolicyResolver`, `ApprovalDecisionService`).
- Migration `V11__approvals_schema.sql`, including seeding `MANAGER`, `COMPLIANCE`, `OPERATIONS` as new rows in the **existing** `users.roles` table.
- One default seeded policy: "Low value, any risk" → one `EMPLOYEE` step — reproduces today's exact approval behavior for every existing transaction until an admin deliberately configures a higher tier.
- `POST /transactions/{id}/approve`/`/reject` internally now route through `ApprovalDecisionService`, externally unchanged for a single-step policy.
- New endpoints: `GET /api/v1/approvals/my-queue`, `POST /api/v1/approvals/{assignmentId}/decide`, admin CRUD on policies.
- Separation-of-duties guard generalized from the existing `ApprovalServiceTest.approveRejectsSelfApproval` test to cover every step in a multi-step workflow.
- Unit + integration tests: `ApprovalPolicyResolverTest`, `ApprovalWorkflowIT` (a real two-step approval end to end).

**Exit criteria:** existing single-employee approval flow works identically from the outside; a manually-configured two-step policy (test-only, via the new CRUD endpoints) proves the multi-step path end to end.

---

## Phase 6 — Outbox + Relay (Notifications) *(architecture doc: Phase F)*

**Backend only. Behavior change: notification delivery becomes decoupled from the transfer transaction — the existing email try/catch's reason for existing goes away.**

- `transactions.outbox_events` table (`V14__outbox_tables.sql`, notifications' producer side first — other modules' outbox tables can follow later, not required this phase).
- New module `com.platform.eventing`: the `@Scheduled` polling relay (`SELECT ... FOR UPDATE SKIP LOCKED`, retry/backoff, dead-letter after max attempts).
- `NotificationEventListener`'s consumption moves behind the outbox — `TransactionStateChangedEvent`s destined for notification now write an outbox row instead of triggering `NotificationEventListener` synchronously; the relay re-publishes them as the same existing Spring event type, so `NotificationEventListener` itself **does not change at all**.
- `AuditEventListener` stays exactly as it is today — synchronous, same transaction (ADR-02's consistency split).
- New metric: `platform.outbox.queue_depth`, `platform.outbox.relay_lag_seconds`.
- Integration tests: `OutboxRelayIT` (delivery, `PROCESSED` idempotency, concurrent-poller no-double-delivery via `SKIP LOCKED`), a dead-letter test.

**Exit criteria:** a transfer approval still produces the same notification and email as before, just with a few seconds of relay latency; simulating a MailHog outage no longer has any special-cased try/catch doing the work — the outbox's retry/dead-letter path handles it generically, and a repeated failure produces an `OperationalException` once Phase 7 ships (until then, it dead-letters silently — acceptable, since Phase 7 is next).

---

## Phase 7 — Reconciliation + Exception Console (backend) *(architecture doc: Phase G)*

**Backend only (Exception Console UI is Phase 9). No behavior change to existing flows — this phase adds verification and tracking around what already exists.**

- New module `com.platform.reconciliation`: `ReconciliationJob` (`@Scheduled`, replacing Phase 2's manual check), `ReconciliationRun`/`ReconciliationResult` entities.
- Migration `V12__reconciliation_schema.sql`.
- New module `com.platform.opsexceptions`: `OperationalException` entity, a listener consuming `ReconciliationMismatchDetectedEvent` and the outbox relay's dead-letter event from Phase 6.
- Migration `V13__opsexceptions_schema.sql`.
- Admin-only endpoints: `GET /admin/reconciliation/runs(+/results)`, `GET/PATCH /ops/exceptions`.
- Advisory-lock guard against overlapping scheduled reconciliation runs (§16's concurrency table).
- Unit tests: `ReconciliationJobTest`, `OperationalExceptionServiceTest`.

**Exit criteria:** the scheduled job runs automatically and shows zero mismatches against the now-real ledger data from Phases 1–2; deliberately corrupting one test account's balance via a direct SQL update produces a tracked, severity-scored `OperationalException` within one scheduled cycle.

---

## Phase 8 — Security Hardening *(architecture doc: Phase H)*

**Backend + minimal frontend (a "My Devices" panel on the profile page). Behavior change: login gets meaningfully stricter — worth calling out plainly.**

- Rate limiting on `/auth/login` and `/auth/register` (Bucket4j + the existing Redis instance — no new infra).
- Automated account lockout: `UserStatus.LOCKED` (already defined, currently dead) actually gets set after N consecutive login failures, with an admin unlock path via the existing `AdminUserController` status endpoint (no new endpoint needed there).
- `auth.refresh_tokens` gains `family_id`; rotation logic detects reuse-after-rotation and revokes the whole family.
- New `auth.sessions` table + `GET/DELETE /api/v1/auth/sessions`.
- MFA: optional TOTP enrollment + the `MFA_REQUIRED` login branch (§12) — additive, invisible to any account that doesn't enable it.
- **Frontend:** a small "Security" section on the existing `ProfilePage` — list active sessions with revoke buttons, MFA enrollment toggle. No new route, an extension of an existing page.
- Security/unit tests: reuse-detection test, lockout-threshold test.

**Exit criteria:** existing login flow for an account that hasn't enabled MFA is unchanged; repeated bad-password attempts visibly lock the account; a revoked session's refresh token is rejected on next use.

---

## Phase 9 — Operations UI *(architecture doc: Phase I)*

**Frontend-heavy, backend only where a view needs a small new read endpoint not already covered. This is where every prior phase's data becomes visible.**

- New shared components: `Drawer`, `Timeline`, generalized `Badge` (subsumes `StatusBadge`), `DataTable` wrapper — per §13, the complete and deliberately short list of new primitives.
- `OperationsDashboardPage` (`features/operations`) — pending approvals, high-risk count, open exceptions, reconciliation health, SLA breaches, each linking to its filtered detail view.
- Approval Queue upgrade (extends the existing page, doesn't replace it) — filter bar, risk/SLA badges, the new drawer-based preview.
- `TransactionDetailPage` ("Transaction 360") — full detail, timeline (Phase 4's endpoint), risk assessment, approval history, ledger entries, correlation ID with copy-to-clipboard. Independent per-section `useQuery` calls per §13's partial-failure requirement.
- `CustomerDetailPage` ("Customer 360").
- Audit Explorer upgrade — date-range, correlation-ID, transaction-ID, severity filters added to the existing page.
- `ExceptionConsolePage` (`features/opsexceptions`).
- `SystemHealthPage` — thin, links out to Grafana, per §13's explicit "don't duplicate Grafana" decision.
- Admin config screens for `LimitPolicy` and `ApprovalPolicy` (simple CRUD forms, following the existing `UserManagementPage` pattern).
- Nav updates for the new roles (`MANAGER`, `COMPLIANCE`, `OPERATIONS`) added in Phase 5.

**Exit criteria:** every backend capability shipped in Phases 1–8 has a real, usable page — nothing built in this phase is decorative or ahead of real data, per §20's explicit ordering rationale.

---

## Phase 10 — Testing & Observability Catch-Up *(architecture doc: Phase J)*

**Backend + frontend. No new features — closing gaps that accumulated across Phases 1–9, exactly like the original project's own Phase 8.**

- The full concurrency test suite from §16's table (including the two genuinely new scenarios: concurrent outbox delivery, concurrent reconciliation runs).
- `SecurityIT` extended to cover the new roles/endpoints in the existing `RbacMatrixIT` pattern.
- Frontend component tests for the new primitives (`Drawer`, `Timeline`, `Badge`) via the already-installed, still-underused Vitest + RTL.
- First Playwright E2E scenario: submit → risk-assess → approve → ledger post → notification → audit, browser-driven, per §16.
- New Grafana panels/dashboard for the operations metrics from §17; alerting rules for outbox queue depth, SLA breaches, critical reconciliation mismatches.
- README status update, following the same phase-log convention the original project's README already uses.

**Exit criteria:** the same bar the original project's Phase 8 set — every deferred test/observability item named in the architecture document is either closed or explicitly re-flagged as a conscious, still-open decision (per ADR-05's optimistic-locking-retry gap, which this phase does not silently close, only re-confirms as intentionally deferred).

---

## Suggested working rhythm
Identical to the original build: one phase per response, backend and frontend together wherever a phase has both, `docker compose up` never left broken, and a plain statement at the top of each response about whether existing behavior changes (most phases here don't; Phases 4, 5, 6, and 8 do, and each says so explicitly above).

**Next step:** say the word and Phase 1 (Ledger Foundation) starts — pure schema and backfill, zero visible change, the safest possible first move on the riskiest part of this whole plan.
