# Customer 360 Performance Case Study

## Environment disclosure (read this first)

This optimization was implemented and code-reviewed in a sandboxed environment
with **no Docker, no Maven Central access, and no running Postgres instance**.
That means:

- The code changes below were written and manually traced against the actual
  schema/entities/repositories, but **never compiled or executed**.
- No benchmark numbers in this repository are real measurements. The
  benchmark harness (`Customer360DatasetSeeder`, `Customer360BenchmarkRunner`,
  `performance/generate_report.py`, `performance/load-test.js`) is fully
  implemented and ready to run, but `performance/baseline.json` and
  `performance/optimized.json` do not exist yet - **you need to generate them
  locally** using the steps under "How to reproduce" below.
- The correctness unit tests (`Customer360ServiceTest`) mock all ports and
  don't need a database - you can run those with plain `mvn test` once you
  have Maven/JDK locally.
- The query-count regression test (`Customer360QueryCountIT`) and the
  benchmark itself need a real Postgres (Testcontainers or `docker compose`),
  which this environment doesn't have.

Nothing in this document should be read as "measured" unless it explicitly
says it came from a file you generated. Anywhere a number would normally go,
this document says `<measure locally>` instead of guessing.

## 1. Original request flow (before)

```
GET /api/v1/employee/customers/{id}/360
  │
  ├─ userLookupPort.getById(customerId)                     [1 query]
  ├─ accountLookupPort.findByOwnerUserId(customerId)         [1 query]
  ├─ transactionLookupPort.listForCustomerSince(customerId)  [1 query, loads ENTIRE 90-day window]
  │
  ├─ for each transaction in that window:
  │      riskAssessmentPort.getAssessment(transaction.id)    [N queries]
  │
  ├─ exceptionLookupPort.listForTransactionIds(allIds)       [1 query]
  │
  └─ for each open exception:
         userLookupPort.getById(exception.assignedToUserId)  [M queries]

Total: 4 + N + M queries, where N = transactions in the 90-day window,
M = open exceptions with an assignee. Unbounded in both N and M.
```

Everything downstream of the transaction load - the recent-15 list, the
30-day/90-day totals, the average amount, pending/failed counts, and the
risk profile - was computed by streaming over the *entire* loaded window in
Java, even though the response only ever displays 15 transactions.

## 2. Optimized request flow (after)

```
GET /api/v1/employee/customers/{id}/360
  │
  ├─ userLookupPort.getById(customerId)                                    [1 query]
  ├─ accountLookupPort.findByOwnerUserId(customerId)                       [1 query]
  ├─ transactionLookupPort.findRecentForCustomer(customerId, limit=20)     [1 query, DB-side LIMIT]
  ├─ transactionLookupPort.getActivityAggregate(customerId, since90/30)    [1 query, COUNT/SUM/CASE in Postgres]
  ├─ transactionLookupPort.findTransactionIdsForCustomerSince(customerId)  [1 query, id-only projection]
  ├─ riskAssessmentPort.getAssessments(windowTransactionIds)               [1 batched IN-clause query]
  ├─ exceptionLookupPort.listForTransactionIds(windowTransactionIds)       [1 batched IN-clause query]
  └─ userLookupPort.getFirstNamesByIds(assigneeIds)                       [1 batched IN-clause query, 0 if no assignees]

Total: 7-8 queries, CONSTANT regardless of how much history the customer has.
```

Query count is now a fixed number of round trips, not a function of
transaction/exception volume. See `Customer360Service`'s class-level javadoc
and each method's javadoc for the full reasoning, including the proof that
fetching only the top 20 transactions (instead of the whole window) is
provably equivalent for both the "recent transactions" list and the merged
timeline.

## 3. What changed, file by file

