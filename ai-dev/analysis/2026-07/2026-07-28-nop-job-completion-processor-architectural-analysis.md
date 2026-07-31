# nop-job Completion Processor 架构再分析：批处理 Session 的问题与 Per-Fire 事务方案

> Status: open
> Date: 2026-07-28
> Scope: nop-job-coordinator (JobCompletionProcessorImpl)、nop-job-dao (JobFireStoreImpl, JobScheduleStoreImpl)
> Conclusion: Plan 301（2026-07-18）已修复 tryUpdateWithVersionCheck 死代码和 retry loop readonly 污染。遗留的核心问题是 `completeFireAndUpdateSchedule` 返回 void 导致指标/告警在写入失败后误触发。Per-Fire 事务是推荐的架构修正，能同时解决此问题并简化 Store 层。

---

## 1. 审查过程

本报告经历了以下审查流程：

1. **初稿**：分析当前代码（Plan 301 修复后），指出批处理级 `@SingleSession` 的架构缺陷
2. **独立技术审查**（sub-agent）：验证了代码级事实，发现多处未验证的 ORM 行为假设
3. **独立架构审查**（sub-agent + pangu）：评估方案可行性，发现内部不一致和遗漏的权衡

以下报告已纳入全部审查反馈，关键修正点：
- ✅ 验证了 `EntityPersisterImpl.incOptimisticLockVersion()` 和 `persisterPostUpdate().orm_clearDirty()` 的实际行为 → **跨 Fire Schedule 干扰不成立**（版本在内存中递增，脏标志清除）
- ✅ 验证了 Plan 301 的 `orm_readonly()` guard 已防止第二写入路径
- ✅ 指标误触发问题被拆分为独立修复（`completeFireAndUpdateSchedule` 返回 `boolean`）和架构级修复（Per-Fire 事务）
- ✅ Per-Fire 代码草案修正为使用 `REQUIRES_NEW` + 返回结果对象（非 `boolean`）

---

## 2. 当前架构（Plan 301 修复后）

```
scanBatch()                                   ← @SingleSession (整个批次共享)
  │
  ├─ fireStore.fetchRunningFires()             ← 加载所有 Fire，进入 Session 缓存
  │
  ├─ for each fire:
  │     taskStore.findTasksByFireId()          ← Session 缓存读取
  │     scheduleStore.tryLoadSchedule()        ← Session 缓存读取
  │     compute completion (in-memory)
  │     fire.setFireStatus(...)                ← 修改 Fire（Session 缓存中的对象）
  │     schedule.setActiveFireCount(...)       ← 修改 Schedule（Session 缓存中的对象）
  │     completeFireAndUpdateSchedule(fire, schedule)  ← @Transactional(REQUIRES_NEW)
  │       ├─ tryUpdateWithVersionCheck(fire)       ← flush 脏实体到新事务
  │       ├─ tryUpdateWithVersionCheck(schedule)   ← flush 脏实体到新事务
  │       └─ 返回 void
  │     fire metrics/alarms                    ← 无条件执行
  │
  └─ @SingleSession 结束 → session.flush()
```

### Plan 301 修复的三个 bug

| Bug | 修复 | 文件 |
|-----|------|------|
| A: `completeFireAndUpdateSchedule` 死代码 | 移除 `requireEntityById` + `isTerminalFire` 短路 | `JobFireStoreImpl:122-124` |
| B: retry loop readonly 污染 | 移除 retry loop；单次 `tryUpdateWithVersionCheck` 靠 entity dirty 属性 flush | `JobFireStoreImpl:134-136` |
| C: 超时路径状态不一致 | `tryMarkDispatchTimeout` 检查 `fire.orm_readonly()` 后跳过 task 取消 | `JobTimeoutCheckerImpl` |

详见 `ai-dev/plans/301-nop-job-complete-fire-transaction-session-bugs.md`。

---

## 3. 遗留问题分析

### 3.1 [P0] `completeFireAndUpdateSchedule` 返回 void → 指标/告警在写入失败后仍触发

`completeFireAndUpdateSchedule` (`JobFireStoreImpl:121-137`) 当 `tryUpdateWithVersionCheck(fire)` 返回 `false` 时静默返回：

