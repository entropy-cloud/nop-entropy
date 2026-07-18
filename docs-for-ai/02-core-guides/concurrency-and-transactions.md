# 并发与事务模式

本文档说明 Nop 平台中常见的并发控制和事务边界模式，帮助开发者（和审计工具）区分正确的设计模式与真正的 bug。

## 默认规则

1. **BizModel 层不需要显式事务**：`@BizMutation` 已自动包事务，不要再叠加 `@Transactional`。
2. **Store/Dao 层使用 `@Transactional(REQUIRES_NEW)`**：后台调度器（Scanner）通过 Store 层操作数据时，每个 Store 方法开独立事务，保证与 Scanner 的 `@SingleSession` 长事务隔离。
3. **乐观锁是默认并发控制机制**：ORM 实体自带 `version` 字段，通过 `tryUpdateManyWithVersionCheck()` 实现无锁并发。

## 模式一：乐观锁 + 预留信号（fetch → tryLock）

这是 nop-job 调度器使用的核心并发模式。在类似的"多实例竞争同一批资源"场景中应复用此模式。

### 标准流程

```
Scanner 线程（定时触发）
    │
    ▼ 1. fetch（无锁读取）
    │  SELECT ... WHERE status=待处理 AND time <= now
    │  → 返回候选列表（可能有多个实例读到同一批数据）
    │
    ▼ 2. tryLock（乐观锁 + 状态变更）
    │  @Transactional(REQUIRES_NEW)
    │  for each entity:
    │      entity.setNextTime(farFuture)   // 预留信号：推到远未来
    │  dao.tryUpdateManyWithVersionCheck(entities)  // UPDATE WHERE version=?
    │  → 只返回成功锁定的实体
    │
    ▼ 3. 业务处理（仅处理锁定成功的）
    │  for each locked entity: ...
```

### 为什么不是竞态条件？

审计工具可能将步骤 1 和步骤 2 之间的时间窗口标记为"TOCTOU 竞态条件"。实际上：

- **fetch 返回的是候选集，不是锁**。多个实例可以读到相同的数据，这是设计如此。
- **tryLock 通过乐观锁保证只有一个实例能成功**。`tryUpdateManyWithVersionCheck()` 内部执行 `UPDATE ... WHERE version=?`，只有 version 匹配才成功。失败的 UPDATE 不会返回对应实体。
- **预留信号（推远未来时间）确保后续 fetch 不会重复选到**。锁定成功后，该实体的 `nextFireTime` 已变为远未来，其他实例下次扫描时不会选到。
- **冲突处理是显式的**：调用方通过 `dueCount - lockedCount` 计算冲突数，只处理 `locked` 列表。

### 何时适用

- 多实例无主协调器竞争同一批任务
- 不需要严格的"先锁后读"语义
- 接受少量冲突重试（冲突后下次扫描自动重试）

### 参考实现

| 组件 | 文件 | 作用 |
|------|------|------|
| Planner Scanner | `nop-job-coordinator/.../JobPlannerScannerImpl.java` | 扫描到时 Schedule，乐观锁锁定后创建 Fire |
| Dispatcher Scanner | `nop-job-coordinator/.../JobDispatcherScannerImpl.java` | 扫描待分发 Fire，乐观锁锁定后创建 Task |
| Worker Scanner | `nop-job-worker/.../JobWorkerScannerImpl.java` | 扫描待认领 Task，乐观锁锁定后执行 |
| Schedule Store | `nop-job-dao/.../JobScheduleStoreImpl.java` | `tryLockSchedulesForPlan()` — 乐观锁实现 |
| Fire Store | `nop-job-dao/.../JobFireStoreImpl.java` | `tryLockFiresForDispatch()` — 乐观锁实现 |
| Task Store | `nop-job-dao/.../JobTaskStoreImpl.java` | `tryLockTasksForExecute()` — 乐观锁实现 |

## 模式二：@SingleSession + REQUIRES_NEW 隔离

### 问题

后台调度器（Scanner）需要在一个长事务中完成多轮扫描和处理，但每轮处理的 Store 操作需要在独立事务中完成，避免长事务锁定过多资源。

### 解决方案

```
Scanner（@SingleSession 长事务）
    │
    ├─ Store.methodA()  ← @Transactional(REQUIRES_NEW) → 新事务，独立提交
    ├─ Store.methodB()  ← @Transactional(REQUIRES_NEW) → 另一个新事务
    └─ Store.methodC()  ← @Transactional(REQUIRES_NEW) → 另一个新事务
```

