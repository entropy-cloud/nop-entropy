# nop-job 超时与恢复设计

**日期**：2026-08-12
**范围**：`nop-job` 的超时语义边界、fire/task 恢复路径、`activeFireCount` 计数一致性
**状态**：设计完成（plan 340 §1.2）

---

## 1. 设计结论

### 1.1 三层超时语义边界（Decision，修正 P1-1）

nop-job 存在三个语义独立的超时，必须严格区分，不得互相回退：

| 超时 | 作用对象 | 语义 | 默认值 | 配置来源 |
|------|----------|------|--------|----------|
| `dispatchTimeoutMs` | fire（DISPATCHING 状态） | fire 已被 dispatcher 锁定但 task 迟未插入（coordinator 卡死/崩溃） | 300000ms（5min） | `nop.job.timeout.dispatch-timeout-ms` |
| `executionTimeoutMs` | task（RUNNING 状态） | task 执行墙钟超时；≤0 表示禁用 | -1（禁用） | `nop.job.timeout.execution-timeout-ms` |
| `schedule.timeoutSeconds` | task（RUNNING 状态） | per-schedule 覆盖执行超时；空/0 表示不覆盖，回退到 `executionTimeoutMs` | 空 | `nop_job_schedule.timeout_seconds` 列 |

**裁定**：`JobTimeoutCheckerImpl` 判定 RUNNING-task 是否超时的优先级为 `schedule.timeoutSeconds > 0` → 否则 `executionTimeoutMs > 0` → **否则不超时（return）**。

**拒绝的替代方案**：原实现把 else 分支回退到 `dispatchTimeoutMs`（commit history），语义错配——`dispatchTimeoutMs` 是"fire 卡在 DISPATCHING"的超时，复用为 task 执行超时会误杀所有未配 per-schedule 超时的长任务（>5min）。该回退已移除（plan 340 §2.1）。

**长任务兜底**：未配任何执行超时的 RUNNING task 由 **worker-liveness 链**兜底——`JobTimeoutCheckerImpl` 周期检查 task 归属 worker 是否存活，不存活则 `CLAIMED/RUNNING → SUSPICIOUS → TIMEOUT`（`tryMarkSuspiciousIfWorkerGone` → `markSuspiciousAsTimeout`）。这是比墙钟超时更根本的死任务回收机制，不依赖超时配置。

### 1.2 recovery 退避（Decision，修正 P2-2）

`recoveryFireAndAdvanceSchedule`（BLOCK_STRATEGY=RECOVERY 时复用失败 fire）在判定失败 fire 已不可恢复（被并发 rerun 改成非 FAILED 状态）时，**仍须推进 `schedule.nextFireTime`** 后再返回，不得裸 `return`。

**理由**：nextFireTime 留在过去 → planner 下个 5s 周期重 fetch 同一 schedule → 命中同一不可恢复 fire → 再次 return → 紧重试循环无退避，浪费扫描资源。推进 nextFireTime 让 schedule 回到正常节律。

### 1.3 `failFireWithoutSchedule` 版本冲突可观测（Decision，修正 P2-3）

fire 的终态写入（`tryUpdateWithVersionCheck`）返回 false（版本冲突，已被并发终结）时必须 `LOG.warn`，不得丢弃返回值。

**理由**：丢弃返回值时 fire 静默留在 DISPATCHING，超时检查器每 5s 重入同一路径，形成无限静默循环。warn 使运维可感知；该 fire 实际已被并发处理，无需额外修复。

---

## 2. `activeFireCount` 计数一致性（Decision，修正 P2-4）

### 2.1 约束：完成/取消路径内不可重试

`JobCompletionProcessorImpl.completeSingleFire` 与 `JobFireStoreImpl.cancelFire`/`completeFireAndUpdateSchedule` 均标注 `@SingleSession`。在该注解下，`requireEntityById` 返回**缓存实体**（同会话内已 dirty 的对象），reload 无法读到最新数据——**路径内 reload + 有界重试无意义**（`JobFireStoreImpl.java:132-136` 注释明确）。

因此当 fire 终态写入成功但 schedule 版本冲突（返回 `FireScheduleOutcome.fireOnly()`）时，schedule 的 `activeFireCount` 不会递减。`Math.max(0, activeFireCount - 1)` 防止负值，但**不自愈**——漂移会累积。

### 2.2 选定方案：独立会话对账器

新增 `JobScheduleCounterReconciler`（独立 scanner，**non-`@SingleSession`**）周期性收敛漂移：

- **扫描范围**：`scheduleStatus=ENABLED AND activeFireCount > 0` 的 schedule。
- **重算**：对每个此类 schedule，聚合查询 `count(*) from nop_job_fire where jobScheduleId=? and fireStatus < 30`（非终态 fire 数），与 `schedule.activeFireCount` 比对。
- **修正**：mismatch 则 `tryUpdateWithVersionCheck` 写回正确值。
- **写回风暴防护**：单次扫描每 schedule 至多一次写回；遇版本冲突**不重试**（warn + 留待下个周期），避免热 schedule 上的写竞争。
- **扫描间隔**：复用 planner 的 `scan-interval-ms`，不引入新线程/新配置。

### 2.3 deviation 说明

现有 5 个 scanner（`JobTimeoutCheckerImpl`/`JobDispatcherScannerImpl`/`JobPlannerScannerImpl`/`JobWorkerScannerImpl`/`JobCompletionProcessorImpl`）均 `@SingleSession`（`AbstractBatchScanner:24` 约定，保证扫描会话内实体可见性）。`JobScheduleCounterReconciler` 是**刻意的例外**——它必须在独立会话里读 live（非缓存）fire 计数才能正确重算，这正是它存在的意义。deviation 在类 javadoc 中标注。

### 2.4 拒绝的替代方案

- **路径内有界重试**：被 `@SingleSession` 缓存语义排除（见 2.1）。
- **仅 warn 不修**：漂移会累积到 `activeFireCount > 0` 误阻塞 DISCARD/OVERLAY/RECOVERY 调度（`JobPlannerScannerImpl:225-227` 的 `shouldDiscard` 读该字段），不可接受。
- **改 5 个 scanner 去掉 @SingleSession**：波及面大，破坏既有可见性约定，风险高于收益。

---

## 3. 关联文档

- 超时扫描的游标分页契约见 `store-contract-design.md`。
- cancelFire 的原子契约（schedule 计数随 fire 取消更新）见 `store-contract-design.md`。
- 完成路径 dirty-flush 冲突的可观测性（P2-6）见 `store-contract-design.md`。
