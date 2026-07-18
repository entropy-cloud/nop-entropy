# nop-job 事务与数据一致性深度分析

> Status: closed (findings integrated into plan 301)
> Date: 2026-07-18
> Scope: nop-job-dao（JobFireStoreImpl, JobScheduleStoreImpl, JobTaskStoreImpl）、nop-job-coordinator（JobCompletionProcessorImpl, JobTimeoutCheckerImpl）
> Conclusion: 3 个正确性 bug（Bug A, B, C）已确认并修复。Bug A 生产成立。Bug B 根因为 readonly 副作用 + @SingleSession 缓存不可刷新（retry loop 无法工作）。Bug C 通过 `fire.orm_readonly()` 跳过状态不一致的 task 取消。所有测试通过。

## Context

基于 `completeFireAndUpdateSchedule` 方法的代码审查，分析 nop-job 在事务边界、乐观锁、Session 缓存一致性方面的设计问题。

**用户原疑点**：
1. `requireEntityById` 不会重新读取数据（Session 缓存返回旧值）
2. 乐观锁失败静默返回，不通知调用方
3. job 状态更新应与外部业务数据在同一事务，不应新建 `REQUIRES_NEW`
4. 整体设计混乱

**发现摘要**：
- 3 个正确性 bug 经源码确认成立
- 1 个架构根因（Session 与事务解耦生命周期）是所有问题的源头
- 现有 `docs-for-ai/02-core-guides/concurrency-and-transactions.md` 已部分记录，但未揭示根本问题

---

## 0. 关键发现：Session 与事务完全解耦

Nop ORM 的两层管理是**完全独立的**：

| 概念 | 注册表 | 生命周期触发 |
|------|--------|-------------|
| ORM Session | `OrmSessionRegistry`（per-`IContext`） | `@SingleSession` / `OrmTemplate.runInSession()` |
| 数据库事务 | `TransactionRegistry`（per-`IContext`） | `@Transactional` / `TransactionTemplate.runInTransaction()` |

**`@Transactional(propagation = REQUIRES_NEW)` 不会创建新 Session。** 它只创建新数据库事务。ORM Session 被 `@SingleSession` 管理，在 `OrmSessionRegistry` 中按 `sessionFactory` 索引，与事务无任何生命周期耦合。

**影响**：当 `completeFireAndUpdateSchedule`（`REQUIRES_NEW`）被 `scanBatch()`（`@SingleSession`）调用时，`requireEntityById` 返回的是 **`@SingleSession` Session 缓存中的同一个 Java 对象**，而非 DB 最新值。

---

## 1. Bug A：`completeFireAndUpdateSchedule` 方法体实质上是死代码

### 调用链

```
JobCompletionProcessorImpl.scanBatch()           ← @SingleSession 开启新 Session
  └─ fetchRunningFires()                          ← 从 DB 加载实体，放入 Session 缓存
  └─ tryCompleteFireAndGetStatus(fire)            ← fire 是 Session 缓存中的实体
       ├─ fire.setFireStatus(finalFireStatus)     ← 修改 in-memory（Session 缓存中同步变更）
       ├─ schedule.setActiveFireCount(...)         ← 修改 in-memory
       └─ fireStore.completeFireAndUpdateSchedule(fire, schedule)  ← @Transactional(REQUIRES_NEW)
```

### 为什么是死代码

```java
// JobFireStoreImpl.java:122-124
NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
if (JobStatusHelper.isTerminalFire(currentFire.getFireStatus())) {
    return;  // ← 永远走到这里！
}
```

- `requireEntityById` → `orm().get(entityName, id)` → `runInSession(session -> session.get(...))` → 复用 `@SingleSession` 的 Session → `OrmSessionImpl.get()` → `makeProxy()` → `cache.get(entityName, id)` 命中 → **返回 `fire` 同一对象**
- `currentFire == fire`（同一 Java 引用）
- 调用方已在 `tryCompleteFireAndGetStatus` 第 173 行设置了 `fire.setFireStatus(finalFireStatus)`（SUCCESS/FAILED/TIMEOUT）
- `isTerminalFire(SUCCESS)` = true
- 第 124 行 return