- `@SingleSession`：标记 Scanner 的 `doScan()` 方法，保证整个扫描周期使用同一个 ORM Session（不是数据库事务）。
- `@Transactional(REQUIRES_NEW)`：Store 层的每个方法开独立数据库事务，提交或回滚不影响外层 Session。

### 注意事项

- **不要在 `@SingleSession` 方法内直接操作 DAO**：应该委托给带 `@Transactional(REQUIRES_NEW)` 的 Store 方法。
- **`@SingleSession` 不是数据库事务**：它只绑定 ORM Session 生命周期。数据库事务边界由 `@Transactional` 控制。

### 常见陷阱：@SingleSession 下的死代码与无法刷新的缓存

`@SingleSession` + `@Transactional(REQUIRES_NEW)` 组合有一个隐性陷阱：Store 方法内的 `requireEntityById` 返回的是 `@SingleSession` Session 缓存中的**同一 Java 对象**（不是 DB 最新值）。这意味着：

1. **死代码陷阱**：如果调用方在 `@SingleSession` 中修改了实体的状态（如将 fire 设为 SUCCESS），然后调用 Store 方法，Store 方法内的 `requireEntityById` 返回**与调用方同一对象**。调用方的修改立即对 Store 方法可见：

   ```java
   @SingleSession
   void scanBatch() {
       fire.setFireStatus(FIRE_STATUS_SUCCESS);  // 修改了 @SingleSession 缓存的实体
       fireStore.completeFireAndUpdateSchedule(fire, schedule);
       //                                 ↑ 这个 fire 与上面是同一对象
   }

   // JobFireStoreImpl.java
   @Transactional(REQUIRES_NEW)
   void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
       NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
       //  ↑ 返回 @SingleSession 缓存的同一对象 — fire 已是 SUCCESS
       if (JobStatusHelper.isTerminalFire(currentFire.getFireStatus())) {
           return;  // ← 短路！方法体是死代码
       }
       // ↓ 以下从不执行
       if (!fireDao().tryUpdateWithVersionCheck(fire)) { ... }
   }
   ```

   **解决方案**：不要用 `requireEntityById` 校验调用方已修改的状态。直接在方法体开始时执行 `tryUpdateWithVersionCheck(fire)` — 如果 fire 版本匹配 DB，更新成功；如果不匹配，说明有并发冲突，静默返回即可。

2. **缓存不可刷新陷阱（@SingleSession 限制定理）**：在 `@SingleSession` 中，`requireEntityById` 通过 `_makeProxy` → `cache.get(entityName, id)` 查询缓存，**命中即返回**，不检查实体状态或版本。这意味着：

   - 即使先调用 `orm_unload()`（将实体状态设为 PROXY），`requireEntityById` 返回的仍是同一对象（状态不变）
   - retry loop 中的 reload 永远得不到新数据
   - **retry loop 在 `@SingleSession` 下注定失败**

   ```java
   // ❌ 错误的 retry loop — 在 @SingleSession 下永远无效
   for (int attempt = 0; attempt < 5; attempt++) {
       if (dao.tryUpdateWithVersionCheck(entity)) return;
       entity.orm_unload();
       entity = dao.requireEntityById(entity.get_id());
       // ↑ 返回与 entity 同一对象（← 因为 @SingleSession 缓存命中）
       // orm_unload() 将状态设为 PROXY，但 requireEntityById 返回同一对象
       // 实体的 orm_readonly(true) 标志未被清除
       // 后续 setter → checkReadonly() → ERR_ORM_ENTITY_IS_READONLY
   }
   ```

   **解决方案**：在 `@SingleSession` 下不要使用 retry loop。实体已被调用方修改（dirty），直接用 `tryUpdateWithVersionCheck` flush 即可。如果版本冲突，由上层 Scanner 的下一个扫描周期自动重试。

3. **状态不一致陷阱**：如果 Store 方法因版本冲突返回（未更新 fire/schedule），调用方不应继续执行后续操作（如取消 tasks），因为 fire 在 DB 中仍保持之前的状态。

   **解决方案**：Store 方法返回后检查实体的 `orm_readonly()` 标志（`tryUpdateWithVersionCheck` 在版本冲突时设此标志）。如果为 true，跳过后续操作。

## 模式三：check-then-act 与二次校验

某些场景需要先检查状态再执行操作，存在理论上的 TOCTOU 窗口：

