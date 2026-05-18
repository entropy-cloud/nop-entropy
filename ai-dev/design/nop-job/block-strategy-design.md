# nop-job 阻塞策略设计

> Status: implemented
> Created: 2026-05-17
> Last Updated: 2026-05-17
> Related: `ai-dev/plans/17-nop-job-block-strategy-metrics.md`

## 1. 决策：四种阻塞策略

| 策略 | 语义 | 实现位置 |
|------|------|----------|
| DISCARD | 有 active fire 时跳过本次触发 | `JobPlannerScannerImpl.shouldDiscard()` — 已实现 |
| OVERLAY | 有 active fire 时取消旧的、创建新的 | `shouldOverlay()` → `JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` — 已实现 cancel |
| PARALLEL | 不判断，直接创建新 fire | 默认行为 — 已实现 |
| RECOVERY | 查找最近一次失败的 fire 并重置为 WAITING | `shouldRecovery()` → `JobScheduleStoreImpl.recoveryFireAndAdvanceSchedule()` — 已实现 |

## 2. OVERLAY Cancel 实现

`JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` 流程：
1. `findActiveFires(scheduleId)` — 查找所有 WAITING/DISPATCHING/RUNNING 状态的 fire
2. 对每个 active fire：`cancelFire()` → 设置 CANCELED 状态 + ERR_JOB_OVERLAID
3. 对每个 active fire 的 tasks：`cancelTasks()` → 设置 CANCELED 状态
4. 创建新 fire
5. 设置 `schedule.activeFireCount = 1`（只有新 fire 是 active 的）

## 3. RECOVERY 实现

`JobPlannerScannerImpl.shouldRecovery()` 条件：`blockStrategy == RECOVERY`（不需要检查 activeFireCount）。

`JobScheduleStoreImpl.recoveryFireAndAdvanceSchedule()` 流程：
1. `findFailedFires(scheduleId)` — 查找 FAILED/TIMEOUT 状态的 fire（最新一条）
2. 如果没有失败 fire：仅推进 `nextFireTime`，不创建新 fire
3. 如果有失败 fire：
   - 重置 fire 状态为 WAITING，清除 errorCode/errorMessage/endTime/durationMs
   - `resetFailedTasks()` — 重置关联 tasks 为 WAITING
   - `schedule.activeFireCount += 1`

## 4. 与 snail-job 对比

| 策略 | snail-job | nop-job |
|------|-----------|---------|
| DISCARD | ✅ | ✅ |
| OVERLAY | ✅ 停止当前 + 新建 | ✅ cancel 旧 fire/tasks + 新建 |
| CONCURRENCY | ✅ | ✅ PARALLEL |
| RECOVERY | ✅ | ✅ 重置失败 fire 为 WAITING |

nop-job 的 RECOVERY 实现比 snail-job 更优：不创建新 fire，而是重置失败 fire 的状态和关联 tasks，保留原始调度链路的审计信息。
