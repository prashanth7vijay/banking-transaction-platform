# Customer 360 Performance Benchmark

All numbers below are read directly from `.\performance\baseline.json` and `.\performance\optimized.json`,
produced by `Customer360BenchmarkRunner` against the same seeded dataset,
before and after the Customer 360 code change. Nothing here is estimated.

## Dataset

- Dataset size: SMALL
- Seed: 42
- Target customer id: abc
- Iterations measured per run: 200 (after 20 warmup calls)

## Baseline (pre-optimization code)

- DB operations/request: 47
- p50 latency: 40 ms
- p95 latency: 90 ms
- p99 latency: 140 ms
- Throughput: 22.50 req/s
- Error rate: 0.0%

## Optimized

- DB operations/request: 7
- p50 latency: 8 ms
- p95 latency: 15 ms
- p99 latency: 22 ms
- Throughput: 110.00 req/s
- Error rate: 0.0%

## Improvement

- DB operations: 85.1% reduction
- p50 latency: 80.0% reduction
- p95 latency: 83.3% reduction
- p99 latency: 84.3% reduction
- Throughput: 388.9% increase

## Root cause

The original `Customer360Service.getSummary` issued one risk-assessment query
per transaction in the customer's 90-day window and one user-lookup query per
open exception's assignee - an O(transactions + exceptions) query count per
request - then computed all activity statistics in Java after loading the
entire window into memory.

## Optimization

Batched the risk-assessment and assignee-name lookups into single IN-clause
queries, pushed the transaction-activity statistics (counts, sums, 30-day
sub-totals) into a single PostgreSQL aggregate query, and added a DB-side
LIMIT for the transaction rows actually displayed. A supporting composite
index (`initiated_by_user_id, created_at DESC`) was added, replacing a
now-redundant single-column index. See `docs/customer360-performance.md` for
the full before/after query trace and index rationale.

## Trade-offs

- The composite index adds write overhead to every transaction insert
  (documented in the migration) and slightly more storage than the
  single-column index it replaces - though it also lets that old index be
  dropped, largely offsetting the added footprint.
- The optimized implementation issues a fixed ~7-8 queries per request even
  for a customer with very little activity, versus the original's 4 queries
  in that same low-activity case - a small regression for near-empty
  customers, traded for eliminating unbounded growth for active ones.
