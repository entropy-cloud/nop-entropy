# 161 nop-ai-agent Session Fork (forkSession)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: forkSession

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 157 (`ai-dev/plans/157-nop-ai-agent-session-cancel.md`, Deferred: "forkSession 实现", Successor Required: yes); roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 (L1-17 extension point delivered the UOE stub; A5 implemented cancelSession/getSessionStatus, deferred forkSession); design `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` §5.3-5.4, `nop-ai-agent-session-and-storage.md` §9, `nop-ai-agent-session-engine.md` §5.2
> Related: Plan 134 (L1-17 added forkSession UOE default methods), Plan 157 (A5 implemented cancel/getSessionStatus, deferred forkSession)

## Purpose

将 `IAgentEngine.forkSession(request, inheritContext)` 和 `ISessionStore.forkSession(parentSessionId, inheritContext, props)` 从 UOE 空壳实现为功能性的会话分叉语义——基于父 session 创建独立子 session，按 `inheritContext` 决定是否继承消息历史/planId/metadata。这收口了 `IAgentEngine` 公开 API 表面最后一个 active UOE，并解除 branch-affinity-scheduling、multi-agent forking、checkpoint/restore、compaction snapshot persistence 等下游能力的阻塞。

## Current Baseline

- `IAgentEngine.forkSession(AgentMessageRequest request, boolean inheritContext)` 是 default 方法，抛 UOE（`IAgentEngine.java:13-15`，消息: "forkSession requires Phase 2 ISessionStore"）
- `ISessionStore.forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props)` 是 default 方法，抛 UOE（`ISessionStore.java:16-18`，消息: "forkSession requires VfsSessionStore"）
- `InMemorySessionStore` 未覆盖 forkSession（继承接口 UOE）
- `DefaultAgentEngine` 未覆盖 forkSession（继承接口 UOE）
- `TestIAgentEngineDefaultMethods.forkSessionThrowsUOE()` 断言 UOE（`:20-25`）
- `TestISessionStoreDefaultMethods.forkSessionThrowsUOE()` 断言 UOE（`:15-19`）
- `AgentSession` 已有 `parentSessionId` 字段 + setter（L1-20 ✅，`:23, 110-116`）、`planId` 字段（`:24, 118-124`）、`metadata` map（`:22, 94-100`）、`messages` list（`:16, 51-60`）
- `AgentSession.create(sessionId, agentName)` 是工厂方法（`:39-41`）；构造器为 private
- `AgentSession.getMessages()` 返回防御性拷贝（`Collections.unmodifiableList(new ArrayList<>(messages))`，`:52`）
- `AgentSession.appendMessages(List<ChatMessage>)` 向内部 list 追加（`:55-60`）
- `AgentEventType` 有 14 个值（EXECUTION_STARTED, ITERATION_STARTED, LLM_RESPONSE_RECEIVED, TOOL_CALL_STARTED, TOOL_CALL_COMPLETED, TOOL_CALL_DENIED, PATH_ACCESS_DENIED, EXECUTION_COMPLETED, EXECUTION_FAILED, SESSION_CREATED, SESSION_LOADED, SESSION_CANCEL_REQUESTED, SESSION_CANCELLED, FORCED_STOP），无 `SESSION_FORKED`（`AgentEventType.java:3-31`）
- `DefaultAgentEngine.cancelSession` 已实现（plan 157），发布 `SESSION_CANCEL_REQUESTED` + `SESSION_CANCELLED` 事件，对不存在的 session fail-fast 抛 `NopAiAgentException`
- `DefaultAgentEngine` 持有 `sessionStore`（`:50`）和 `eventPublisher`（`:49`）
- `NopAiAgentException` 在 `io.nop.ai.agent.engine` 包中（`DefaultAgentEngine` 同包无需 import；`InMemorySessionStore` 在 `io.nop.ai.agent.session` 包，需 `import io.nop.ai.agent.engine.NopAiAgentException`——两者在同一 Maven 模块内，跨包 import 无编译问题，`NopAiAgentException` 无自身依赖，不形成实质循环依赖）
- 设计文档已定义 fork 语义：
  - `context-model.md` §5.3: Fork 基于 session 创建新 session，消息历史是快照拷贝，工具集从新 Agent 配置重新装配，fork 后父子完全独立；第 3 点另称"新 session 的 Plan 状态是当前 Plan 的深拷贝"——这与 `session-and-storage.md` §6（Plan 是项目级共享实体，session 仅持有 planId 引用，不深拷贝）的共享引用模型矛盾，待 Phase 2 消解
  - `context-model.md` §5.4: inheritContext=true 时继承消息历史快照 + Plan 状态（深拷贝）；false 则为空；环境信息始终继承；sessionId 新建，parentSession 指向父
  - `session-and-storage.md` §9: 分叉流程 = 创建快照 → 生成子 session → 复制初始状态 → 记录父引用
  - `session-engine.md` §5.2: 分叉 = 创建快照 → 生成子 session → 写入父引用
  - `session-and-storage.md` §6: Plan 是项目级实体，独立于 session，session 通过 planId 引用（不深拷贝）