```java
@Transactional(REQUIRES_NEW)
public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
    if (!fireDao().tryUpdateWithVersionCheck(fire)) {
        return;  // void 返回，调用方不知失败
    }
    if (!scheduleDao().tryUpdateWithVersionCheck(schedule)) {
        LOG.warn("...");
    }
}
```

`tryCompleteFireAndGetStatus` 无条件执行指标和告警（第 208-217 行）：

```java
fireStore.completeFireAndUpdateSchedule(fire, schedule);
// 无论是否写入，都执行下方代码：
completionMetrics.onFireSuccess(duration);       // false positive!
handleAlarmTimeout(fire, schedule, duration);     // false alarm!
handleRetryAndAlarm(fire, schedule, duration);    // false retry!
return finalFireStatus;  // scanBatch 递增 completedCount
```

**触发条件**：双 Coordinator 部署时，同一 Fire 被两个实例同时扫描。一个版本冲突失败后仍触发指标/告警。

**严重度**：🔴 P0（多 Coordinator 部署下确定发生）。单 Coordinator 下不存在，但 nop-job 设计目标就是多实例高可用。

**修复选项**：
- **直接修复（推荐独立施行）**：`completeFireAndUpdateSchedule` 返回 `boolean`，调用方根据返回值决定是否触发指标/告警
- **架构修复**：Per-Fire 事务（见第 5 节），自然消除此问题

这两个修复是独立的。直接修复 < 5 行代码变更，应作为 Plan 301 的 follow-up 立即实施。

---

### 3.2 [P1] `failFireWithoutSchedule` 和 `updateRetryRecordId` 使用 `updateEntityDirectly`，版本冲突时抛异常

`failFireWithoutSchedule` (`JobFireStoreImpl:258-268`)：

```java
@Transactional(REQUIRES_NEW)
public void failFireWithoutSchedule(String jobFireId, ...) {
    NopJobFire fire = fireDao().requireEntityById(jobFireId);
    //                        ↑ @SingleSession 缓存，不是 DB 最新值
    fire.setFireStatus(FIRE_STATUS_FAILED);
    fireDao().updateEntityDirectly(fire);
    //      ↑ 未设置 orm_disableVersionCheckError(true)
    //        版本冲突时 flushUpdate → checkUpdateResult(0, ...) → throw ERR_ORM_UPDATE_ENTITY_NOT_FOUND
}
```

**问题**：
- 此方法是**副效应操作**（Schedule 已删，Fire 需标记失败），不应该因版本冲突抛异常
- 异常被 `scanBatch` 的 catch 捕获（第 130-132 行），warn 日志后继续处理其他 Fire，业务上无害但产生错误日志噪音
- 如果是在 Per-Fire 事务架构中，此方法可直接内联到 `completeSingleFire`，用自然 flush 替代 `updateEntityDirectly`

**类似问题**：`updateRetryRecordId`（`JobFireStoreImpl:250-256`）同为副效应操作，同样使用 `updateEntityDirectly`。

**修复**：改为 `tryUpdateWithVersionCheck`（返回 false 静默），或在 Per-Fire 事务中消除此方法。

---

### 3.3 [P3] 当前架构依赖的 `orm_readonly()` guard 是脆弱的设计

Plan 301 引入了 `orm_readonly()` 检查来防止第二写入路径（`JobTimeoutCheckerImpl` 中）。但：

- `orm_readonly()` 是一个**内部标志位**，不是 API 契约
- 任何未来代码变更如果调用了 `entity.orm_unload()` 或其他重置状态的操作，都会静默清除此标志，暴露不一致状态
- Nop ORM 的 `orm_unload()` 实现确认**不清除 readonly 标志**（Plan 301 分析已证实），但这只是当前实现，不是语言级保证

此问题不迫切（`orm_readonly()` guard 当前正常工作），但是在 Per-Fire 事务架构中自然消除。

---

### 3.4 [P3] 之前报告中声称的"跨 Fire Schedule 干扰"——经代码验证不成立

初稿中宣称（第 2.1 节）同一批次中两个 Fire 引用同一 Schedule 会导致 `activeFireCount` 确定性泄漏。经代码验证**不成立**。

