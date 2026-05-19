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
    fireDao().updateEntityDirectly(currentFire);
}
```

这种模式是**安全的**，因为：

1. 整个方法在 `REQUIRES_NEW` 事务中执行。
2. `requireEntityById` 在事务内读取，后续 UPDATE 在同一事务中，数据库行锁保证一致性。
3. 状态检查是幂等的：如果状态已变，直接跳过，不产生副作用。

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
| `@SingleSession` | 绑定 ORM Session 到方法生命周期（非数据库事务） |
| `@Transactional(propagation = REQUIRES_NEW)` | 开启独立新事务，与外层事务隔离 |
| `dao.updateEntityDirectly(entity)` | 直接更新实体（跳过某些中间层，在 Store 层使用） |
| `dao.saveEntityDirectly(entity)` | 直接保存实体 |
| `dao.requireEntityById(id)` | 按 ID 查询，不存在则抛异常 |
