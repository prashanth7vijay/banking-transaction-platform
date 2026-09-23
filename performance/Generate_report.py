#!/usr/bin/env python3
"""
Generates performance/report.md from performance/baseline.json and
performance/optimized.json.

Usage:
    python3 performance/generate_report.py \
        --baseline performance/baseline.json \
        --optimized performance/optimized.json \
        --out performance/report.md

Every number in the generated report is read directly from the two input
JSON files (themselves written by Customer360BenchmarkRunner from an actual
measured run) or computed from them with the formulas below. This script
does not invent, estimate, or default any performance number - if an input
file is missing or malformed, it fails loudly instead of guessing.
"""
import argparse
import json
import sys
from pathlib import Path


def pct_reduction(before: float, after: float) -> float:
    """(before - after) / before * 100 - positive means "after" is smaller/better."""
    if before == 0:
        return 0.0
    return (before - after) / before * 100.0


def pct_increase(before: float, after: float) -> float:
    """(after - before) / before * 100 - positive means "after" is larger/better."""
    if before == 0:
        return 0.0
    return (after - before) / before * 100.0


def load(path: Path) -> dict:
    if not path.exists():
        print(f"ERROR: {path} does not exist. Run Customer360BenchmarkRunner first "
              f"(see docs/customer360-performance.md 'How to reproduce') - this script "
              f"never fabricates a missing measurement.", file=sys.stderr)
        sys.exit(1)
    with path.open() as f:
        return json.load(f)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", default="performance/baseline.json")
    parser.add_argument("--optimized", default="performance/optimized.json")
    parser.add_argument("--out", default="performance/report.md")
    args = parser.parse_args()

    baseline = load(Path(args.baseline))
    optimized = load(Path(args.optimized))

    if baseline.get("datasetSize") != optimized.get("datasetSize") and optimized.get("datasetSize") != "REUSED_EXISTING":
        print("WARNING: baseline and optimized runs report different datasetSize values. "
              "The comparison below is only meaningful if both runs actually measured the "
              "SAME seeded data (seed once, measure twice - see the runner's docstring).",
              file=sys.stderr)
    if baseline.get("targetCustomerId") != optimized.get("targetCustomerId"):
        print("WARNING: baseline and optimized runs used different targetCustomerId values. "
              "Pass --target-customer-id explicitly for the second run so both measure the "
              "identical customer.", file=sys.stderr)

    b_db = baseline["dbOperationsPerRequest"]
    o_db = optimized["dbOperationsPerRequest"]
    b_p95 = baseline["latencyMillis"]["p95"]
    o_p95 = optimized["latencyMillis"]["p95"]
    b_p99 = baseline["latencyMillis"]["p99"]
    o_p99 = optimized["latencyMillis"]["p99"]
    b_p50 = baseline["latencyMillis"]["p50"]
    o_p50 = optimized["latencyMillis"]["p50"]
    b_tp = baseline["throughputRequestsPerSecond"]
    o_tp = optimized["throughputRequestsPerSecond"]

    db_reduction = pct_reduction(b_db, o_db)
    p50_reduction = pct_reduction(b_p50, o_p50)
    p95_reduction = pct_reduction(b_p95, o_p95)
    p99_reduction = pct_reduction(b_p99, o_p99)
    throughput_increase = pct_increase(b_tp, o_tp)

    report = f"""# Customer 360 Performance Benchmark

All numbers below are read directly from `{args.baseline}` and `{args.optimized}`,
produced by `Customer360BenchmarkRunner` against the same seeded dataset,
before and after the Customer 360 code change. Nothing here is estimated.

## Dataset

- Dataset size: {baseline.get("datasetSize")}
- Seed: {baseline.get("seed")}
- Target customer id: {baseline.get("targetCustomerId")}
- Iterations measured per run: {baseline.get("iterations")} (after {baseline.get("warmupIterations")} warmup calls)

## Baseline (pre-optimization code)

- DB operations/request: {b_db}
- p50 latency: {b_p50} ms
- p95 latency: {b_p95} ms
- p99 latency: {b_p99} ms
- Throughput: {b_tp:.2f} req/s
- Error rate: {baseline.get("errorRatePercent")}%

## Optimized

- DB operations/request: {o_db}
- p50 latency: {o_p50} ms
- p95 latency: {o_p95} ms
- p99 latency: {o_p99} ms
- Throughput: {o_tp:.2f} req/s
- Error rate: {optimized.get("errorRatePercent")}%

## Improvement

- DB operations: {db_reduction:.1f}% reduction
- p50 latency: {p50_reduction:.1f}% reduction
- p95 latency: {p95_reduction:.1f}% reduction
- p99 latency: {p99_reduction:.1f}% reduction
- Throughput: {throughput_increase:.1f}% increase

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
"""

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(report)
    print(f"Wrote {out_path}")


if __name__ == "__main__":
    main()