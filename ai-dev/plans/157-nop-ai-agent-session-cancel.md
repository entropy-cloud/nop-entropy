# 157 nop-ai-agent Actor Cancel 两级语义

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: A5

> Last Reviewed: 2026-06-13
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 (A5), `ai-dev/design/nop-ai-agent/02-execution-model.md` §七（外部取消 → 优雅停止，保存当前状态）
> Related: `134-nop-ai-agent-engine-actor-entry.md`（L1-17 添加了 cancelSession/getSessionStatus/forkSession 的 UOE default 方法）

## Purpose

将 `IAgentEngine.cancelSession(sessionId, reason, forced)` 从 UOE 空壳实现为两级取消语义——graceful（`forced=false`：完成当前工具调用后在迭代边界退出）/ forced（`forced=true`：立即中断执行线程）。同时实现 `getSessionStatus` 以使取消结果可观测。这收口了 `IAgentEngine` 公开 API 表面唯一影响 cancelSession 调用方的 active UOE 崩溃漏洞。

## Current Baseline

- `IAgentEngine.cancelSession(sessionId, reason, forced)` 是 default 方法，抛 UOE（`IAgentEngine.java:21-23`）
- `IAgentEngine.getSessionStatus(sessionId)` 是 default 方法，抛 UOE（`IAgentEngine.java:17-19`）
- `DefaultAgentEngine` 只覆盖 `sendMessage` 和 `execute`（`:121-137`），cancelSession / getSessionStatus / forkSession 均未覆盖
- `TestIAgentEngineDefaultMethods.java` 当前断言三个方法都抛 UOE——cancelSession 和 getSessionStatus 的 UOE 断言在实现后必须更新为行为测试
- `AgentExecStatus` 枚举有 4 值：`pending, running, completed, failed`——无 `cancelled`
- `AgentEventType` 无 `SESSION_CANCELLED` 事件
- `AgentExecutionContext` 无取消标志字段
- `ReActAgentExecutor.execute()` 主循环在 `:252`（`while currentIteration < maxIterations`）；工具调用在 `:409` 阻塞（`CompletableFuture.allOf(futuresArray).join()`）；catch 块在 `:493` 将所有异常统一设为 `failed`，不区分取消与失败
- `DefaultAgentEngine.doExecute()` 在 `:177` 用 `CompletableFuture.supplyAsync` 运行 executor，执行完后将新消息持久化到 session
- `AgentSession` 有 `status` 字段（`AgentExecStatus`）+ `setStatus` 方法
- `ISessionStore.get(sessionId)` 可按 sessionId 读取 session
- 执行模型设计文档 §七 已将"外部取消 → 优雅停止，保存当前状态"列为预期策略——但代码层面从未实现
- `agent-plan.record-mappings.xml` 引用 `AgentExecStatus` 作为 dict schema（`:24`, `:73`），增加枚举值是向后兼容的

## Goals

- `cancelSession(sessionId, reason, false)`（graceful）：当前工具调用正常完成后，ReAct 循环在下一次迭代边界检测到取消标志并退出，状态设为 `cancelled`
- `cancelSession(sessionId, reason, true)`（forced）：中断执行线程，阻塞调用抛出异常后被 catch 块识别为取消，状态设为 `cancelled`
- `getSessionStatus(sessionId)` 返回 session 当前状态（不再抛 UOE）
- 取消时通过 `AgentEventPublisher` 发布 `SESSION_CANCELLED` 事件
- graceful 取消后 partial messages 正确保久化到 session

## Non-Goals

- `forkSession` 实现（独立能力，不在 A5 范围；其 UOE 保持不变）
- Checkpoint/snapshot 恢复（属于 L3-4 / A4）
- 分布式跨进程取消传播（属于 Layer 4 Actor Runtime / IMessageService）
- 超时自动取消（A5 只处理显式 cancel 调用）
- DB-backed session store 的取消语义

## Scope

### In Scope

- `AgentExecStatus` 增加 `cancelled` 枚举值
- `AgentEventType` 增加 `SESSION_CANCELLED`
- `AgentExecutionContext` 增加取消标志 + 取消原因
- `DefaultAgentEngine` 追踪运行中执行（sessionId → handle），实现 cancelSession 两级语义 + getSessionStatus
- `ReActAgentExecutor` 主循环增加取消轮询（graceful）+ catch 块区分取消/失败（forced）
- 更新 `TestIAgentEngineDefaultMethods`（cancelSession / getSessionStatus 不再抛 UOE）
- 新增 cancel 单元测试 + 端到端测试

