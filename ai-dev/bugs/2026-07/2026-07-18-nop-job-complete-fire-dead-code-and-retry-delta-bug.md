# 2026-07-18 nop-job `completeFireAndUpdateSchedule` 方法体死代码 + 乐观锁重试 readonly 崩溃

## Problem

`JobFireStoreImpl.completeFireAndUpdateSchedule()` 在 `@SingleSession` 调用链（生产环境所有路径：`JobCompletionProcessorImpl`、`JobTimeoutCheckerImpl`）中，方法体实质上是**死代码**：

```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
    NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());  // ← 返回 @SingleSession 缓存的同一对象
    if (JobStatusHelper.isTerminalFire(currentFire.getFireStatus())) {           // ← 调用方已设置为 SUCCESS/FAILED/TIMEOUT
        return;                                                                  // ← 永远走到这里！
    }
    // ↓ 以下代码从不执行 ↓
    if (!fireDao().tryUpdateWithVersionCheck(fire)) { return; }
    // ... schedule 更新重试循环 ...
}
```

同时，乐观锁重试循环中 `tryUpdateWithVersionCheck(schedule)` 在版本冲突时设置 `orm_readonly(true)` 副作用，`orm_unload()` 不清除此标志，导致后续 setter 抛出 `ERR_ORM_ENTITY_IS_READONLY`（Bug B）。`cancelFire` 有完全相同的问题。

## Diagnostic Method

- **诊断难点**：代码结构"看起来正确"——`requireEntityById` 看起来应该从 DB 重新读取，`@Transactional(REQUIRES_NEW)` 看起来应该开启独立会话。不追踪 Nop ORM 的 `OrmSessionRegistry` 和 `@SingleSession` 拦截器细节无法发现问题。
- **被排除的假设**：认为 `REQUIRES_NEW` 会创建新 Session（实际只创建新数据库事务）；认为 `requireEntityById` 总是读 DB（实际检查 Session 缓存优先）。
- **决定性证据**：
  1. `OrmSessionRegistry.instance().get(sessionFactory)` 返回 `@SingleSession` 开启的已有 Session
  2. `OrmSessionImpl._makeProxy()` 在 `cache.get(entityName, id)` 命中时直接返回缓存实体
  3. 调用方 `tryCompleteFireAndGetStatus()` 在第 173 行 `fire.setFireStatus(finalFireStatus)` 修改了该缓存实体
  4. `isTerminalFire(SUCCESS)` = true → 第 124 行 return

**Bug B 诊断**：`activeDelta = schedule.getActiveFireCount() - origActiveFireCount` 中，`schedule.getActiveFireCount()` 是调用方的本地值（原始值减 1 后的结果），`origActiveFireCount` 是重试循环中从 DB 重新读取的 baseline。当并发线程将 DB baseline 恰好修改为与调用方本地值相同（如都变为 4）时，delta 为 0，业务意图丢失。

## Root Cause

### Bug A：Session 与事务生命周期解耦

Nop ORM 中 `@Transactional(REQUIRES_NEW)` 只创建新数据库事务，**不创建新 ORM Session**。`@SingleSession` 单独管理 Session 生命周期（通过 `OrmSessionRegistry`）。当 `@SingleSession` 调用 `REQUIRES_NEW` 方法时：

- `requireEntityById` → `orm().get()` → `runInSession()` → 复用 `@SingleSession` 的 Session → 返回 Session 缓存的同一 Java 对象
- 该对象已被调用方修改 → `isTerminalFire` 返回 true → 方法体短路

实际持久化发生在 `@SingleSession` 拦截器的无事务 `session.flush()` 中。

### Bug B：重试循环复用 readonly-tainted 实体

`tryUpdateWithVersionCheck(schedule)` 在版本冲突后通过 `checkUpdateResult` 设置 `orm_readonly(true)`。retry loop 随后对同一 `schedule` 实体调用 setter，`OrmEntity.checkReadonly()` 抛 `ERR_ORM_ENTITY_IS_READONLY`。

`orm_unload()` 虽设置 state=PROXY 并清除 initedProps，但**不清除 `readonly` 标志位**（`OrmEntity.java:227-229` `orm_readonly(boolean)` 方法实现有 bug：始终设置为 true 忽略参数）。因此 `orm_unload()` + reload 后实体仍然是只读的。