`EntityPersisterImpl.queueUpdate`（第 470-489 行）在成功更新后执行：

```java
incOptimisticLockVersion(entity);     ← 内存中版本号 +1
session.persisterPostUpdate(entity);  ← orm_clearDirty() 清除脏标志
```

`incOptimisticLockVersion`（第 370-377 行）：

```java
void incOptimisticLockVersion(IOrmEntity entity) {
    int versionProp = entityModel.getVersionPropId();
    if (versionProp > 0) {
        Object value = entity.orm_propValue(versionProp);
        value = MathHelper.add(value, 1);
        entity.orm_internalSet(versionProp, value);  // 内存中版本递增
    }
}
```

所以同一 Session 中连续处理两个 Fire 时：

| 步骤 | Fire A | Fire B |
|------|--------|--------|
| 初始状态 | schedule.version=1 | schedule.version=1（同一对象） |
| setActiveFireCount | schedule.dirty=true | schedule.dirty=true |
| 第一次 tryUpdateWithVersionCheck | UPDATE WHERE version=1 → 成功 → **version=2, dirty=false** | — |
| 第二次 setActiveFireCount | — | schedule.dirty=true, **version=2（正确！）** |
| 第三次 tryUpdateWithVersionCheck | — | UPDATE WHERE version=2 → 成功 → **version=3, dirty=false** |

**结论**：单 Coordinator 实例内，ORM 在成功更新后已在内存中递增版本号并清除脏标志，下一个 Fire 使用正确版本号操作。不存在确定性泄漏。

---

## 4. 确认：`updateEntityDirectly` 在 Per-Fire 事务中不再必要

`updateEntityDirectly` 和 `saveEntityDirectly` 的所有使用位置：

| 方法 | 位置 | 当前用途 | 在 Per-Fire 事务中 |
|------|------|----------|-------------------|
| `completeFireAndUpdateSchedule` | `JobFireStoreImpl:121` | `tryUpdateWithVersionCheck`（已有版本检查） | ✅ 保留 `tryUpdateWithVersionCheck`（显式版本检查优于隐式 flush） |
| `failFireWithoutSchedule` | `JobFireStoreImpl:267` | 版本冲突时抛异常 | ❌ 消除方法。直接内联：`fire.setFireStatus(FAILED)`，事务提交自然 flush |
| `updateRetryRecordId` | `JobFireStoreImpl:255` | 同上 | ❌ 消除方法。同上 |
| `insertTasksAndMarkFireDispatching` | `JobFireStoreImpl:115` | `saveEntityDirectly`（INSERT） | ✅ 此方法属于 Dispatch 路径，不在 Per-Fire 范围内 |
| `JobScheduleStoreImpl` 各 `saveEntityDirectly` | 多处 | 创建新 Fire | ✅ 属于 Plan 路径，不在 Per-Fire 范围内 |

`failFireWithoutSchedule` 和 `updateRetryRecordId` 的存在本身就是当前架构缺陷的证据：因为读写不在同一事务，需要独立副效应方法在 `REQUIRES_NEW` 中手动刷出。在 Per-Fire 事务中，它们只是 `completeSingleFire` 中的几行 setter。

---

## 5. Per-Fire 事务方案（推荐架构修正）

### 5.1 核心设计

`scanBatch` 不再有 `@SingleSession`，只负责取出 Fire ID。每个 Fire 在独立事务 + 独立 Session 中完成。

```
scanBatch()
  │
  ├─ fireStore.fetchRunningFires()    ← 独立临时 Session，只读 Fire ID 和 partition 信息
  │
  ├─ for each fire.getJobFireId():
  │     completeSingleFire(fireId)    ← @Transactional(REQUIRES_NEW) + @SingleSession
  │       ├─ loadFire(fireId)          ← 新 Session，新鲜数据（这是必要的"重复加载"）
  │       ├─ resolveFinalFireStatus    ← 同一事务中一致性读取
  │       ├─ 修改 Fire + Schedule      ← 脏，事务提交时自然 flush
  │       └─ 返回 FireCompletionResult (status, duration) 或 null
  │     └─ 根据 result 触发指标/告警  ← 仅当写入确认后才触发
  │
  └─ return fires.size() >= batchSize
```