| File | Change |
|---|---|
| `TransactionRepository` | Added an id-only projection query, a `Pageable` overload for DB-side limiting, and one CASE/SUM aggregate query. Removed nothing. |
| `TransactionLookupPort` / `TransactionLookupPortImpl` | Replaced `listForCustomerSince` (loaded everything) with `findRecentForCustomer`, `findTransactionIdsForCustomerSince`, `getActivityAggregate`. |
| `TransactionActivityAggregate` (new) | DTO carrying the DB-computed counts/sums. |
| `RiskAssessmentRepository` | Added `findByTransactionIdIn` (batch). |
| `RiskAssessmentPort` / `RiskAssessmentService` | Added `getAssessments(Collection<UUID>)` (batch); `getAssessment(UUID)` unchanged for existing callers (Transaction 360 etc.). |
| `UserRepository` | Added `findFirstNamesByIdIn` - a narrow id+firstName projection, avoiding the EAGER `roles` collection entirely for this use case. |
| `UserLookupPort` / `UserLookupPortImpl` | Added `getFirstNamesByIds(Collection<UUID>)` (batch); `getById` unchanged. |
| `Customer360Service` | Rewritten to use the batched/limited/aggregated calls above. Response shape (`Customer360Response` and all nested DTOs) is **unchanged** - API contract preserved. |
| `V14__customer360_performance_indexes.sql` (new) | Composite index, see below. |
| `Customer360ServiceTest` (new) | Mockito unit tests for correctness (no DB needed). |
| `Customer360QueryCountIT` (new) | Testcontainers regression test asserting query count stays low. |
| `Customer360DatasetSeeder`, `Customer360BenchmarkRunner`, `DatasetSize` (new, `benchmark` profile only) | Reproducible synthetic data + measurement harness. |
| `performance/generate_report.py`, `performance/load-test.js` (new) | Report generation and HTTP-level load test. |

**Not touched:** authentication, JWT, ledger, payment/transfer logic, approval
workflows, risk *rules* (only risk *lookups*), exception workflows, any other
controller, any other repository. `CustomerController`'s endpoint signature
and `Customer360Response`'s shape are unchanged - existing frontend code and
any other consumer keep working without modification.

## 4. Indexing

**Query pattern needing support:** every new/changed Customer 360 query
filters on `initiated_by_user_id = ?` and (except the id-projection's
WHERE-only use) `created_at >= ?`, ordered `created_at DESC`.

**Index added:**
```sql
CREATE INDEX idx_transactions_customer_created_at
    ON transactions.transactions (initiated_by_user_id, created_at DESC);
```

**Why this column order:** `initiated_by_user_id` leads because every query
uses it as an equality predicate (highest selectivity, narrows to one
customer first); `created_at DESC` second because it's both the range
predicate (`>= since`) and the exact `ORDER BY` direction every query uses -
Postgres can walk this index in order and stop at the `LIMIT` without a
separate sort step.

**Why the old index was dropped:** `idx_transactions_initiated_by` (single
column) becomes redundant once the composite index exists - any plan that
could use the old index for a bare equality lookup can use the new one too,
since `initiated_by_user_id` is still its leading column. The only other
query filtering on that column (`findByIdempotencyKeyAndInitiatedByUserId`,
used for idempotency checks) leads with `idempotency_key`, which already has
its own unique index and is far more selective - it never actually depended
on the index being dropped. Keeping both indexes would mean paying their
insert-time maintenance cost twice for zero additional read benefit.

**Write-performance trade-off:** one B-tree index over two `UUID`+`timestamptz`
columns instead of one column - marginally larger per-row insert cost and
storage than the single-column index, but since it replaces (not adds to) the
old index, the net index count on this table is unchanged (still 3: this one,
`idx_transactions_status`, plus the primary key and the idempotency-key unique
constraint).

**Expected EXPLAIN ANALYZE change (to verify locally, not measured here):**
before this index, `WHERE initiated_by_user_id = ? AND created_at >= ? ORDER
BY created_at DESC LIMIT 20` against a large `transactions` table would
either use the old single-column index and then sort the matching rows
in-memory, or fall back to a sequential scan if the customer's row count is a
large fraction of the table - both get worse as the table grows. With the
composite index, the expected plan is an `Index Scan Backward` (or forward,
depending on how Postgres orients the DESC index) directly satisfying the
predicate and the ordering, stopping after 20 rows without touching unrelated
rows. Run `EXPLAIN ANALYZE` on the query yourself after seeding data (see
below) to confirm this on your own Postgres/version/statistics - do not take
this paragraph as a substitute for that.

## 5. How to reproduce