**补充发现**：`cancelFire` 的 retry loop（`JobFireStoreImpl.java:233-248` 旧版）有完全相同的模式——`tryUpdateWithVersionCheck(schedule)` 在循环内第一次迭代设置 readonly，第二次迭代的 `setVersion` 抛 `ERR_ORM_ENTITY_IS_READONLY`。

**delta 计算正名**：原代码的 delta 计算（调用方目标值 − 初始 baseline 值）本身是正确的。唯一问题是 delta 被应用到了 readonly-tainted 的 `schedule` 参数而非干净的 `baseline` 实体。

### Bug C：超时流程状态不一致

`JobTimeoutCheckerImpl.tryMarkDispatchTimeout` 在 `completeFireAndUpdateSchedule` 短路返回后（no-op），继续用 `taskStore.updateTask()`（独立 `REQUIRES_NEW` 事务）将 tasks 标记为 CANCELED，但 fire 在 DB 中仍为 RUNNING。

## Fix

### Bug A 修复（方案 A — 最小改动）

移除 `requireEntityById` 二次校验的短路逻辑，让 `tryUpdateWithVersionCheck(fire)` 在 `REQUIRES_NEW` 事务中真实执行：

```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
    if (!fireDao().tryUpdateWithVersionCheck(fire)) {
        return; // 版本冲突，重试将在下个扫描周期自动进行
    }
    // ... schedule 更新重试循环 ...
}
```

**注意**：此修复依赖 `fire` 实体包含的 version 值为 SQL WHERE 子句的一部分。由于 `requireEntityById` 不再执行，fire 的 version 值取决于调用方提供的实体。在 `@SingleSession` 路径下，fire 是从查询加载的实体，version 是最新的，因此 `tryUpdateWithVersionCheck` 在无并发时会成功。

### Bug A + B + C 联合修复

简化 `completeFireAndUpdateSchedule` 和 `cancelFire`：**移除 retry loop**，改为单次 `tryUpdateWithVersionCheck`。

**核心发现（@SingleSession 限制定理）**：在 `@SingleSession` 上下文中，`requireEntityById` **永远**通过 `OrmSessionImpl._makeProxy` 返回 Session 缓存中的同一条目（`cache.get(entityName, id)` 在 line 795 命中即返回）。这意味着 retry loop 的 reload 操作永远不能获得新鲜实体。retry loop 在 `@SingleSession` 下**注定失败**——无论重试多少次，baseline 总是同一只读对象。

```java
// completeFireAndUpdateSchedule（最终简化版）
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
    // Bug A: 无 requireEntityById 短路
    if (!fireDao().tryUpdateWithVersionCheck(fire)) {
        return;
    }
    // Bug B: 无 retry loop；schedule 已被调用方修改（dirty），flush 会包含所有 dirty 属性
    scheduleDao().tryUpdateWithVersionCheck(schedule);
}
```

```java
// cancelFire schedule 部分（简化版）
schedule.setActiveFireCount(Math.max(0, defaultInt(schedule.getActiveFireCount()) - 1));
schedule.setTotalFireCount(Math.max(0, defaultLong(schedule.getTotalFireCount()) + 1));
schedule.setFailFireCount(Math.max(0, defaultLong(schedule.getFailFireCount()) + 1));
// ... 更多属性设置 ...
if (!scheduleDao().tryUpdateWithVersionCheck(schedule)) {
    LOG.warn("nop.job.cancel.schedule-update-conflict:fireId={}", fire.getJobFireId());
}
```

### Bug C 修复

在 `JobTimeoutCheckerImpl.tryMarkDispatchTimeout` 中，`completeFireAndUpdateSchedule` 返回后检查 `fire.orm_readonly()`。`tryUpdateWithVersionCheck` 在版本冲突时通过 `checkUpdateResult` 设置此标记。如果为 true（= fire 未实际更新），跳过 task 取消逻辑，避免 DB 中 RUNNING fire + CANCELED tasks 的不一致：

```java
fireStore.completeFireAndUpdateSchedule(fire, schedule);
if (fire.orm_readonly()) {
    return; // skip task cancellation — fire wasn't actually timed out
}
```

### Bug C 修复