- `01-architecture-baseline.md` §四 IAgentEngine 行写道："forkSession 仍为 Phase 2 stub（抛 UOE，待 VFS session store）"

## Goals

- `ISessionStore.forkSession` 在 `InMemorySessionStore` 中功能化实现（不再抛 UOE）
- `IAgentEngine.forkSession` 在 `DefaultAgentEngine` 中功能化实现（不再抛 UOE）
- fork 创建子 session：新 sessionId、parentSessionId 指向父、`inheritContext=true` 时独立拷贝父消息列表（新 ArrayList，非共享引用）+ 继承 planId + 继承 metadata
- fork 后父子 session 完全独立——向子追加消息不影响父，反之亦然
- fork 时发布 `SESSION_FORKED` 事件（父子 sessionId + agentName 可观测）
- forkSession 对不存在的 parent sessionId fail-fast 抛 `NopAiAgentException`（非静默返回 null）

## Non-Goals

- VfsSessionStore / VFS event log 持久化（未来能力，InMemorySessionStore 是当前唯一具体实现）
- SessionSnapshot 持久化到 VFS（plan 158 deferred，fork 当前在内存中操作，快照概念仅在审计意义上成立）
- Plan 深拷贝（Plan 是项目级实体，跨 session 共享，session 仅持有 planId 引用——见 session-and-storage.md §6）
- call-agent 工具集成（Layer 4，fork+exec 模型由 call-agent 工具驱动）
- 多 session tree 遍历查询（如 "获取所有子 session"）
- Working memory / long-term memory fork（Layer 2/4）
- 分布式跨进程 fork（Layer 4 Actor Runtime）

## Scope

### In Scope

- `InMemorySessionStore.forkSession(parentSessionId, inheritContext, props)` 覆盖实现
- `DefaultAgentEngine.forkSession(request, inheritContext)` 覆盖实现
- `AgentEventType` 增加 `SESSION_FORKED`
- 更新 `TestISessionStoreDefaultMethods`（移除 forkSession UOE 断言）
- 更新 `TestIAgentEngineDefaultMethods`（移除 forkSession UOE 断言）
- 新增 fork 语义单元测试 + 端到端测试
- 更新 `01-architecture-baseline.md` §四 IAgentEngine 行（forkSession 已实现）

### Out Of Scope

- VfsSessionStore 实现（未来）
- call-agent 工具（Layer 4）
- Plan 深拷贝
- 跨进程 fork 传播

## Execution Plan

### Phase 1 - InMemorySessionStore.forkSession + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/InMemorySessionStore.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/ISessionStore.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/TestISessionStoreDefaultMethods.java`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/` (new fork test file)

- Item Types: `Fix | Follow-up`