```java
@Transactional(REQUIRES_NEW)
public void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {
    // 二次校验：重新读取最新状态
    NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
    if (currentFire.getFireStatus() != FIRE_STATUS_DISPATCHING) {
        return; // 状态已变，跳过
    }
    // 安全执行
    for (NopJobTask task : tasks) {
        taskDao().saveEntityDirectly(task);
    }
    currentFire.setFireStatus(FIRE_STATUS_RUNNING);
    List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(
            Collections.singletonList(currentFire));
    // 乐观锁版本检查，并发冲突时 updated 为空
}
```

这种模式是**安全的**，因为：

1. 整个方法在 `REQUIRES_NEW` 事务中执行。
2. `requireEntityById` 在事务内读取（若实体未在 Session 缓存中则从 DB 加载），后续 UPDATE 在同一事务中，数据库行锁保证一致性。
3. 状态检查是幂等的：如果状态已变，直接跳过，不产生副作用。

> **注意**：如果实体在同一 Session 中已被加载过，`requireEntityById` 返回缓存对象而非 DB 最新值（见模式四）。在乐观锁重试循环中，需要先 `orm_unload()` 再 `requireEntityById` 才能获得 DB 最新值。

## 模式四：ORM Session 一级缓存与数据新鲜度

### 核心规则

Nop ORM 的 Session 维护一级缓存（按 `entityName + id` 索引）。**一旦实体进入 Session 缓存并完成字段初始化，后续任何读取操作都不会从 DB 覆盖已初始化的字段值。** 这是 Session 级别的 repeatable-read 语义，确保同一 Session 内看到的数据一致。

### 受影响的 API（行为相同）

| API | 缓存行为 | 说明 |
|-----|---------|------|
| `dao.getEntityById(id)` / `requireEntityById(id)` | 返回缓存实体 | 若实体已在 Session 中，直接返回，不查 DB |
| `dao.findAllByQuery(query)` | 返回缓存实体（不覆盖已初始化字段） | SQL 查询结果经 `internalAssemble` 组装，但已初始化字段不被覆盖 |
| `dao.batchGetEntitiesByIds(ids)` / `tryBatchGetEntitiesByIds(ids)` | 返回缓存实体 | 内部用 `session.load()` 检查缓存 |
| `dao.loadEntityById(id)` | 返回 proxy 或缓存实体 | proxy 是未加载的占位对象 |

> **关键结论**：`findAllByQuery` 和 `tryBatchGetEntitiesByIds` 在缓存行为上**没有区别**。两者都不会刷新 Session 中已存在实体的字段值。不要认为 `findAllByQuery` 比 `getEntityById` "更新鲜"。
>
> **补充澄清**：`findAllByQuery` 仍会执行 SQL（有 DB I/O 成本），可能填充此前未初始化的 lazy 字段——使数据"更完整"，但对已初始化字段而言并不比 `getEntityById` 新鲜。

### 实现机制

`OrmSessionImpl.internalAssemble` 是字段组装的核心逻辑（`OrmSessionImpl.java:365`）：

```java
if (!entity.orm_state().isManaged()) {
    // 新实体或 proxy：从 DB 值设置所有字段
    entity.orm_internalSet(propId, values[i]);
} else {
    // 已 managed（已在 Session 缓存中）：
    // "如果已经设置过，不再更新，确保 session 范围内看到的数据具有一致性"
    if (!entity.orm_propInited(propId)) {
        entity.orm_internalSet(propId, values[i]);  // 只填充未初始化的字段
    }
}
```

### 如何强制从 DB 重新加载

| 方法 | 适用场景 | 说明 |
|------|---------|------|
| `entity.orm_unload()` + `dao.getEntityById(id)` | 非 @SingleSession 场景的乐观锁重试 | `orm_unload()` 将实体状态重置为 proxy，下次 `getEntityById` 重新从 DB 加载所有字段 |
| `session.refresh(entity)` | 显式刷新单个实体 | 内部调用 `orm_unload()` + `internalLoad()` |
| `session.evict(entity)` + `dao.getEntityById(id)` | 从缓存中移除后重新加载 | evict 后实体不在缓存中，`getEntityById` 创建新实例 |

> **⚠️ 限制**：上述方法在 `@SingleSession` 下**均无效**。因为 `_makeProxy` 在 `cache.get(entityName, id)` 命中时直接返回缓存对象（不检查状态）。即使先 `orm_unload()` 或 `evict()`，`requireEntityById` 返回同一对象。详见模式二的"缓存不可刷新陷阱"。

### 常见误区