**结果**：第 127-128 行的 `tryUpdateWithVersionCheck(fire)` 和第 131-165 行的 schedule 更新循环**永远不执行**。

### 实际持久化路径

```java
// AbstractBatchScanner.doScan()
//   → scanOnce() → scanBatch() → ... → 返回
//   → @SingleSession 拦截器：
//      OrmTemplateImpl.runInNewSession():
//        session.flush()     ← 在此处 flush 所有 in-memory 修改到 DB！
//        session.close()
```

真正的持久化发生在 `@SingleSession` 拦截器的 `session.flush()`。该 flush **没有数据库事务包裹**（`@SingleSession` 不开启事务），每个 UPDATE 语句被 JDBC auto-commit。

### 后果

| 问题 | 说明 |
|------|------|
| 无原子性 | Fire 和 Schedule 的更新通过多个 auto-committed UPDATE 分别执行，无原子保证 |
| 无版本检查 | Session flush 是否对 in-memory 修改过的实体做 version 检查？如果是，flush 失败后已提交的修改不能回滚 |
| 死代码迷惑 | 方法体从未执行，阅读代码的人会被误导 |
| 无调回通知 | 调用方认为 `completeFireAndUpdateSchedule` 已提交修改，继续触发 metrics/alarm，但实际上修改是后续 flush 完成的 |

---

## 2. Bug B：乐观锁重试循环中的 delta 不一致

### 场景：同一 Schedule 的多个 Fire 并发完成

假设 Schedule S 的 `activeFireCount = 5`：
- Fire 1 和 Fire 2 同时完成
- `JobCompletionProcessorImpl` 是单线程顺序处理的，但在多 Coordinator 实例下 `JobTimeoutCheckerImpl` 可能并发

### 代码路径

```java
// JobCompletionProcessorImpl.tryCompleteFireAndGetStatus() line 187
schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
```

调用方在线程 A 中：`schedule.activeFireCount = 4`（原始 5 - 1）

```java
// JobFireStoreImpl.completeFireAndUpdateSchedule() lines 133-146
int origActiveFireCount = baseline.getActiveFireCount(); // 重新读取的 baseline
int activeDelta = schedule.getActiveFireCount() - origActiveFireCount;
// ...
scheduleDao().tryUpdateWithVersionCheck(schedule); // UPDATE
```

### 错误推导

| 步骤 | 场景 | `activeDelta` | 结果 |
|------|------|------|------|
| 初始 | Thread A 读 Schedule，`activeFireCount=5` | - | - |
| Thread A 设置 | `schedule.activeFireCount = 4`（5-1） | `4 - currentBaseline` | 取决于并发 |
| 无并发 | `baseline.activeFireCount = 5` | `4 - 5 = -1` | ✅ 正确：`5 + (-1) = 4` |
| Thread B 先更新 | `baseline.activeFireCount = 4`（B 已减到 4） | `4 - 4 = 0` | ❌ **应为 -1，得 0** → `activeFireCount` 应减到 3 但保持 4 |

**根因**：`activeDelta = schedule.getActiveFireCount() - origActiveFireCount` 中的 `schedule.getActiveFireCount()` 是调用方的"原始值 - 1"，而 `origActiveFireCount` 是重试循环中重新读取的 baseline。delta 不是固定的业务意图（减 1），而是**调用方的本地值**（4）与 **当前 DB 值**（4）的差。当调用方的本地值巧合等于被并发修改后的 DB 值时，delta 为 0，业务意图丢失。

**正确的做法**：固定 delta = -1（不管 baseline 是什么，始终减 1）：
```java
schedule.setActiveFireCount(baseline.getActiveFireCount() - 1);
```

### 实际影响

`JobCompletionProcessorImpl` 是单线程顺序处理的（一个线程内顺序遍历 fires），所以在同进程内不会并发修改同一个 schedule 的计数器。但在**多 Coordinator 分布式部署**下，`JobTimeoutCheckerImpl` 和 `JobCompletionProcessorImpl` 可能同时修改同一个 schedule 的计数器，触发此 bug。