在 `JobTimeoutCheckerImpl` 中，`completeFireAndUpdateSchedule` 返回后检查 fire DB 状态。如果状态与期望不一致（乐观锁冲突导致无操作），跳过后续的 task 取消逻辑。或者，将 task 更新移到 `completeFireAndUpdateSchedule` 方法内，与 fire+schedule 在同一 `REQUIRES_NEW` 事务中原子执行。

## Tests

已写入 `TestJobFireStoreRace.java`（10 tests, 0 failures, 0 errors）：

1. **`testCompleteFireDoesNotOverwriteCanceledFire`**（已有）— 验证 cancelFire 后 completeFire 不覆盖。
2. **`testCancelFireDetectsVersionMismatch`**（已有）— 验证 cancelFire 的乐观锁版本检查。
3. **`testCompleteFireConvergesDespiteScheduleModifications`**（更新）— 原名 `testCompleteFireThrowsOnScheduleVersionConflict`，修复后 retry loop 正确加载最新 version，不再需要重试 5 次。
4. **`testTryUpdateWithVersionCheckOnDetachedReturnsFalse`**（新增）— 验证 `tryUpdateWithVersionCheck` 不抛异常，返回 false。
5. **`traceCompleteFireErrorCode()`**（工具方法）— 手动运行时输出明确异常 `ERR_ORM_ENTITY_IS_READONLY` + 堆栈。

### 关键实测发现（2026-07-18）

| 发现 | 结论 | 调整 |
|------|------|------|
| `tryUpdateWithVersionCheck` 在版本冲突时不抛异常 | **Bug D 假说被证伪** | 移除对 `tryUpdateWithVersionCheck` 抛异常的假设 |
| `orm_unload()` 后 setter 不抛异常（因为 `fullyLoaded` 未清除） | **Bug E 假说被证伪** | retry loop 在 `orm_unload()` + setter 阶段不抛异常 |
| `traceCompleteFireErrorCode()` 确认异常为 `ERR_ORM_ENTITY_IS_READONLY` | **retry loop 崩溃在 readonly setter** | 修复方案从"修正 delta 计算"改为"不复用 readonly-tainted 实体" |
| Bean 连接确认 | **Bug A 生产环境成立** | Bug A 修复仍是首要任务 |
| `cancelFire` 有相同的 readonly 重试问题 | **Bug B 在 cancelFire 中同样存在** | 附带修复 cancelFire 的 retry loop |
| 原 delta 计算（目标值 − 初始 baseline）本身正确 | **Bug B 根因重新解释** | 保留 delta 计算方式，改为对 baseline 实体操作 |

### Bug D 正名

**原假说**：`tryUpdateWithVersionCheck(schedule)` 抛出 `OrmException(nop.err.orm.update-entity-not-found)` 使 retry loop 不可达。

**实际**：`tryUpdateWithVersionCheck` 通过 `entity.orm_disableVersionCheckError(true)` 设置旗标，`checkUpdateResult` 在 `EntityPersisterImpl.java:506-515` 正确检查此旗标：为 true 时**只写 log + 标记 readonly，不抛异常**。方法返回 `!entity.orm_readonly()` = `false`。

**正名**：`tryUpdateWithVersionCheck` 行为正确。retry loop 是可进入的。

## Affected Files

- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`（`completeFireAndUpdateSchedule`、`cancelFire`）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`（`tryMarkDispatchTimeout`）
- `nop-job/nop-job-dao/src/test/java/io/nop/job/dao/store/TestJobFireStoreRace.java`（新增测试用例）

## Notes For Future Refactors

- Nop ORM 的 Session 与事务解耦设计是刻意的，不是 bug。使用 `@Transactional(REQUIRES_NEW)` 时必须意识到**不创建新 Session**。如果需要新 Session+新事务，用 `AbstractDaoHandler.runLocal()` 模式（显式嵌套 `runInNewSession { runInTransaction(REQUIRES_NEW) { ... } }`）。
- `@SingleSession` + `REQUIRES_NEW` 模式的安全使用条件：Store 方法内 `requireEntityById` 返回的实体**未被调用方在同一 Session 中修改过**。否则需要显式 `orm_unload()` 或在方法内开新 Session。
- 如果未来将 Store 方法改为开新 Session（方案 B），需要注入 `IOrmTemplate` 并使用 `runInNewSession`。这会增加每次调用的 Session 开销，但与 `@Transactional(REQUIRES_NEW)` 结合使用可避免死代码问题。