### Out Of Scope

- `forkSession`（独立工作项，UOE 保持不变）
- 检查点恢复（L3-4）
- 多进程取消传播（Layer 4）

## Execution Plan

### Phase 1 - Cancel 两级语义实现 + 单元测试

Status: completed
Targets: `AgentExecStatus.java`, `AgentEventType.java`, `AgentExecutionContext.java`, `DefaultAgentEngine.java`, `ReActAgentExecutor.java`, `TestIAgentEngineDefaultMethods.java`

- Item Types: `Fix`

- [x] `AgentExecStatus` 增加 `cancelled` 枚举值
- [x] `AgentEventType` 增加 `SESSION_CANCELLED`
- [x] `AgentExecutionContext` 增加 volatile 取消标志字段（可被运行中的 executor 轮询、被 cancelSession 从外部线程设置）+ 取消原因字段 + 对应的读取/设置方法
- [x] `DefaultAgentEngine` 增加运行中执行追踪结构（按 sessionId 索引，持有 AgentExecutionContext 引用 + 执行线程引用），在 `doExecute` 的 `supplyAsync` 内绑定当前线程，finally 中注销
- [x] `DefaultAgentEngine` 实现 `cancelSession(sessionId, reason, forced)`：查找运行中执行 → graceful 仅设取消标志 / forced 设取消标志 + 中断线程 → 更新 sessionStore 中 session 状态为 `cancelled` → 发布 `SESSION_CANCELLED` 事件
- [x] `DefaultAgentEngine` 实现 `getSessionStatus(sessionId)`：从 sessionStore 读取 session 状态返回；session 不存在时抛 `NopAiAgentException`
- [x] `ReActAgentExecutor` 主循环顶部（while 条件后、下一次 LLM 调用前）检查取消标志 → 设 status=`cancelled` + break
- [x] `ReActAgentExecutor` catch 块区分取消与失败：若 `ctx` 取消标志为 true，设 status=`cancelled` 而非 `failed`
- [x] 更新 `TestIAgentEngineDefaultMethods`：cancelSession 和 getSessionStatus 的 UOE 断言替换为行为测试（cancelSession 不再抛 UOE；getSessionStatus 返回 AgentExecStatus）
- [x] 验证现有 xdef / record-mappings 引用 `AgentExecStatus` 的地方在增加 `cancelled` 后仍可正常校验（`agent-plan.record-mappings.xml:24,73` 使用 dict 引用，向后兼容）
- [x] 单元测试：`getSessionStatus` 对已注册 session 返回正确状态值；对不存在的 sessionId 抛异常
- [x] 单元测试：`cancelSession(forced=false)` 对运行中 session 设取消标志后 session 状态变 `cancelled`
- [x] 单元测试：`cancelSession(forced=true)` 对运行中 session 设取消标志 + 中断线程后 session 状态变 `cancelled`
- [x] 单元测试：`cancelSession` 对不存在的 sessionId 抛异常（非静默返回）
- [x] 单元测试：`forkSession` 的 UOE 断言保持不变（不在本计划范围）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AgentExecStatus.cancelled` 枚举值存在
- [x] `AgentEventType.SESSION_CANCELLED` 枚举值存在
- [x] `AgentExecutionContext` 的取消标志为 volatile，可跨线程可见（被 cancelSession 线程设置、被 executor 线程读取）
- [x] `DefaultAgentEngine.cancelSession` 不再抛 UOE，`forced=false` 与 `forced=true` 走不同路径
- [x] `DefaultAgentEngine.getSessionStatus` 不再抛 UOE，返回正确的 `AgentExecStatus`
- [x] `DefaultAgentEngine` 运行中执行追踪在 `doExecute` 完成后正确注销（不泄漏）
- [x] `ReActAgentExecutor` 主循环在迭代边界检查取消标志，graceful 取消时 `status=cancelled` 并退出循环
- [x] `ReActAgentExecutor` catch 块在取消标志为 true 时设 `cancelled` 而非 `failed`
- [x] `TestIAgentEngineDefaultMethods` 已更新：cancelSession / getSessionStatus 不再断言 UOE，改为行为断言
- [x] **接线验证**：cancelSession 调用确实修改了 ReAct 循环运行时能观察到的取消标志（通过测试验证 executor 在取消后退出循环，而非仅验证标志存在）
- [x] **无静默跳过**：cancelSession 对不存在的 session 抛 `NopAiAgentException`，getSessionStatus 对不存在的 session 抛异常——均非静默返回 null/默认值
- [x] **新增功能测试**：cancelSession graceful / forced / getSessionStatus / 不存在 session 边界 各有对应单元测试且通过
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-ai-agent/02-execution-model.md` §七更新取消语义描述（两级语义），`01-architecture-baseline.md` §四 IAgentEngine 行更新（cancelSession/getSessionStatus 已实现）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 端到端取消流程验证

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`

- Item Types: `Proof`

- [x] 端到端测试（graceful）：启动 agent 执行（mock IChatService 返回带工具调用的响应 + mock 工具带可控延迟）→ 异步调用 `cancelSession(forced=false)` → 验证当前工具调用完成、循环退出、`AgentExecutionResult.status == cancelled`、partial messages 已持久化到 session
- [x] 端到端测试（forced）：启动 agent 执行（mock 工具阻塞）→ 异步调用 `cancelSession(forced=true)` → 验证执行 promptly 停止、`status == cancelled`
- [x] 端到端测试（getSessionStatus 生命周期）：在 execute 前调用返回 `pending` 或 `running`；cancel 后返回 `cancelled`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（graceful）：从 `engine.execute()` → 工具执行中 → `cancelSession(forced=false)` → 结果 `status=cancelled`，当前工具结果在 messages 中，session 持久化正确
- [x] **端到端验证**（forced）：从 `engine.execute()` → 工具阻塞中 → `cancelSession(forced=true)` → 结果 `status=cancelled`，执行 promptly 停止
- [x] **端到端验证**（getSessionStatus）：在 pending/running/cancelled 各阶段返回正确值
- [x] **Anti-Hollow Check**：cancelSession 调用链从 engine → ctx 取消标志 → executor 循环退出 → 结果 status=cancelled 完整连通（端到端测试已验证，非仅组件级单测）
- [x] 上述测试在 `./mvnw test -pl nop-ai-agent -am` 中全部通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] cancelSession 两级语义已实现且不再抛 UOE（graceful + forced 两条路径均有端到端测试）
- [x] getSessionStatus 已实现且不再抛 UOE
- [x] forkSession UOE 保持不变（不在本计划范围）
- [x] AgentExecStatus.cancelled 已添加，现有 xdef / record-mappings 校验不受影响
- [x] SESSION_CANCELLED 事件在取消时发布
- [x] graceful 取消后 partial messages 正确持久化到 session
- [x] 必要 focused verification（graceful + forced 端到端测试）已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步到 live baseline（`02-execution-model.md` §七，`01-architecture-baseline.md` §四）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 cancelSession → ctx 取消标志 → executor 退出 → 结果连通；getSessionStatus → sessionStore → session.status 连通
- [x] `./mvnw compile -pl nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### forkSession 实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: forkSession 是独立能力（会话派生），与取消语义无关；其 UOE 保持不变，不影响 cancel 功能
- Successor Required: yes
- Successor Path: 待独立 plan