---

## 3. Bug C：超时流程中的状态不一致

### 调用链

```java
// JobTimeoutCheckerImpl.tryMarkDispatchTimeout() line 319-362
fire.setFireStatus(FIRE_STATUS_TIMEOUT);         // 1. in-memory 修改 fire
// ...
schedule.setActiveFireCount(...);                // 2. in-memory 修改 schedule
// ...
fireStore.completeFireAndUpdateSchedule(fire, schedule);  // 3. REQUIRES_NEW → 死代码! (Bug A)
// ↓ 继续执行
List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
for (NopJobTask task : tasks) {
    // ...
    taskStore.updateTask(task);                  // 4. 另一个 REQUIRES_NEW
}
```

### 问题流程

1. 第 3 步的 `completeFireAndUpdateSchedule` 因 Bug A 死代码，不更新 fire 和 schedule
2. 第 4 步的 `taskStore.updateTask(task)` 在**另一个独立事务**中提交 task 的 CANCELED 状态
3. Fire 在 DB 中保持 RUNNING，但 tasks 已被标记为 CANCELED
4. 后续 `JobCompletionProcessor` 重新扫描时，看到所有 tasks 已终态，但没有 pending tasks，将 fire 判定为 CANCELED
5. Schedule 的计数器（`activeFireCount`、`failFireCount`）最终通过 session flush 更新，但 fire status（TIMEOUT）被 flush 覆盖为后续的 CANCELED

**结果**：Fire 的最终状态是 CANCELED 而非 TIMEOUT，但 timeout 流程的 alarm 已经触发。最终状态与已触发的告警不一致。

---

## 4. 架构根因：Session 与事务生命周期解耦

### Nop ORM 的设计选择

Nop ORM 选择将 Session 生命周期与事务生命周期**完全分离**：
- `@SingleSession` 管理 Session（一级缓存、identity map）
- `@Transactional` 管理数据库事务

这是**有意的设计**（`concurrency-and-transactions.md` 模式二明确说明），但在 nop-job 的使用场景中产生了意外后果：

### 当前模式

```
@SingleSession (Session A)     ← 无事务
  └─ fetchRunningFires          ← 查询，实体进入 Session A 缓存
  └─ fire.setStatus(...)        ← 修改 Session A 缓存中的实体
  └─ @Transactional(REQUIRES_NEW) (Txn 1)  ← 新事务，但 Session A 不变
       └─ requireEntityById()   ← 返回 Session A 缓存的同个对象
       └─ 代码路径被短路 (Bug A)
  └─ [更多操作]
session.flush()                 ← 自动提交到 DB（auto-commit）
```

### 安全做法对比

Hibernate 的典型做法：**一个事务一个 Session**（Session-per-Request / Session-per-Transaction）。JPA 的 `EntityManager` 生命周期通常绑定到事务。

### 为什么 nop-job 需要 `REQUIRES_NEW`

`scanBatch()` 没有事务，所以 Store 方法需要开事务来保证单次操作的原子性。但 `REQUIRES_NEW` 遇到 `@SingleSession` 时：

| 期望 | 实际 |
|------|------|
| Store 方法在独立事务中执行更新 | ✅ 是的，Txn 1 是独立事务 |
| Store 方法读取的是 DB 最新数据 | ❌ 从 `@SingleSession` 缓存读取，可能是脏数据 |
| Store 方法的更新是原子提交 | ❌ 主调用者使用 session flush，无事务包裹 |
| 调用者知道 Store 方法是否成功 | ❌ Store 方法静默返回 void，不暴露结果 |

---

## 5. 对其他 Store 方法的验证

并非所有 Store 方法都存在 Bug A。取决于 `requireEntityById` 返回的实体是否被调用方修改过：

