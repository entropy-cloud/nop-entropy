# Adversarial Review: nop-job Round 8 — Summary

**Date**: 2026-06-04
**Module**: nop-job
**Scope**: Independent Round 8 after AR-1~AR-53

## Overall Verdict: **issues**

16 new findings: 1×P0, 5×P1, 8×P2, 2×P3

## Top 3 Actionable Directions

1. **Calendar 构建和边界问题 (P0 + 2×P1)** — `DailyCalendarSpec` 默认 end time 崩溃 (AR-54), WeeklyCalendarSpec 映射错误 (AR-55), DailyCalendar 午夜排除 (AR-56)。修复简单，影响面广。

2. **RECOVERY 策略完整性 (P1 + P2)** — `shouldRecovery` 缺少 `activeFireCount` 守卫 (AR-57), `resetFailedTasks` 跳过 SUSPICIOUS 任务 (AR-61)。两个问题组合导致 RECOVERY 策略行为不完整。

3. **API 保护与 BizModel 安全 (2×P2)** — `NopJobFireBizModel` 未限制 CRUD (AR-65), xmeta 缺少只读覆盖 (AR-67)。

## New Findings

| ID | Sev | Description | Confidence |
|----|-----|-------------|------------|
| AR-54 | **P0** | DailyCalendarSpec `LocalTime.of(24,0,0)` crash | Certain |
| AR-55 | **P1** | WeeklyCalendarSpec ISO→Calendar day mapping wrong | Certain |
| AR-56 | **P1** | DailyCalendar excludes midnight + PauseCalendarTrigger = loop | Certain |
| AR-57 | **P1** | shouldRecovery missing activeFireCount guard | Certain |
| AR-58 | **P1** | Malformed resultPayload JSON aborts entire completion batch | Certain |
| AR-59 | **P1** | persistSchedule force-update after optimistic lock exhaustion | Certain |
| AR-60 | P2 | enableSchedule preserves stale nextFireTime | Certain |
| AR-61 | P2 | resetFailedTasks skips SUSPICIOUS tasks | Certain |
| AR-62 | P2 | PeriodicTrigger fixed-rate drifts | Certain |
| AR-63 | P2 | DailyCalendar.getNextIncludedTime no iteration limit | Certain |
| AR-64 | P2 | CronCalendar MAX_ITERATION returns excluded time | Certain |
| AR-65 | P2 | NopJobFireBizModel unguarded CRUD | Certain |
| AR-66 | P2 | JobFireResult.ERROR sets completed=true | Likely |
| AR-67 | P2 | NopJobFire.xmeta missing 6 read-only field overrides | Certain |
| AR-68 | P3 | Metrics discard durationMs for failures/timeouts | Certain |
| AR-69 | P3 | CronCalendar declares throws ParseException but throws NopException | Certain |

## Prior Findings Status (AR-40~AR-53 still unfixed)

| ID | Sev | Description | Status |
|----|-----|-------------|--------|
| AR-40 | P2 | OnceTrigger misfire ignored | Unfixed |
| AR-42 | P2 | JobPartitionResolver no naming cache | Unfixed |
| AR-43 | P2 | CronExpression new GregorianCalendar every call | Unfixed |
| AR-44 | P2 | Helper methods use updateEntityDirectly | Unfixed |
| AR-46 | P2 | JobPartitionResolver first call returns false | Unfixed |
| AR-48 | P2 | BizModel recalculateNextFireTime uses local clock | Unfixed |
| AR-49 | P2 | rerunFire copies stale jobParamsSnapshot | Unfixed |
| AR-50 | P2 | tryMarkDispatchTimeout loadSchedule NPE on deleted schedule | Unfixed |
| AR-51 | P2 | Timeout checker ignores updateTask return | Unfixed |
| AR-52 | P2 | NopRetryJobRetryBridge returns null | Unfixed |
