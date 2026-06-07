# Adversarial Review: nop-job Round 9 — Summary

**Date**: 2026-06-04
**Module**: nop-job
**Scope**: Independent Round 9 after AR-1~AR-69

## Overall Verdict: **issues**

13 new findings: 3×P1, 7×P2, 3×P3

## Verified Fixes Since R8

| Prior ID | Status |
|----------|--------|
| AR-57 (P1) | Fixed: shouldRecovery now checks activeFireCount |
| AR-59 (P1) | Fixed: persistSchedule throws instead of force-update |
| AR-60 (P2) | Fixed: enableSchedule always recalculates nextFireTime |

## Top 3 Actionable Directions

1. **Coordinator lifecycle safety (AR-70 P1, AR-74 P2)** — `doStop()` lacks per-component try-catch (cascading failure), stop order is inverted (planner stops last, creates orphaned fires).

2. **Worker identity chain is effectively dead (AR-71 P1)** — `workerInstanceId` = coordinator host, `resolveAliveWorkerIds` queries coordinator's own service. SUSPICIOUS detection always short-circuits because coordinator always sees itself as alive. The entire proactive worker-crash detection mechanism is a no-op.

3. **Completion batch isolation + block strategy fallthrough (AR-73 P2, AR-75 P2)** — Completion processor has no per-fire try-catch (one failure aborts batch). Unknown/null blockStrategy falls through to default insert, creating concurrent fires silently.

## New Findings

| ID | Sev | Description | Confidence |
|----|-----|-------------|------------|
| AR-70 | **P1** | doStop() cascading failure — no per-component try-catch | Certain |
| AR-71 | **P1** | workerInstanceId = coordinator host — SUSPICIOUS detection no-op | Certain |
| AR-72 | **P1** | Task execution timeout fallback to dispatchTimeoutMs — semantic mismatch | Certain |
| AR-73 | P2 | Completion processor no per-fire try-catch — batch abort | Certain |
| AR-74 | P2 | Stop order inverted — planner creates orphaned fires | Certain |
| AR-75 | P2 | Unknown blockStrategy falls through — concurrent fires | Certain |
| AR-76 | P2 | No config validation — zero/negative values silent malfunction | Certain |
| AR-77 | P2 | Broadcast fire status by worst task — partial success invisible | Certain |
| AR-78 | P2 | NopJobScheduleBizModel no delete override — orphans fires/tasks | Certain |
| AR-79 | P2 | Planner vs completion race on schedule COMPLETED | Likely |
| AR-80 | P3 | getCurrentTime() called multiple times — inconsistent timestamps | Certain |
| AR-81 | P3 | No metric for discarded fires (DISCARD strategy) | Certain |
| AR-82 | P3 | Dynamic bean lookup on every fire dispatch | Certain |