| 误区 | 实际行为 | 正确做法 |
|------|---------|---------|
| "`findAllByQuery` 比 `getEntityById` 数据更新" | 两者返回相同缓存实体，字段不覆盖 | 需要新鲜数据时先 `orm_unload()`（但注意 @SingleSession 下无效） |
| "`tryBatchGetEntitiesByIds` 返回旧数据所以不能用" | 与 `findAllByQuery` 行为一致 | 两者可互换，缓存行为不影响选择 |
| "在 `REQUIRES_NEW` 事务中 `requireEntityById` 一定读到 DB 最新值" | 若实体已在 Session 缓存中则返回缓存 | 先 `orm_unload()` 再 `requireEntityById`（但 @SingleSession 下无效） |
| "`@Transactional(REQUIRES_NEW)` 会自动刷新缓存" | `REQUIRES_NEW` 控制数据库事务，不等于新 Session | 取决于 Session 管理策略，需验证 |
| "retry loop 的 `orm_unload()` + reload 能获得新数据" | `@SingleSession` 下 `requireEntityById` 始终返回同一缓存对象 | @SingleSession 下 retry loop 注定失败，应移除 retry loop |
| "`orm_unload()` 能清除所有状态" | `orm_unload()` 不清除 `fullyLoaded` 和 `orm_readonly` | setter 在 `fullyLoaded=true` 下仍可工作，但 `readonly` 会阻止修改 |

### 在乐观锁重试循环中的应用

```java
for (int attempt = 0; attempt < 5; attempt++) {
    // 基于 baseline 计算增量
    int delta = target - baseline.getValue();

    if (dao.tryUpdateWithVersionCheck(entity)) {
        return; // 成功
    }

    // 失败后刷新 baseline：
    // 必须 orm_unload()，否则 requireEntityById 返回缓存旧值
    entity.orm_unload();
    baseline = dao.requireEntityById(entity.get_id());
}
```

> **⚠️ 重要限制**：上述 retry loop 在 `@SingleSession` 下**无效**。因为 `requireEntityById` 总是返回同一缓存对象（`_makeProxy` → `cache.get()` 命中）。而且 `orm_unload()` 不清除 `readonly` 标志（`tryUpdateWithVersionCheck` 在版本冲突时设此标志），导致后续 setter 抛出 `ERR_ORM_ENTITY_IS_READONLY`。
>
> **在 `@SingleSession` 下，应移除 retry loop**，使用单次 `tryUpdateWithVersionCheck`。如果实体是 dirty 的，flush 会自动包含所有修改。版本冲突由上层 Scanner 的下一个扫描周期处理。

## 审计误报排除清单

以下模式在审计中可能被标记为"竞态条件"或"事务边界问题"，但实际是正确的并发设计：

| 模式 | 表面问题 | 实际情况 |
|------|---------|---------|
| fetch → tryLock 两步法 | "读和锁之间有时间窗口" | 乐观锁保证最终一致性，冲突自动丢弃 |
| `tryUpdateManyWithVersionCheck` 返回子集 | "部分更新丢失" | 失败的是被其他实例抢走的，属于正常并发 |
| `@SingleSession` + `REQUIRES_NEW` 混用 | "事务边界不清晰" | 刻意设计：Session 生命周期 ≠ 数据库事务 |
| Store 方法内二次校验状态 | "check-then-act 竞态" | 在 `REQUIRES_NEW` 事务内，行锁保护 |
| nextFireTime 推到远未来 | "修改了业务字段作为锁" | 预留信号模式：用时间字段同时表达"锁定"和"下次触发时间" |

## 关键 API

| API | 用途 |
|-----|------|
| `IOrmEntityDao.tryUpdateManyWithVersionCheck(entities)` | 乐观锁批量更新，只返回成功更新的实体 |
| `IOrmEntityDao.tryUpdateWithVersionCheck(entity)` | 乐观锁单实体更新，返回 boolean |
| `@SingleSession` | 绑定 ORM Session 到方法生命周期（非数据库事务） |
| `@Transactional(propagation = REQUIRES_NEW)` | 开启独立新事务，与外层事务隔离 |
| `dao.updateEntityDirectly(entity)` | 直接更新实体（跳过某些中间层，在 Store 层使用） |
| `dao.saveEntityDirectly(entity)` | 直接保存实体 |
| `dao.requireEntityById(id)` | 按 ID 查询，不存在则抛异常 |
| `entity.orm_unload()` | 将实体重置为 proxy 状态，下次读取时从 DB 重新加载（见模式四） |
| `session.refresh(entity)` | 显式从 DB 刷新实体字段（内部调用 `orm_unload()` + `internalLoad()`）|
