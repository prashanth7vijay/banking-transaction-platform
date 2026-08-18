# Development Plan — Enterprise Transaction Platform

## Workflow Rules (confirmed)

1. **Backend + frontend together, every phase.** Each phase below delivers both the API/service/entity work *and* the corresponding React pages, components, routing, and API integration in the same pass — never backend-complete-then-frontend-later.
2. **Every phase is independently runnable.** After any phase, `docker compose up` gives you a working system you can click through in the browser for everything completed so far — nothing is "backend done, UI pending."
3. **One phase per response**, unless a response would run up against the context limit — in which case the phase is split, not rushed or truncated.
4. **Hard stop near context limit.** If a response is approaching ~95% of available context, I stop generating immediately and tell you exactly which files are finished, which are still needed, and where to resume — never a half-written file.
5. **No placeholders.** Every file compiles, every feature is real, tests are included where applicable — no TODOs, no mocked-out logic to "fill in later."
6. **Project integrity every response.** Build passes, tests for the completed phase pass, `docker compose up` still works, after every single response.

Build order is chosen so that every phase produces something that **compiles, runs, and is tested — backend and frontend both** — no phase depends on code that doesn't exist yet further down the list.

---

## Phase 0 — Project Skeleton & Infra Baseline
**Goal:** empty-but-real full stack boots end to end before a single feature exists.

Backend:
- Spring Boot scaffold (`shared` package only), `GlobalExceptionHandler`, `CorrelationIdFilter`, structured JSON logging, profiles (`application.yml` / `-docker` / `-dev`)
- Flyway wired with an empty baseline migration
- RSA key pair generation script (`scripts/generate-keys.sh`), `.gitignore`'d

Frontend:
- Vite + React 19 + TypeScript scaffold, Tailwind + shadcn/ui installed and themed (dark/light toggle wired, even with nothing to show yet), base layout shell, router skeleton, Axios instance pointed at the backend

Infra:
- `docker-compose.yml` + override: Postgres, Redis, backend, frontend/nginx, MailHog
- GitHub Actions: build + test stages for both backend and frontend (real pipeline, minimal tests)

**Exit criteria:** `docker compose up` serves the React shell at the nginx URL, backend `/actuator/health` is green, CI passes.

---

## Phase 1 — `users` Module + `auth` Module (combined — auth needs users to mean anything)
**Goal:** the first real, demoable feature: an account you can register, log into, and see yourself as.

Backend:
- `User`, `Role` entities + Flyway migration; repository/service/mapper for `users`
- RS256 `JwtService`, `JwtFilter`, `SecurityConfig`, refresh-token rotation (Redis denylist for logout)
- Endpoints: register, login, refresh, logout, forgot-password, reset-password, `GET/PATCH /users/me`, change-password

Frontend:
- Register/login/forgot-password pages, auth context + protected route wrapper, Axios interceptor (attach token, silent refresh on 401)
- Profile page (view/edit, change password)
- Role-based nav shell (routes exist for Customer/Employee/Admin even if most are empty stubs *behind* the guard — the guard itself must be real, not a placeholder)

**Exit criteria:** From the actual browser: register → land on a real profile page → log out → log back in → refresh survives a page reload. Security tests (expired/tampered token, wrong-role 403) pass.

---

## Phase 2 — `accounts` Module
**Goal:** customers have real data to look at.

Backend:
- `Account`, `Beneficiary` entities (optimistic locking), Flyway migration, service/repository/mapper
- Endpoints: list my accounts, account detail, beneficiary CRUD; ownership enforced in service layer

Frontend:
- Customer dashboard (account cards, balances), account detail view, beneficiary management (list/add/remove) with React Hook Form + Zod

**Exit criteria:** Logged-in customer sees seeded accounts and manages beneficiaries entirely through the UI. Optimistic-lock repository test passes.

---

## Phase 3 — `transactions` Module
**Goal:** the core banking workflow, fully clickable — the phase most worth demoing.

Backend:
- `Transaction` entity + status state machine, `TransferService` (idempotency key, balance validation via `accounts` port interface), `ApprovalService` (maker-checker: initiator ≠ approver, tested)
- Endpoints: create transfer, get/list/filter transactions, approve, reject
- Domain events published (consumed by later phases)

Frontend:
- Transfer form (customer), paginated/filterable transaction history, employee approval queue with approve/reject actions
- Real-time-ish status reflected in UI (TanStack Query invalidation on mutation)

**Exit criteria:** A customer submits a transfer in the browser, an employee (different account) approves/rejects it in the browser, balances update, and the maker-checker rule is enforced and tested.

---

## Phase 4 — `audit` Module
**Goal:** every meaningful action is durably recorded and visible to Admins.

Backend:
- `AuditLog` entity (append-only), listeners on auth/user/transaction events, filterable admin query endpoint

Frontend:
- Admin audit dashboard (table, filters by actor/action/date)

**Exit criteria:** Logging in, changing a password, and approving a transaction each produce a correctly attributed, queryable log row — visible in the Admin UI, not just asserted in a test.

---

## Phase 5 — `notifications` Module
**Goal:** MailHog + DB notifications, closing the loop on earlier events.

Backend:
- `Notification` entity, listeners on the same domain events audit consumes, Spring Mail → MailHog integration
- Endpoints: list my notifications, mark as read

Frontend:
- Notification bell/dropdown, toast on relevant mutation outcomes

**Exit criteria:** Registering, resetting a password, and having a transfer approved/rejected each produce a visible MailHog email *and* an in-app notification, viewable in the browser.

---

## Phase 6 — Admin Capabilities & Full RBAC/UX Polish
**Goal:** the last role (Admin) fully built out, and every route held to the UX bar from the brief.

Backend:
- Admin endpoints: user management (list/search/disable), role assignment, system metrics summary
- RBAC matrix test: every endpoint checked against every role

Frontend:
- Admin user/role management screens, role-based nav finalized across all three roles, skeleton loaders + error boundaries audited on every route (not just the earliest ones), dark/light mode consistency pass

**Exit criteria:** All three roles have a complete, dead-end-free UI, backed by a fully role-tested API.

---

## Phase 7 — Observability Stack
**Goal:** turn on Prometheus/Grafana/ELK now that there's real, clickable traffic to generate data.

- Micrometer custom metrics wired into `transactions`/`auth`
- Prometheus scrape config, Grafana provisioned dashboards (infra + business metrics)
- ELK: Logstash appender, Kibana index pattern + saved search tracing one request by correlation ID
- Small synthetic traffic script so dashboards have real (not empty) data for screenshots

**Exit criteria:** Grafana + Kibana show real data generated by using the actual frontend, screenshot-ready.

---

## Phase 8 — Hardening Pass
**Goal:** the "would a Barclays senior engineer approve this PR" bar.

- Security review: CORS, security headers (CSP/HSTS/X-Frame-Options), CI dependency vulnerability scan
- Test coverage review on service/security layers (meaningful, not vanity percentage)
- Performance sanity: HikariCP pool sizing, N+1 check on transaction history (pagination/fetch joins)
- README: architecture diagram, one-command setup, screenshots, design-decisions summary

**Exit criteria:** Repository is resume/interview-link-ready with no caveats.

---

## Suggested Working Rhythm

- One phase at a time, backend and frontend together, fully tested, before moving on.
- Each phase leaves `docker compose up` strictly more complete and still fully working.
- I'll flag clearly if a phase needs to split across responses, and exactly where the next response should resume.

**Next step:** say the word and I'll start Phase 0 (project skeleton + infra baseline — backend and frontend both).