**`scanBatch` 不要 `@Transactional`**：`fetchRunningFires` 在临时 Session 中执行，返回的 Fire 实体只读取标量字段（`jobFireId`），不跨越 Session 边界。

### 5.2 代码草案

```java
// JobCompletionProcessorImpl
@Override
protected boolean scanBatch() {
    IntRangeSet partitions = partitionResolver != null ? partitionResolver.resolvePartitions() : null;
    List<NopJobFire> fires = fireStore.fetchRunningFires(batchSize, partitions);
    // ↑ 返回的 fires 是临时 Session 中的托管实体
    // 只读取 getJobFireId() 等标量字段，不访问 lazy 关联

    if (fires.isEmpty()) return false;

    int completedCount = 0;
    for (NopJobFire fire : fires) {
        try {
            FireCompletionResult result = completeSingleFire(fire.getJobFireId());
            if (result != null) {
                completedCount++;
                fireCompletionMetrics(result);  // 仅在此触发
            }
        } catch (Exception e) {
            LOG.warn("nop.job.completion.fire-complete-failed:fireId={}", fire.getJobFireId(), e);
        }
    }
    if (completedCount > 0) {
        completionMetrics.onFiresCompleted(completedCount);
    }
    return fires.size() >= batchSize;
}

/**
 * 每个 Fire 在独立事务+Session 中完成。
 * @Transactional(REQUIRES_NEW) 确保每个 Fire 独立提交/回滚。
 * @SingleSession 提供独立的 ORM Session，避免与其他 Fire 共享缓存。
 * AOP 拦截器顺序：@SingleSession（外层）→ @Transactional（内层）。
 * 这与 AbstractDaoHandler.runLocal() 的 canonical pattern 一致。
 */
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
@SingleSession
FireCompletionResult completeSingleFire(String fireId) {
    NopJobFire fire = fireStore.loadFire(fireId);
    if (fire == null || fire.getFireStatus() != _NopJobCoreConstants.FIRE_STATUS_RUNNING) {
        return null;
    }

    List<NopJobTask> tasks = taskStore.findTasksByFireId(fireId);
    if (tasks.isEmpty()) return null;

    Integer finalFireStatus = resolveFinalFireStatus(tasks);
    if (finalFireStatus == null) return null;  // 任务仍在执行

    NopJobSchedule schedule = scheduleStore.tryLoadSchedule(fire.getJobScheduleId());

    if (schedule == null) {
        // Schedule 已删除，标记 Fire 失败
        // 直接在实体上 setter，事务提交时自然 flush（不需要 updateEntityDirectly）
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_FAILED);
        fire.setEndTime(new Timestamp(scheduleStore.getCurrentTime()));
        fire.setErrorCode(JobCoreErrors.ERR_JOB_SCHEDULE_DELETED.getErrorCode());
        fire.setErrorMessage(JobCoreErrors.ERR_JOB_SCHEDULE_DELETED.getDescription());
        return new FireCompletionResult(FIRE_STATUS_FAILED, 0L);
    }

    // compute completion decision
    Timestamp fireEndTime = latestEndTime(tasks, new Timestamp(scheduleStore.getCurrentTime()));
    FireCompletionDecision decision = resolveCompletionDecision(tasks, schedule);
    // ... set fire fields, schedule counters ...

    // 事务提交时自然 flush 所有脏实体。
    // 版本冲突导致 @Transactional 回滚 → 抛异常 → scanBatch 捕获 → 下个周期重试
    return new FireCompletionResult(finalFireStatus, durationMs);
}

// 返回值对象，替代当前 void
record FireCompletionResult(Integer finalStatus, long durationMs) {}
```

### 5.3 解决了什么

