# nop-job 阻塞策略设计

> Status: draft
> Created: 2026-05-17
> Related: `ai-dev/plans/17-nop-job-block-strategy-metrics.md`

## 1. 决策：四种阻塞策略

| 策略 | 语义 | 实现位置 |
|------|------|----------|
| DISCARD | 有 active fire 时跳过本次触发 | `JobPlannerScannerImpl.shouldDiscard()` — 已实现 |
| OVERLAY | 有 active fire 时取消旧的、创建新的 | `shouldOverlay()` — **cancel 旧 fire 的逻辑缺失** |
| PARALLEL | 不判断，直接创建新 fire | 默认行为 — 已实现 |
| RECOVERY | 最近一次 fire 失败时重新触发 | **未实现** |

## 2. 需要补齐的

### OVERLAY — cancel 旧 fire

当前 `overlayFireAndAdvanceSchedule` 只创建新 fire，没有 cancel 旧的。需要在 overlay 前通过 `IJobCancelHandler` 取消正在执行的旧 fire。

需要 `IJobScheduleStore` 增加 `findActiveFires(String scheduleId)` 方法。

### RECOVERY — 新增策略

ORM dict 增加 RECOVERY=4。条件：没有 active fire + 最近一次 fire 失败。

**前置依赖**：Schedule 上需要有 `lastFireStatus` 聚合字段（plan 17 slice 3 中实现）。

## 3. 与 snail-job 对比

| 策略 | snail-job | nop-job |
|------|-----------|---------|
| DISCARD | ✅ | ✅ |
| OVERLAY | ✅ 停止当前 + 新建 | ⚠️ 缺 cancel |
| CONCURRENCY | ✅ | ✅ PARALLEL |
| RECOVERY | ✅ | ❌ 待实现 |
