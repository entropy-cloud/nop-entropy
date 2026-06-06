# Summary: Adversarial Review of nop-job

- **Date**: 2026-06-04
- **Verdict**: <ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>

## Overview

Open-ended adversarial review of `nop-job` module covering all sub-modules (api, core, dao, coordinator, worker, service, web, retry-adapter). The module is well-architected with proper status state machines, optimistic locking, and good race condition tests. No P0 or P1 issues found.

## Key Findings

| ID | Severity | Title |
|----|----------|-------|
| AR-1 | P2 | SUSPICIOUS→TIMEOUT promotion bypasses schedule timeout config |
| AR-2 | P2 | Duplicate IJobExecutionContext construction (~350 lines duplicated) |
| AR-3 | P2 | `fetchRunningFires` only matches RUNNING — DISPATCHING fires invisible to completion processor |
| AR-4 | P3 | `resolveJobParams` returns live mutable map without defensive copy |
| AR-5 | P3 | `TriggerSpec.maxFailedCount` hardcoded to 0 — dead code in LimitCountTrigger |
| AR-6 | P2 | `completeFireAndUpdateSchedule` silently swallows fire version conflicts without logging |
| AR-7 | P3 | `scheduleStatus` has no column-level constraint (standard Nop pattern, low risk) |
| AR-8 | P2 | `JobCoordinator.stopScanning()` does not await in-flight scan completion |

## Top Recommendations

1. **AR-1** (highest impact): Respect `schedule.getTimeoutSeconds()` as a floor when promoting SUSPICIOUS → TIMEOUT to prevent premature timeout during naming service blips.
2. **AR-2** (highest maintenance cost): Extract shared `IJobExecutionContext` factory to eliminate ~350 lines of duplication.
3. **AR-6** (easiest fix): Add WARN log when fire version check fails in `completeFireAndUpdateSchedule`.

## Files

- [01-open-findings.md](01-open-findings.md) — Full findings with evidence, risk analysis, and suggestions