| Store 方法 | 上游修改实体？ | Bug A 影响？ |
|-----------|---------------|------------|
| `completeFireAndUpdateSchedule` | ✅ 调用方修改了 fire 和 schedule | ❌ 死代码短路 |
| `insertTasksAndMarkFireDispatching` | ✅ 调用方可能修改了 fire（`setFireStatus` 前有二次校验，以 `requireEntityById` 返回为准，是正确做法） | 第 93 行 `requireEntityById` 返回原始 fire，`status != DISPATCHING` 时正确跳过。但如果 fire 已被修改为 RUNNING，则 `getFireStatus() == DISPATCHING` 不成立，正确跳过。**此处正确**。 |
| `cancelFire` | ❌ 调用方传 `jobFireId` 字符串，不传实体 | ✅ 无问题（从未使用 session 缓存中修改过的实体） |
| `revertDispatchingFireToWaiting` | ✅ 调用方传 fire 实体，但方法内重新 `requireEntityById` 做二次校验 | 类似 `insertTasksAndMarkFireDispatching`，以重新读取的 `currentFire` 为准。**此处正确**。 |
| `tryLockFiresForDispatch` | ✅ 调用方传 fires 列表，不重新读取 | 直接 `tryUpdateManyWithVersionCheck(fires)`，没有先读。**依赖调用方提供的实体版本正确。** |

**结论**：只有 `completeFireAndUpdateSchedule` 受 Bug A 影响，因为它做 `requireEntityById` 二次校验的同时，传入的实体已被调用方修改。

---

## 6. 与现有文档的关系

`docs-for-ai/02-core-guides/concurrency-and-transactions.md` 模式四已正确说明：

> "如果实体在同一 Session 中已被加载过，`requireEntityById` 返回缓存对象而非 DB 最新值"

但这个文档强调的"问题"是 retry loop 中 baseline 不刷新（需要 `orm_unload()`），而 **Bug A 的核心问题不在 retry loop，在于方法入口处的第一个 `requireEntityById`**。文档没有覆盖这个场景。

**文档遗漏**：
- 没有说明 `@SingleSession` 调用 `REQUIRES_NEW` 方法时的 Session 复用问题
- 没有说明 `REQUIRES_NEW` 不自动创建新 Session
- 没有说明 session flush 发生在 `@SingleSession` 拦截器中，且无事务包裹
- 没有揭示 `completeFireAndUpdateSchedule` 的代码实际是死代码

---

## 7. 修复建议

### 方案 A：让 Store 方法真实执行（最小改动）

去掉 `requireEntityById` 的二次校验短路逻辑，改为直接 `tryUpdateWithVersionCheck`：

```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
    // 移除此行：NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
    // 移除此行：if (JobStatusHelper.isTerminalFire(currentFire.getFireStatus())) { return; }
    
    // 直接尝试乐观锁更新 fire
    if (!fireDao().tryUpdateWithVersionCheck(fire)) {
        return; // 或抛异常让调用方重试
    }
    // ... 继续 schedule 更新 ...
}
```

但这样依赖 fire 实体的 version 字段正确（由调用方保证从 DB 正确读取）。

**问题**：调用方从 `@SingleSession` 缓读取的 fire，version 可能不是最新的。`tryUpdateWithVersionCheck` 在 `REQUIRES_NEW` 中使用同一个 Session，UPDATE 时会使用实体上的 version 值。如果 version 是旧的，UPDATE 会失败（0 rows affected），entity 变 `orm_readonly()`。

### 方案 B：Store 方法内开新 Session（侵入性改造）

```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
    // 在新 Session 中重新加载 fire 和 schedule
    NopJobFire freshFire = orm().runInNewSession(session -> {
        return fireDao().requireEntityById(fire.getJobFireId());
    });
    // 现在 freshFire 是从 DB 读取的独立副本（不在 @SingleSession 缓存中）
}
```

但 `OrmTemplate.runInNewSession()` 内嵌在 `REQUIRES_NEW` 中，需要注入 `IOrmTemplate`。

### 方案 C：消除 @SingleSession（架构级改造）

将 Scanner 从 `@SingleSession` + `REQUIRES_NEW` 模式改为每个 Store 调用各自管理 Session：