- [x] `InMemorySessionStore` 覆盖 `forkSession(parentSessionId, inheritContext, props)`：查找父 session → 不存在则抛 `NopAiAgentException`（fail-fast，defense-in-depth 层——engine 层也会校验）→ 生成新 sessionId（UUID）→ 创建子 `AgentSession`（agentName 取 props 中的 "agentName" 或父 agentName）→ `inheritContext=true` 时拷贝父消息历史（独立 ArrayList，非共享引用）+ 拷贝 planId + 拷贝 metadata → 合并 props（过滤 "agentName" key）到子 metadata → 设子 `parentSessionId` = parentSessionId → 存入 sessions map → 返回新 sessionId
- [x] 子 session 状态初始化为 `pending`（新 session 尚未执行），`totalTokensUsed=0`、`totalIterations=0`（不继承父的运行统计）
- [x] 为 `ISessionStore.forkSession` 和 `IAgentEngine.forkSession` 接口方法添加 javadoc，记录 props 契约（识别 "agentName" key 用于子 session agentName）、inheritContext 语义、fail-fast 行为（供 VfsSessionStore 等未来实现者参考）
- [x] `inheritContext=false` 时子 session 消息历史为空、planId 为 null（仅继承 parentSessionId + props 中的 agentName/metadata）
- [x] 更新 `TestISessionStoreDefaultMethods`：移除 `forkSessionThrowsUOE` 测试（InMemorySessionStore 不再对 forkSession 抛 UOE）；其他 4 个 UOE 测试（appendEvent/compact/loadSnapshot/setPlanRef）保持不变
- [x] 单元测试（新文件 `TestInMemorySessionStoreFork`）：forkSession 返回新 sessionId（≠ parent）
- [x] 单元测试：forkSession(inheritContext=true) → 子 session 消息数量 == 父消息数量
- [x] 单元测试：forkSession(inheritContext=false) → 子 session 消息数量 == 0
- [x] 单元测试：子 session 的 parentSessionId == parent sessionId
- [x] 单元测试：独立性——fork 后向子 session 追加消息，父 session 消息数量不变（验证非共享 list）
- [x] 单元测试：forkSession(inheritContext=true) → 子 session 的 planId == 父 planId（如有）
- [x] 单元测试：forkSession(inheritContext=false) → 子 session 的 planId == null
- [x] 单元测试：forkSession 对不存在的 parent sessionId 抛 `NopAiAgentException`（非静默返回 null）
- [x] 单元测试：props 中的 key-value 合并到子 session 的 metadata

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `InMemorySessionStore.forkSession` 不再抛 UOE，返回有效的新 sessionId（String）
- [x] 子 session 的 `parentSessionId` 正确指向父（可从 store.get(childId).getParentSessionId() 验证）
- [x] `inheritContext=true` 时子 session 拥有与父相同数量的消息，且消息列表是独立拷贝（追加验证非共享）
- [x] `inheritContext=false` 时子 session 消息为空、planId 为 null
- [x] **无静默跳过**：forkSession 对不存在的 parent 抛 `NopAiAgentException`（非返回 null / 空字符串）
- [x] **新增功能测试**：forkSession 语义（inheritContext true/false、独立性、parentSessionId、planId 继承、不存在 parent）各有对应单元测试且通过
- [x] `TestISessionStoreDefaultMethods` 中 forkSession 的 UOE 断言已移除；其他 UOE 测试不受影响
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 中 store 层 fork 测试全部通过
- [x] No owner-doc update required（store 实现细节不改变公开契约，Phase 2 统一更新 design doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DefaultAgentEngine.forkSession 接线 + 端到端测试 + 文档更新

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentEventType.java`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestIAgentEngineDefaultMethods.java`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/` (new fork test file), `ai-dev/design/nop-ai-agent/01-architecture-baseline.md`, `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md`

- Item Types: `Fix | Follow-up`

- [x] `AgentEventType` 增加 `SESSION_FORKED` 枚举值
- [x] `DefaultAgentEngine` 覆盖 `forkSession(request, inheritContext)`：解析 parent sessionId（request.getSessionId()，null 则抛异常）→ 从 sessionStore.get() 验证父存在（不存在则抛 `NopAiAgentException`，fail-fast）→ 构建 props（含 request.getAgentName() 作为子 agentName + request.getMetadata()）→ 调用 `sessionStore.forkSession(parentSessionId, inheritContext, props)` 获取新 sessionId → 发布 `SESSION_FORKED` 事件（payload 含 parentSessionId、childSessionId、inheritContext）→ 返回 `CompletableFuture.completedFuture(childSessionId)`
- [x] 更新 `TestIAgentEngineDefaultMethods`：移除 `forkSessionThrowsUOE` 测试（DefaultAgentEngine 不再对 forkSession 抛 UOE）
- [x] 端到端测试（新文件 `TestDefaultAgentEngineFork`）：engine.forkSession(request, true) → 返回新 sessionId → store.get(childId) 存在 → parentSessionId 正确 → 消息继承正确
- [x] 端到端测试：engine.forkSession(request, false) → 子 session 消息为空
- [x] 端到端测试：独立性——fork 后通过 engine.execute 向子 session 发消息 → 子消息增长 → 父 session 消息不变（或直接通过 store 操作验证）
- [x] 端到端测试：engine.forkSession 发布 `SESSION_FORKED` 事件（通过 eventPublisher 注册 listener 验证事件类型 + payload）
- [x] 端到端测试：engine.forkSession 对不存在的 parent sessionId 抛 `NopAiAgentException`
- [x] 更新 `01-architecture-baseline.md` §四 IAgentEngine 行：将 "forkSession 仍为 Phase 2 stub（抛 UOE，待 VFS session store）" 更新为 forkSession 已在 DefaultAgentEngine + InMemorySessionStore 中实现
- [x] 更新 `nop-ai-agent-context-model.md` §5.3 第 3 点 + §5.4 Plan 行：移除/修正 "Plan 深拷贝" 表述，改为 "继承 planId 引用（Plan 是项目级共享实体，见 session-and-storage.md §6）"，消除与 session-and-storage.md §6 的矛盾
- [x] 更新 `nop-ai-agent-session-and-storage.md` §9 或 §13（如需要）反映 InMemorySessionStore 已实现 fork
- [x] 更新 `nop-ai-agent-session-engine.md` §6.2：原文称 "Phase 1 的 `InMemorySessionStore` 不继承任何额外行为（4 原始方法不变，5 default 自然继承 UOE）"——经本 plan Phase 1 后 `InMemorySessionStore` 覆盖了 `forkSession`（具体方法由 4 增至 5，default 自然继承 UOE 的方法由 5 降至 4），更新该表述以消除 owner-doc drift
- [x] 更新 `nop-ai-agent-react-engine.md` §3.2：原文关于 forkSession 的 "default + UOE 确保 Phase 1 实现类不受影响，Phase 2 消费者在误用 InMemorySessionStore 时立刻失败" 理据——经本 plan 后 `InMemorySessionStore`/`DefaultAgentEngine` 已覆盖实现 `forkSession`，更新该段以反映 forkSession 已实现，消除 owner-doc drift

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultAgentEngine.forkSession` 不再抛 UOE，返回包含新 sessionId 的 `CompletableFuture<String>`
- [x] `AgentEventType.SESSION_FORKED` 枚举值存在
- [x] **接线验证**：engine.forkSession → sessionStore.forkSession 调用链连通（端到端测试验证子 session 确实出现在 store 中，非仅验证返回值类型）
- [x] **端到端验证**：从 engine.forkSession() → store.get(childId) → 验证 parentSessionId + 消息继承 + 独立性，完整路径走通
- [x] **端到端验证**：SESSION_FORKED 事件在 fork 时发布（listener 捕获到事件，payload 含 parentSessionId + childSessionId）
- [x] **无静默跳过**：engine.forkSession 对不存在的 parent 抛 `NopAiAgentException`（非静默返回 null future）
- [x] **新增功能测试**：engine 层 fork（inheritContext true/false、事件发布、不存在 parent、独立性）各有对应测试且通过
- [x] `TestIAgentEngineDefaultMethods` 中 forkSession 的 UOE 断言已移除
- [x] `01-architecture-baseline.md` §四 IAgentEngine 行已更新（forkSession 不再标记为 UOE stub）
- [x] `nop-ai-agent-context-model.md` §5.3/§5.4 已更新（移除 "Plan 深拷贝" 矛盾表述，指向 session-and-storage.md §6 权威）
- [x] `nop-ai-agent-session-engine.md` §6.2 已更新（反映 `InMemorySessionStore` 现已覆盖 `forkSession`，不再称 "5 default 自然继承 UOE"）
- [x] `nop-ai-agent-react-engine.md` §3.2 已更新（反映 `forkSession` 已在 `InMemorySessionStore`/`DefaultAgentEngine` 中实现，不再依赖 default + UOE 理据）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 fork 测试 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IAgentEngine.forkSession` 和 `ISessionStore.forkSession` 均不再抛 UOE（InMemorySessionStore + DefaultAgentEngine 已覆盖实现）
- [x] fork 语义正确：新 sessionId、parentSessionId link、inheritContext 控制消息/planId/metadata 继承
- [x] 父子 session 独立性已验证（fork 后追加消息互不影响）
- [x] SESSION_FORKED 事件在 fork 时发布
- [x] forkSession 对不存在的 parent fail-fast 抛 NopAiAgentException（无静默跳过）
- [x] 必要 focused verification（store 单元测试 + engine 端到端测试）已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步到 live baseline（`01-architecture-baseline.md` §四、`nop-ai-agent-context-model.md` §5.3/§5.4、`nop-ai-agent-session-engine.md` §6.2、`nop-ai-agent-react-engine.md` §3.2）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 engine.forkSession → store.forkSession → 新 session 出现在 store 中的完整调用链连通（端到端测试证明，非仅组件级单测）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### VfsSessionStore fork 实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: forkSession 在 InMemorySessionStore（当前唯一具体实现）中已功能化。VfsSessionStore 是未来 Phase 2 持久化后端，fork 语义相同，只是底层存储不同。当前内存 fork 完全满足所有下游依赖（branch-scheduling、multi-agent、checkpoint、compaction snapshot 均在单进程内存引擎上工作）。
- Successor Required: yes
- Successor Path: 未来 VfsSessionStore plan

### Plan 深拷贝

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Plan 是项目级实体，跨 session 共享，session 仅持有 planId 引用（见 session-and-storage.md §6）。context-model.md §5.3 提到的 "Plan 深拷贝" 与 session-and-storage.md §6 的权威声明存在设计演进——以 storage 权威为准（共享引用，不深拷贝）。fork 继承 planId 引用即可。
- Successor Required: no

### SessionSnapshot 持久化到 VFS

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: fork 当前在内存中操作，快照概念仅在审计追溯意义上成立。SessionSnapshot 的 VFS 持久化属于 plan 158 deferred item（SessionSnapshot offload）。当前 fork 直接拷贝消息列表，无需持久化快照文件。
- Successor Required: yes
- Successor Path: 未来 VFS / 持久化 plan（plan 158 后续）

### call-agent 工具 fork+exec 集成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: call-agent 工具（Layer 4）是 fork+exec 模型的上层消费者，不是 forkSession 引擎能力的必要部分。forkSession API 已就绪后，call-agent 工具可以在 Layer 4 独立实现。
- Successor Required: yes
- Successor Path: 未来 Layer 4 plan（L4-1+ IMessageService / call-agent 工具）

## Non-Blocking Follow-ups

- 子 session 的 `forkLabel`（来自 session-engine.md §6.1 ISessionManager `forkSession(parentSessionId, forkLabel)` 签名）—— 当前 props map 可携带任意 label，但无专用字段；如后续需要可加 AgentSession.forkLabel 字段
- fork 时为父 session 生成历史快照（session-and-storage.md §9.2 step 1）—— 当前直接拷贝消息列表，内存模式下无需显式快照对象；VfsSessionStore 实现时再引入 SessionSnapshot

## Closure

Status Note: forkSession 已在 `DefaultAgentEngine` + `InMemorySessionStore` 中功能化实现。`IAgentEngine` 公开 API 表面的最后一个 active UOE 已收口。独立子 agent closure audit 全部 9 项检查通过（PASS），实现完整、接线连通、无空壳、无静默跳过，owner docs 已同步。
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: Independent explore subagent (task_id: ses_13e11b78bffe5RI9ab6caztUyT)
- Audit Session: ses_13e11b78bffe5RI9ab6caztUyT
- Evidence:
  - Exit Criterion "forkSession 不再抛 UOE" — PASS: `DefaultAgentEngine.forkSession` (lines 213–248) 和 `InMemorySessionStore.forkSession` (lines 37–61) 均为完整实现，无 UOE
  - Exit Criterion "fork 语义正确" — PASS: 新 UUID sessionId（InMemorySessionStore:46）、parentSessionId link（:57）、inheritContext 控制消息/planId/metadata 继承（:49–53）
  - Exit Criterion "父子独立性" — PASS: `TestInMemorySessionStoreFork.forkSessionProducesIndependentMessageLists` + `TestDefaultAgentEngineFork.forkSessionThenExecuteChildDoesNotAffectParent`
  - Exit Criterion "SESSION_FORKED 事件" — PASS: `AgentEventType.SESSION_FORKED` 存在（:30），`TestDefaultAgentEngineFork.forkSessionPublishesSessionForkedEvent` 验证 payload
  - Exit Criterion "fail-fast" — PASS: engine 层（:217, :223）+ store 层（:41）均抛 `NopAiAgentException`
  - Anti-Hollow 检查 — PASS: `engine.forkSession → sessionStore.forkSession → store.put → store.get(childId)` 完整调用链在 `forkSessionInheritsMessagesEndToEnd` 端到端测试中验证
  - 接线验证 — PASS: `DefaultAgentEngine.forkSession:235` 调用 `sessionStore.forkSession(...)`，返回值绑定到 `childSessionId`
  - 无静默跳过 — PASS: 无空方法体、无 TODO/FIXME、无 silent return-null；缺失功能均 fail-fast 抛异常
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` — 490 tests, 0 failures, 0 errors
  - Deferred 项分类检查 — PASS: VfsSessionStore / Plan 深拷贝 / SessionSnapshot 持久化 / call-agent 集成均 adjudicated 为 out-of-scope improvement，non-blocking 理由明确

Follow-up:

- VfsSessionStore fork 实现（Successor Required: yes，未来 VFS 持久化 plan）
- SessionSnapshot VFS 持久化（plan 158 后续）
- call-agent 工具 fork+exec 集成（Layer 4 plan）

## Follow-up handled by 166-nop-ai-agent-inter-agent-messenger.md

Plan 166 接管 Deferred 项 "call-agent 工具 fork+exec 集成" 中标注的 successor path "L4-1+ IMessageService" 的底层部分 —— 即建立 Agent 域 messenger 抽象（基于平台 `IMessageService`/`LocalMessageService`）、内存实现、NoOp 默认，并接入 `DefaultAgentEngine`。这是 call-agent 工具与 Actor Runtime（L4-8）所依赖的消息信道基底；call-agent 工具本身仍是 plan 166 之后的独立工作项。plan 166 落地后，"L4-1+ IMessageService" 这一 successor path 的 L4-1 部分被收口。
