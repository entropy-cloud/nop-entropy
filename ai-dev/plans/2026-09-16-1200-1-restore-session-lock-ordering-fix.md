# Plan: restoreSession 锁序修复 + 租户上下文修复

> Status: DRAFT
> Created: 2026-09-16
> Source: independent audit of nop-ai-agent deep-audit fixes (2026-09-16)

## Goals

修复 `AgentSessionLifecycle.restoreSession` 中两个残留的正确性问题：

1. **P0 — 状态变更在锁获取之前**：`transitionToRunningAndPublishRestored` 在 `acquireRestoreExecutionSlot` 之前被调用，导致并发 restoreSession 调用可在未持锁的情况下变更共享 session 状态
2. **P1 — worker 线程租户上下文为 null**：restoreSession 的 worker lambda 设置 `ThreadLocalTenantResolver.set(null)` 而非 `sessionTenantId`，导致恢复路径的 DB 操作以 null tenant 运行（跨租户可见）

## Non-Goals

- 不修改 resumeSession/wakeSession（已正确实现）
- 不修改 doExecute（已正确实现）
- 不引入新的 session 状态或锁机制

## Current Baseline

**问题代码位置**：`AgentSessionLifecycle.java`

**问题 1 — 锁序颠倒**（第 582-597 行）：
```java
public CompletableFuture<AgentExecutionResult> restoreSession(...) {
    AgentSession session = validateRestorableSession(sessionId);
    // ... validation ...
    transitionToRunningAndPublishRestored(session, ...);  // ← 状态变更在锁之前
    ExecutionWiring wiring = buildExecutionWiring(...);
    CancelHandle handle = new CancelHandle(wiring.ctx(), null);
    acquireRestoreExecutionSlot(sessionId, handle);  // ← 锁获取在状态变更之后
    return dispatchRestoreExecution(...);
}
```

`transitionToRunningAndPublishRestored`（第 700-725 行）执行：
- `session.setStatus(AgentExecStatus.running)` — 共享状态变更
- `eventPublisher.publish(AgentEvent.create(SESSION_RESTORED, ...))` — 事件发布

**问题 2 — 同步阶段无租户上下文**：`buildExecutionWiring`（第 592 行）调用链：
- `buildExecutionWiring` → `buildBaseExecutionContext` → `buildBudgetedMemorySection`（第 156 行）
- `buildBudgetedMemorySection` 执行 `memoryStore.readBudgeted(...)` — 若 store 是租户感知的，此操作以 null tenant 运行（跨租户可见）

**问题 3 — worker 线程租户上下文为 null**（第 806 行）：
```java
ThreadLocalTenantResolver.set(null);  // ← 应为 sessionTenantId
```

**对比 resumeSession 的正确模式**（第 270-344 行）：
```java
// 1. 捕获 sessionTenantId
String sessionTenantId = session.getTenantId();
// 2. 先获取锁
if (!config.getSessionTakeoverLock().tryAcquire(...)) { throw ...; }
CancelHandle existing = runningExecutions.putIfAbsent(...);
slotRegistered = true;
// 3. 锁获取成功后，才设置租户上下文和变更状态
ThreadLocalTenantResolver.set(sessionTenantId);
try {
    config.getDenialLedger().reset(sessionId);
    config.getPostDenialGuard().reset(sessionId);
    session.setStatus(AgentExecStatus.running);
    eventPublisher.publish(AgentEvent.create(SESSION_RESUMED, ...));
} finally {
    ThreadLocalTenantResolver.set(previousTenant);
}
```

## Execution Plan

### Phase 1: 修复 restoreSession 锁序 + 租户上下文

**修改 `restoreSession` 方法**（第 582-597 行）：
- 在方法入口捕获 `sessionTenantId`（与 resumeSession 第 243 行对称）
- 将 `transitionToRunningAndPublishRestored` 调用移入 `acquireRestoreExecutionSlot` 内部
- 在 `putIfAbsent` 成功后执行状态变更和事件发布
- 添加租户上下文设置（与 resumeSession 对称）

**修改 `buildExecutionWiring` 调用**：
- 将 `buildExecutionWiring` 调用包装在租户上下文块中
- 或者验证 memory store 是 session-scoped 而非 tenant-scoped（若是后者，记录显式文档说明）

**修改 `acquireRestoreExecutionSlot` 方法**（第 754-784 行）：
- 添加 `sessionTenantId` 参数
- 在 `slotRegistered = true` 之后设置 `ThreadLocalTenantResolver.set(sessionTenantId)`
- 执行状态变更和事件发布
- 在 finally 中恢复 `ThreadLocalTenantResolver.set(previousTenant)`

**修改 `dispatchRestoreExecution` 方法**（第 793-881 行）：
- 添加 `sessionTenantId` 参数（显式数据流，非闭包）
- worker lambda 中的 `ThreadLocalTenantResolver.set(null)` 改为 `ThreadLocalTenantResolver.set(sessionTenantId)`

### Phase 2: 测试验证

- 运行 `./mvnw test -pl nop-ai/nop-ai-agent -am` 确认无回归
- 添加回归测试：并发 restoreSession 同一 sessionId → 第二次应抛出 "session already executing"
- 添加回归测试：worker 线程租户上下文等于 `session.getTenantId()`（断言 `ThreadLocalTenantResolver.current()`）
- 添加回归测试：SESSION_RESTORED 事件在锁获取之后发布

## Exit Criteria

- [ ] `restoreSession` 的状态变更和事件发布在锁获取之后执行
- [ ] `restoreSession` 同步阶段操作（buildExecutionWiring、verifyLatestCheckpoint）运行在正确的租户上下文中
- [ ] `restoreSession` 的 worker lambda 设置正确的租户上下文（非 null）
- [ ] 所有现有测试通过
- [ ] 新增回归测试：并发 restoreSession 锁保护（第二次调用抛出 "session already executing"）
- [ ] 新增回归测试：worker 线程租户上下文断言
- [ ] 新增回归测试：SESSION_RESTORED 事件发布时序

## Closure Gates

- [ ] 独立子 agent 审计确认锁序正确
- [ ] 独立子 agent 审计确认租户上下文正确
- [ ] 无新增 P0/P1 发现