| 问题 | 当前架构 | Per-Fire 事务 |
|------|---------|--------------|
| **指标/告警误触发** | `completeFireAndUpdateSchedule` 返回 void，调用方无法判断是否成功 | 返回 `FireCompletionResult`，调用方仅在非 null 时触发指标 |
| **`failFireWithoutSchedule` 抛异常** | 副效应方法用 `updateEntityDirectly`，版本冲突抛异常 | 方法内联，自然 flush，无需 `updateEntityDirectly` |
| **`orm_readonly()` guard 脆弱性** | 依赖内部标志位防止第二写入路径 | 每个 Fire 独立 Session，无第二写入路径 |
| **Session 缓存污染** | 批次内所有 Fire 共享 Session 缓存 | 每个 Fire 独立 Session，自然隔离 |
| **死代码陷阱** | `requireEntityById` 返回缓存对象 | 新 Session 中返回 DB 最新值 |
| **缓存不可刷新** | @SingleSession 下 retry loop 注定失败 | 新 Session，每次都是新鲜数据 |
| **"重复加载"** | fetchRunningFires 加载一次，failFireWithoutSchedule 又加载一次 | 一次：loadFire 直接获取最新数据 |

### 5.4 代价

| 代价 | 说明 | 评估 |
|------|------|------|
| **额外 loadFire 查询** | 每个 Fire 多一次主键查询 | 🟢 O(1) 操作，可忽略 |
| **AOP 拦截器开销** | 每个 Fire 触发 `@SingleSession` + `@Transactional` 拦截器链 | 🟡 batch_size=100 每秒约 100 次 Session+Transaction 创建。对于 5s 间隔的后台调度器可忽略，但间隔 < 1s 时需监控 |
| **事务数量增加** | 每个 Fire 独立事务 | 🟢 Completion 流程无冲突（Fire 即将完成），短事务很快释放 |
| **与现有 Store 模式不一致** | 其他 Scanner 仍用 `@SingleSession` + `REQUIRES_NEW` | 🟡 需要记录差异，明确 Completion 的特殊性 |

### 5.5 替代方案对比：为什么不选其他方案

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| **仅改 `completeFireAndUpdateSchedule` 返回 boolean** | 最小改动（< 5 行），修复指标误触发 | 不解决 `failFireWithoutSchedule` 抛异常、`orm_readonly()` 脆弱性、Session 缓存污染 | ✅ **应立即实施**，作为 Plan 301 follow-up，不必等 Per-Fire |
| **内层 Session 读取 Schedule（仅改 Store 方法）** | 消除跨 Fire Schedule 干扰，不改动 Scanner | 需要手动物字段拷贝，产生 detached entity 需 `updateEntityDirectly`，维护成本高 | ❌ 复杂度过高，Per-Fire 更简洁 |
| **批次末批量更新 Schedule** | 单批次内 Schedule 只写一次 | 指标不能 per-Fire 触发，崩溃时推迟到下一扫描周期，聚合逻辑复杂 | ❌ 增加复杂性，收益低 |
| **Per-Fire 事务（本报告推荐）** | 消除全部遗留问题，接口简洁 | 架构变更（现有模式二 → 模式五），AOP 开销 | ✅ **推荐**

---

## 6. 与现有模式的关系

### 6.1 不同 Scanner 的不同模式

| Scanner | 实体 | 共享风险 | 推荐模式 |
|---------|------|---------|---------|
| **Planner** (`JobPlannerScannerImpl`) | Schedule | 低（各 Schedule 独立） | 模式二：`@SingleSession` + `REQUIRES_NEW` |
| **Dispatcher** (`JobDispatcherScannerImpl`) | Fire | 低（各 Fire 独立） | 模式二 |
| **Completion Processor** (`JobCompletionProcessorImpl`) | Fire + **Schedule** | **高**（多 Fire 共享 Schedule） | **模式五：Per-Fire 独立事务** |
| **Timeout Checker** (`JobTimeoutCheckerImpl`) | Fire + **Schedule** | **高**（与 Completion 同理） | **模式五** |

### 6.2 建议新增模式五到文档

在 `docs-for-ai/02-core-guides/concurrency-and-transactions.md` 中增加：