```
// 现在：每个 scanBatch() 一个 Session
@SingleSession
scanBatch() {
    requireEntityById() → 同个 Session 缓存
    tryUpdateWithVersionCheck() → 同个 Session
    session.flush() → auto-commit
}

// 改造后：每次操作独立 Session+事务
scanBatch() {  // 移除 @SingleSession
    fireStore.completeFireAndUpdateSchedule(fire, schedule)
    // 方法内部：开新 Session + 开新事务
    // 用 runLocal() 模式：runInNewSession { runInTransaction(REQUIRES_NEW) { ... } }
}
```

这需要 store 方法自己管理 Session+事务（用 `AbstractDaoHandler.runLocal()` 模式）。但会增加每次调用的 Session 开销。

### 推荐：方案 A + 文档修正

方案 A 风险最低：
1. 移除 `requireEntityById` 二次校验
2. 让 `tryUpdateWithVersionCheck` 在 `REQUIRES_NEW` 的独立事务中真正执行
3. 如果 version 过期导致 UPDATE 失败，返回 false 让调用方重试（下个扫描周期）
4. 修正 `docs-for-ai/` 文档，明确说明 `REQUIRES_NEW` 下的 Session 复用行为

**同时修复 bug B**：固定 delta = -1，不依赖调用方本地值。

---

## 8. 对用户原疑点的逐一回答

### 疑点 1：`requireEntityById` 不会重新读取数据

✅ **正确。** 在 `@SingleSession` + `REQUIRES_NEW` 模式下，`requireEntityById` 返回 Session 缓存的同一实例。文档 `concurrency-and-transactions.md` 模式四已说明此事，但未说明 `REQUIRES_NEW` 不创建新 Session。

### 疑点 2：乐观锁失败静默返回

✅ **正确。** 第 127-128 行 `if (!fireDao().tryUpdateWithVersionCheck(fire)) { return; }` 静默返回。但调用方不知道更新失败，继续触发 metrics/alarm。**更严重的是**：由于 Bug A，这个代码路径根本不会执行。

### 疑点 3：为什么新建事务，不在同一事务中

✅ **正确的问题方向。** 当前设计强迫 Store 方法用 `REQUIRES_NEW` 是因为调用方 `@SingleSession` 不提供事务。但 `REQUIRES_NEW` 的 Session 复用导致 `requireEntityById` 返回脏缓存。正确的做法是让 Store 方法内自行管理 Session+事务，或者让调用方提供已开事务的上下文。

### 疑点 4：整体设计混乱

✅ **合理判断。** 现有设计存在：
- Session 与事务解耦导致意料之外的缓存问题
- `requireEntityById` 二次校验收回 Session 缓存的过期对象
- 实际的持久化发生在无事务的 session flush 中
- 方法体实质上是死代码
- 重试循环有并发正确性 bug

---

## Open Questions

- [ ] ~~验证 `OrmSessionImpl.flush()` 是否对 in-memory 修改过的实体做 version 检查，还是直接 UPDATE 覆盖~~ → **2026-07-18 实测已回答**：`@SingleSession` 的 `session.flush()` 确实做版本检查，但无事务包裹，失败后导致部分更新已提交。
- [ ] ~~验证 `@SingleSession` 的 `session.flush()` 是否在 `OrmTemplateImpl.runInNewSession()` 的 try-finally 中执行~~ → **2026-07-18 代码确认**：是。
- [ ] `cancelFire` 和其他 Store 方法的调用来源是否走 `@SingleSession` 路径
- [ ] `activeFireCount` 是否真的需要在 schedule 表上维护，还是改为统计查询（`COUNT(*) WHERE fireStatus != terminal`）更可靠
- [ ] 分布式多 Coordinator 场景下，并发修改 schedule 计数器是否触发 Bug B，现有测试是否覆盖

---

## 9. 2026-07-18 实测发现

### 9.1 Bean 连接验证：Bug A 生产成立

通过读取 `orm-defaults.beans.xml` 确认：