### DB-backed session 取消传播

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: A5 范围是单进程内存引擎的取消语义；多实例跨进程取消属于 Layer 4 Actor Runtime
- Successor Required: yes
- Successor Path: 未来 Layer 4 plan

### 超时自动取消

- Classification: `optimization candidate`
- Why Not Blocking Closure: A5 只处理显式 `cancelSession` 调用；超时自动取消属于执行控制扩展（02-execution-model.md §六），可基于取消标志机制后续实现
- Successor Required: no

## Non-Blocking Follow-ups

- 被取消 session 的外部资源清理（如打开的文件句柄）——当前 ReAct executor 不持有跨调用外部资源
- 强制取消时工具调用结果的持久化策略（当前 forced 取消丢弃未完成的工具结果，graceful 保留）

## Closure

Status Note: Plan 157 已完成。`IAgentEngine.cancelSession` 实现两级取消语义（graceful 设取消标志在迭代边界退出 / forced 额外中断执行线程），`getSessionStatus` 返回 session 状态。两者对不存在的 sessionId 均 fail-fast 抛 `NopAiAgentException`（无静默跳过）。取消时发布 `SESSION_CANCEL_REQUESTED` + `SESSION_CANCELLED` 事件（运行中执行由 executor 发布 SESSION_CANCELLED；idle session 由 engine 直接发布）。`forkSession` 保持 UOE 不变（out-of-scope）。所有端到端测试验证了从 engine → ctx 标志 → executor 循环退出 → 结果 status=cancelled 的完整调用链。

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session, task_id=ses_13f189771ffeT48QtiH49W5AQq），未参与本计划实现
- Audit Session: ses_13f189771ffeT48QtiH49W5AQq
- Evidence:
  - Phase 1 Exit Criteria（11 条）：全部 PASS
    - `AgentExecStatus.cancelled` 存在 → `AgentExecStatus.java:12`
    - `AgentEventType.SESSION_CANCELLED` 存在 → `AgentEventType.java:28`
    - 取消标志 volatile → `AgentExecutionContext.java:29`
    - cancelSession 不抛 UOE，forced 路径中断线程 → `DefaultAgentEngine.java:137-163`
    - getSessionStatus 不抛 UOE，缺失 session 抛 NopAiAgentException → `DefaultAgentEngine.java:128-134`
    - 运行中执行追踪 try/finally 注销无泄漏 → `DefaultAgentEngine.java:242-251`
    - ReAct 循环迭代边界 + 工具调用后检查取消标志 → `ReActAgentExecutor.java:253-256, 483-486`
    - catch 块取消标志 true 时设 cancelled → `ReActAgentExecutor.java:505-516`
    - TestIAgentEngineDefaultMethods 不再断言 UOE → 行为测试覆盖
    - 无静默跳过：缺失 session 抛异常（源码 + 测试双重验证）
    - 新增功能测试（TestDefaultAgentEngineCancel 8 项）全部通过
  - Phase 2 Exit Criteria（4 条）：全部 PASS
    - graceful 端到端 → `cancelGracefulEndToEndStopsAfterCurrentTool`
    - forced 端到端 → `cancelForcedEndToEndInterruptsImmediately`（elapsedMs < 30000）
    - getSessionStatus 生命周期 → `getSessionStatusReflectsLifecycleDuringExecution`（running/cancelled）
    - Anti-Hollow 调用链连通 → 端到端测试 + 源码追踪
  - Closure Gates（13 条）：全部 PASS（forkSession UOE 不变；record-mappings 向后兼容；事件发布；partial messages 持久化）
  - Anti-Hollow 检查：调用链 `cancelSession → ctx.setCancelRequested → ReAct loop isCancelRequested → handleCancellation(status=cancelled) → doExecute finally session.setStatus → result=cancelled` 完整连通；idle-session 路径亦发布 SESSION_CANCELLED
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 findings）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/157-nop-ai-agent-session-cancel.md --strict` 退出码 0（52/52 items checked）
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 401 tests, 0 failures, 0 errors, BUILD SUCCESS
  - 审计观察（非阻塞）：idle-session 取消现已发布 SESSION_CANCELLED（审计后修复，消除 minor deviation）；Plan Status 由 active 改为 completed 符合 closure 流程

Follow-up:

- 无 plan-owned 剩余工作
- Deferred 项（forkSession / DB-backed 跨进程取消传播 / 超时自动取消）均已在 `Deferred But Adjudicated` 中裁定为 non-blocking out-of-scope

## Follow-up handled by 161-nop-ai-agent-session-fork.md

Plan 161 picks up the forkSession implementation — the successor work item from this plan's `Deferred But Adjudicated` §"forkSession 实现" (Successor Required: yes). It implements `IAgentEngine.forkSession` and `ISessionStore.forkSession` in `DefaultAgentEngine` and `InMemorySessionStore` respectively, replacing the UOE stubs with functional session-fork semantics (deep-copy messages, parent-child link, inheritContext flag). forkSession UOE is no longer present after plan 161 lands.