All commands assume you're in the `transaction-platform/` directory with
Docker and a JDK 21 + Maven available (this sandbox has neither Docker nor
Maven Central access, which is why these haven't been run yet).

```bash
# 1. Start the database (and the rest of the stack, optional for just benchmarking)
./scripts/generate-keys.sh
docker compose up -d postgres

# 2. Run migrations (Flyway runs automatically on app startup, or standalone:)
cd backend
mvn -q flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/platform \
  -Dflyway.user=platform -Dflyway.password=platform

# 3. Run the existing + new unit tests (no DB needed for Customer360ServiceTest)
mvn test -Dtest=Customer360ServiceTest

# 4. Run the query-count regression test (needs Docker, pulls postgres:16-alpine itself)
mvn test -Dtest=Customer360QueryCountIT

# 5. BASELINE measurement - checkout/apply the code as it was BEFORE this
#    optimization (e.g. `git stash` this patch, or check out the commit prior
#    to it), then seed + measure against it:
mvn spring-boot:run -Dspring-boot.run.profiles=benchmark -Dspring-boot.run.arguments=\
"--spring.datasource.url=jdbc:postgresql://localhost:5432/platform \
 --spring.datasource.username=platform --spring.datasource.password=platform \
 --benchmark.seed-data=true --benchmark.dataset-size=MEDIUM --benchmark.seed=42 \
 --benchmark.label=baseline --benchmark.output-file=../performance/baseline.json"
# Note the "Benchmark target customer id" the seeder logs - you'll reuse it below.

# 6. OPTIMIZED measurement - re-apply/checkout this optimization, rebuild, and
#    measure against the SAME already-seeded data (benchmark.seed-data=false):
mvn spring-boot:run -Dspring-boot.run.profiles=benchmark -Dspring-boot.run.arguments=\
"--spring.datasource.url=jdbc:postgresql://localhost:5432/platform \
 --spring.datasource.username=platform --spring.datasource.password=platform \
 --benchmark.seed-data=false --benchmark.target-customer-id=<id from step 5> \
 --benchmark.label=optimized --benchmark.output-file=../performance/optimized.json"

# 7. Generate the report
cd ..
python3 performance/generate_report.py

# 8. Optional: HTTP-level load test against the running app
docker compose up -d --build backend
# log in as the seeded EMPLOYEE user (or the dev-profile demo account,
# employee@platform.local / Password123) to get a bearer token, then:
BASE_URL=http://localhost:8080 CUSTOMER_ID=<id> AUTH_TOKEN=<token> VUS=10  k6 run performance/load-test.js
BASE_URL=http://localhost:8080 CUSTOMER_ID=<id> AUTH_TOKEN=<token> VUS=50  k6 run performance/load-test.js
BASE_URL=http://localhost:8080 CUSTOMER_ID=<id> AUTH_TOKEN=<token> VUS=100 k6 run performance/load-test.js
```

`DatasetSize` in `Customer360BenchmarkRunner`'s `benchmark.dataset-size`
property accepts `SMALL` (~1K customers / 20K transactions, seconds to seed),
`MEDIUM` (~20K/300K, roughly a minute), or `LARGE` (100K customers / 1M+
transactions / 1M+ risk assessments / 100K+ exceptions, as named in the
original brief - expect several minutes to seed and plan for a JVM heap of a
few GB, e.g. `-Xmx4g`, for the seeding step).

## 6. What could not be run here, and why (per the exercise's own requirement)

| Step | Why it couldn't run in this sandbox | How to run it yourself |
|---|---|---|
| `mvn compile` / `mvn test` | No network access to Maven Central (`repo.maven.apache.org`) to resolve Spring Boot/Lombok/Testcontainers dependencies; no local `.m2` cache | Any machine with normal internet access and JDK 21 + Maven |
| Flyway migrations against Postgres | No Postgres instance, no Docker | `docker compose up -d postgres`, then `mvn flyway:migrate` or just start the app |
| `Customer360QueryCountIT` | Needs Testcontainers, which needs Docker | Run locally with Docker installed |
| Dataset seeding (`Customer360DatasetSeeder`) | Needs a running Postgres | Step 5 above |
| Baseline/optimized benchmark runs | Needs a running app + Postgres + the seeded data | Steps 5-6 above |
| `EXPLAIN ANALYZE` on the new query | Needs a running Postgres with the migration applied and data seeded | `psql`, after step 2 and seeding: `EXPLAIN ANALYZE SELECT ... FROM transactions.transactions WHERE initiated_by_user_id = '<id>' AND created_at >= now() - interval '90 days' ORDER BY created_at DESC LIMIT 20;` |
| k6 load test | Needs a running app + valid auth token | Step 8 above |

No step in this list has been faked, skipped-and-reported-as-passing, or had
its output invented. This table exists so you know exactly what to run to
turn the harness above into real numbers.