| Bean | 引用 |
|------|------|
| `nopOrmTemplate` (id=`nopOrmTemplate`, class=`OrmTemplateImpl`, **singleton**) | 所有 `@Inject IOrmTemplate` 注入此实例 |
| `nopDaoProvider` (id=`nopDaoProvider`) | `constructor-arg ref="nopOrmTemplate"` |
| `nopSingleSessionMethodInterceptor` | `constructor-arg ref="nopOrmTemplate"` |
| 所有 `OrmEntityDao` 实例 | 创建时使用 `DaoProvider.ormTemplate`（即 `nopOrmTemplate`） |

因此 `SingleSessionMethodInterceptor` 和所有 DAO 共享**同一个 `OrmTemplateImpl` 实例**。在 `@SingleSession` 方法中 `requireEntityById` 调用 `orm().runInSession(...)`，`OrmTemplateImpl.runInSession` 检查 `OrmSessionRegistry` → 找到已绑定的 Session → **复用**。结论：Bug A 在**生产环境成立**。

### 9.2 Bug D：`tryUpdateWithVersionCheck` 不会抛异常（正名）

**原假说**：`tryUpdateWithVersionCheck` 在版本冲突时抛出 `OrmException(nop.err.orm.update-entity-not-found)`，使 retry loop 无法进入。

**实际验证**：此假说**不成立**。

```text
// 实测输出（testTryUpdateWithVersionCheckOnDetachedReturnsFalse）：
nop.err.orm.update-entity-not-found:entity=NopJobSchedule[...,status=MANAGED,dirty,fullyLoaded]
// → INFO 级别日志，不是异常
// → tryUpdateWithVersionCheck 返回 false（！entity.orm_readonly()）
```

`checkUpdateResult` 在 `EntityPersisterImpl.java:506-515` 确实检查 `entity.orm_disableVersionCheckError()`：为 true 时只写日志+标记 readonly，不抛异常。`tryUpdateWithVersionCheck` 通过 `entity.orm_disableVersionCheckError(true)` 在源码级别正确设置此旗标。

### 9.3 `orm_unload()` 后 setter 不抛异常（ORM 行为澄清）

**原猜测**：`orm_unload()` 将 state 设为 PROXY 后，setter 会触发 `requireEnhancer()` 抛 `ERR_ORM_SESSION_CLOSED`。

**实际验证**：**不抛异常**。因为 `orm_unload()` 不清除 `fullyLoaded` 标志位。`forcePropLoaded(propId)` 方法首先检查 `orm_propLoaded(propId)`，而 `fullyLoaded == true` 时该方法返回 true，**短路返回**，不会调用 `requireEnhancer()`。

```java
// OrmEntity.java:454-462
protected void forcePropLoaded(int propId) {
    if (state == OrmEntityState.TRANSIENT || state == OrmEntityState.SAVING || state == OrmEntityState.MISSING)
        return;
    if (orm_propLoaded(propId))  // ← fullyLoaded=true 时直接返回
        return;
    requireEnhancer().internalLoadProperty(this, propId);
}
```

### 9.4 `traceCompleteFireErrorCode()` 确认异常为 `ERR_ORM_ENTITY_IS_READONLY`

`traceCompleteFireErrorCode()` 输出确认 retry loop 抛出的异常不是 `ERR_JOB_FIRE_STATUS_CONFLICT`，而是 `nop.err.orm.entity-is-readonly`：

```text
12:33:45.237 [main] INFO io.nop.orm.persister.EntityPersisterImpl -- nop.err.orm.update-entity-not-found:entity=NopJobSchedule[...,status=MANAGED,dirty,readonly,fullyLoaded]
// 后续 setter 调用 → checkReadonly() → ERR_ORM_ENTITY_IS_READONLY
```

**触发路径**：
1. `tryUpdateWithVersionCheck(schedule)` 在 retry loop 外第一次尝试（代码第 122 行，旧版）→ 版本冲突 → `checkUpdateResult(0, schedule)` → `orm_readonly(true)` + 返回 false
2. retry loop 进入 → `schedule.orm_unload()` → 不清理 readonly 标志
3. `schedule.setActiveFireCount(...)` → `checkReadonly()` → **抛出 `ERR_ORM_ENTITY_IS_READONLY`**
4. 这完全绕过了 5 次重试和 `ERR_JOB_FIRE_STATUS_CONFLICT` 的抛出逻辑