> **模式五：Per-Item 独立事务（适用于共享实体的写操作）**
>
> 当每个工作项需要更新可能被并发的共享实体时，应使用 Per-Item 独立事务。
>
> ```
> Scanner（无 @SingleSession）
>   │
>   ├─ fetchItems()
>   │
>   ├─ for each itemId:
>   │     processItem(itemId)     ← @SingleSession + @Transactional(REQUIRES_NEW)
>   │       ├─ reload item          ← 新 Session，新鲜数据
>   │       ├─ read dependencies    ← 同一事务中一致性读取
>   │       ├─ modify entities      ← 脏，事务提交自然 flush
>   │       └─ return result        ← 非 void，调用方据此决策
>   │
>   └─ 根据 return result 触发副效应（指标/告警）
> ```
>
> **限制**：
> - `scanBatch` 不能有 `@SingleSession`
> - `processItem` 必须 `@Transactional(REQUIRES_NEW)`，保证独立提交
> - AOP 拦截器顺序必须是 `@SingleSession`（外层）→ `@Transactional`（内层）
> - 返回值必须携带处理结果（非 `void`），调用方据此判断是否触发副效应

### 6.3 `IJobFireStore` 接口契约对齐

如果实施 Per-Fire 事务，`IJobFireStore` 中以下方法可以简化或消除：

| 接口方法 | 变更 |
|---------|------|
| `completeFireAndUpdateSchedule(fire, schedule): void` | → 改为 `boolean`（作为直接修复）或消除（Per-Fire 中不再需要此方法，逻辑内联到 `completeSingleFire`） |
| `failFireWithoutSchedule(jobFireId, errorCode, errorMessage): void` | 消除。在 `completeSingleFire` 中直接 setter |
| `updateRetryRecordId(jobFireId, retryRecordId): void` | 消除。在 `completeSingleFire` 中直接 `fire.setRetryRecordId(...)` |

但建议分步实施：
1. **第一步**（紧急修复）：仅改 `completeFireAndUpdateSchedule` 返回 `boolean`，不改架构
2. **第二步**（Per-Fire）：评估是否保留 `completeFireAndUpdateSchedule` 还是完全内联

---

## 7. 遗留问题清单（修正后）

| # | 问题 | 严重度 | 归属 | 状态 |
|---|------|--------|------|------|
| 1 | `completeFireAndUpdateSchedule` 返回 void → 指标/告警在写入失败后误触发 | 🔴 P0 | Plan 301 follow-up | ❌ |
| 2 | `failFireWithoutSchedule` 用 `updateEntityDirectly`，版本冲突抛异常 | 🟡 P1 | Plan 301 follow-up | ❌ |
| 3 | `updateRetryRecordId` 用 `updateEntityDirectly`，版本冲突抛异常 | 🟡 P1 | Plan 301 follow-up | ❌ |
| 4 | Per-Fire 事务消除 `orm_readonly()` 依赖（当前工作正常，长期脆弱） | 🟢 P3 | 架构改进 | ❌ |
| 5 | 跨 Fire Schedule 干扰（初稿声称，已证实不存在） | — | 初稿错误 | ✅ 已证伪 |

---

## Open Questions

- [x] `completeFireAndUpdateSchedule` 能否直接返回 `boolean`？→ 可以，这是最小修复方案
- [x] `loadFire(String)` 接口是否存在？→ 已存在（`JobFireStoreImpl:205-207`），无需新增
- [x] `AbstractBatchScanner.doScan()` 是否有 Session/Transaction 注解？→ 无（仅 `scanBatch()` 在子类有 `@SingleSession`）
- [x] ORM 在成功 flush 后是否在内存中递增版本？→ 是（`incOptimisticLockVersion` 第 374 行），跨 Fire 干扰不成立
- [x] ORM 在成功 flush 后是否清除脏标志？→ 是（`persisterPostUpdate` → `orm_clearDirty()` 第 1160 行）
- [ ] `cancelFire` 的调用上下文是否在 `@SingleSession` 中？→ 需验证（与 Completion 不同路径，可能不受影响）
- [ ] `IJobFireStore` 是否需要标注每个方法预期的 Session/Transaction 模式？→ 建议在接口文档中注明

## References

- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`
- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`
- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`
- `nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java`
- `ai-dev/analysis/2026-07/2026-07-18-nop-job-transaction-analysis.md`
- `ai-dev/plans/301-nop-job-complete-fire-transaction-session-bugs.md`
- `docs-for-ai/02-core-guides/concurrency-and-transactions.md`
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:370-377, 470-489, 504-520`
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:1157-1161`