**对 Bug B 的重新解释**：Bug B 不是 delta 计算问题，而是 retry loop 复用 readonly-tainted 的 `schedule` 参数。delta 计算（调用方目标值 vs 初始 baseline）本身正确。

### 9.5 `cancelFire` 也有同样的 readonly 重试问题

`cancelFire` 的 retry loop（`JobFireStoreImpl.java:233-248` 旧版）与 `completeFireAndUpdateSchedule` 完全相同的模式：
- 先用 `tryUpdateWithVersionCheck(schedule)` 尝试更新（作为循环体的第一条语句）
- 失败后 `orm_unload()` + reload
- 然后用 `schedule.setVersion(...)` 恢复版本号
- `orm_unload()` 不清理 readonly → `setVersion` 抛 `ERR_ORM_ENTITY_IS_READONLY`

虽然 `cancelFire` 的 `tryUpdateWithVersionCheck` 在循环体内（第一次调用才触发 readonly），但第二次循环迭代必然崩溃。

### 9.6 测试变更说明

- 移除 `testCompleteFireUnderSingleSession_DeadCode_BugA_proof`（无法在单元测试中重现 — 因为 DAO 和注入的 `IOrmTemplate` 在测试中使用不同的 `sessionFactory` key，不共享 Session）
- 移除 `testCompleteFireUnderSingleSession_afterFix_throwsOnScheduleConflict`（同样无法隔离 Session）
- 新增 `testTryUpdateWithVersionCheckOnDetachedReturnsFalse`（确认 Bug D 不成立）
- 新增 `traceCompleteFireErrorCode()` 工具方法用于手动堆栈跟踪
- 原 `testCompleteFireThrowsOnScheduleVersionConflict` → 改为 `testCompleteFireConvergesDespiteScheduleModifications`（修复后 retry loop 加载最新 version，不需要重试 5 次）

### 9.7 修复方案

**问题根因**：`tryUpdateWithVersionCheck` 在版本冲突时设置 `orm_readonly(true)` 作为副作用，而 `orm_unload()` 不清除此标志位。retry loop 复用同一 `schedule` 实体，导致后续 setter 抛 `ERR_ORM_ENTITY_IS_READONLY`。

**修复策略**：retry loop 不直接修改 `schedule` 参数（被 readonly-tainted），而是对一个从 DB 重新加载的 `baseline` 实体应用 delta：

```
completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule):
  1. tryUpdateWithVersionCheck(fire)
  2. baseline = requireEntityById(schedule.id)  // fresh read
  3. Compute deltas: caller_schedule.value - baseline.value
  4. Copy non-counter fields to baseline
  5. Loop(5x):
     a. Apply deltas to baseline
     b. tryUpdateWithVersionCheck(baseline)  // baseline not readonly
     c. If success → return
     d. baseline.orm_unload(); reload baseline
```

`cancelFire` 使用同样的策略，且因其 deltas 固定（−1, +1, +1），计算更简单。

**关键决策**：移除 retry loop（非 baseline 重试）。因为 `@SingleSession` 下 `requireEntityById` **永远返回缓存对象**（`_makeProxy` 先查缓存），retry loop 无论重试多少次都无法获得新鲜数据。schedule 实体的 dirty 属性确保单次 `tryUpdateWithVersionCheck` flush 包含全部修改。`cancelFire` 同理。Bug C 改用 `fire.orm_readonly()` 作为 guard：`completeFireAndUpdateSchedule` 返回后检查此标志位，为 true 表示 fire 未实际更新，跳过 task 取消逻辑。

## References

- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:119-169`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:117-219`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:289-362`
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:336-426, 777-818`
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:204-231`
- `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:238-283`
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/interceptor/SingleSessionMethodInterceptor.java`
- `docs-for-ai/02-core-guides/concurrency-and-transactions.md`
- `ai-dev/analysis/2026-07-02-nop-job-code-quality-remediation-analysis.md`（已有 Issue 12 关于 baseline 缓存刷新问题）
