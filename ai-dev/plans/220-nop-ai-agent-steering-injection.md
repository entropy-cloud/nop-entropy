# 220 nop-ai-agent Steering 注入机制（Actor Mailbox → ReAct 推理上下文）

> **Plan Status**: active
> **Module**: nop-ai-agent
> **Work Item**: L4-8-steering（plan 218 Non-Blocking Follow-ups 第一条）

> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/218-nop-ai-agent-actor-runtime.md`（Non-Blocking Follow-ups 第一条："Steering 注入机制：将 mailbox 消息注入 ReAct 上下文（需 `AgentExecutionContext` steering queue + design `02-execution-model.md` §四 steering 机制设计裁定）。Classification: successor plan required"）；`ai-dev/design/nop-ai-agent/02-execution-model.md` §四（Steering 机制期望行为与拒绝项）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §10 Phase 2（MessageRouter + steering 接线）
> Related: `218`（交付 Actor Runtime 基础层 + observation-only 消费循环，本计划将其升级为 steering-injection）、`216`（交付 `IMailbox` deferred-ack 邮箱，本计划的 Actor 消费对象）

## Purpose

把 nop-ai-agent 的 Actor mailbox 消费从 plan 218 交付的 **observation-only**（poll → record → ack，消息不注入 ReAct 上下文）升级为 **steering-injection**（poll → enqueue 到 `AgentExecutionContext` steering queue → ack），并在 ReAct 循环中新增 steering 检查点，使外部注入的消息能真正进入 agent 推理上下文。本计划闭合 design `02-execution-model.md` §四定义的 steering 机制——"每轮工具执行后检查是否有外部注入的 steering 消息，如果有，注入消息、进入下一轮推理"——使多 agent 通信主路径（messenger.send → mailbox → Actor 消费 → ReAct 推理）从"可观测但不生效"变为"功能可用"。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **Actor Runtime observation-only 消费 ✅**（plan 218 / L4-8 基础层）：`InMemoryActorRuntime` 在专用单线程上运行 mailbox 消费循环（`poll` → `actor.addReceivedMessage(entry)` + `LOG.info` → `ack`），消息**不**注入 session 或 ctx。plan 218 裁定 6 明确声明 observation-only 为 foundational slice 的有意取舍，steering 注入为 successor。
- **`IMailbox` deferred-ack 邮箱 ✅**（plan 216 / L4-5）：`DefaultAgentEngine.ensureSessionMailbox`（`:1966`）经 `mailboxFactory` 为每个 session 创建 per-session `IMailbox`，三个执行入口点均调用。Actor 消费的是 engine-created 既有 mailbox。
- **`AgentExecutionContext` 无 steering queue**：当前 ctx 有 `messages` 列表（ReAct 循环用 `ctx.getMessages()` 构建 LLM 请求）、`cancelRequested` volatile 标志、`budgetSnapshot`、`tokensUsed` 等字段，但**无** steering 消息队列。grep `steering|Steering|steeringQueue` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 命中。
- **ReAct 循环无 steering 检查点**（`ReActAgentExecutor.execute`）：循环结构为 LLM 调用 → 解析输出 → 工具执行 → 结果写回消息 → 回到 LLM 调用。循环中无"检查外部注入消息"步骤。design `02-execution-model.md` §四定义了 steering 机制语义但从未实现。
- **`AgentSession.messages` 非线程安全**（plan 218 裁定 6 关键理由）：`AgentSession.messages` 是非同步 `ArrayList`。Actor 线程（专用单线程）与 ReAct 线程（ForkJoinPool `supplyAsync`）并发修改会 race。因此 steering 注入**不能**直接 append 到 `session.getMessages()`——需要 ctx 层的线程安全队列。
- **design §四拒绝项**：(1) 通过 Hook 注入 steering 消息——Hook 语义是"增强当前事件"不是"注入新消息流"；(2) 通过修改消息历史注入——绕过引擎主循环，难以保证一致性。本计划须遵守这两条拒绝项。

## Goals

- `AgentExecutionContext` 新增线程安全 steering 消息队列（`ChatMessage` 元素，Actor 线程写、ReAct 线程读），提供 enqueue / drain API。
- ReAct 循环在每轮工具执行完成后、下一轮 LLM 调用前，drain steering queue。若 queue 非空，将 steering 消息追加到 ctx 消息列表（append 新消息，非修改历史），进入下一轮推理。若 queue 为空，正常继续。
- Actor 消费循环（`InMemoryActorRuntime`）从 observation-only 升级为 steering-injection：poll mailbox 消息后将 envelope payload 转换为 `ChatMessage`（首版 String → `ChatUserMessage` role=user）并 enqueue 到 ctx steering queue（消息同时记录到 `receivedMessages` 保持可观测性），然后 ack。
- shipped 默认（`NoOpActorRuntime`，`isEnabled()==false`）下无 Actor 消费循环运行，steering queue 恒空，ReAct 循环 steering 检查为 no-op（drain 空队列 → continue），既有测试零回归。
- design `02-execution-model.md` §四标注 steering 机制已落地；`nop-ai-agent-actor-runtime-vision.md` §10 Phase 2 steering 部分标注已落地。
- focused 测试覆盖：steering queue 线程安全 enqueue/drain、ReAct 循环 steering 注入、Actor 消费循环 steering-injection、NoOp 默认零回归。
- 端到端验证：`engine.execute()` → Actor 创建 → `messenger.send` → mailbox offer → Actor poll → enqueue 到 ctx steering queue → ReAct 循环 drain → steering 消息出现在下一轮 LLM 请求的消息列表 → LLM 看到 steering 消息。

## Non-Goals

- **call-agent 异步 mailbox 模型**（vision §6 / §10 Phase 2 剩余）：将 `call-agent` 工具从 fork+exec 迁移到 Actor mailbox REQUEST/RESPONSE。本计划只交付 steering 注入基础机制（消息注入到 ReAct 上下文），call-agent 的异步化是独立行为变更 successor。Classification: successor plan required。
- **Mid-round steering（工具执行中途打断）**：design §四提到"跳过当前剩余工具"，但 foundational slice 在 round 边界（所有当前轮工具执行完成后）检查 steering。Mid-round 打断（多个并行工具执行中、steering 到达时跳过剩余工具）是独立增强。Classification: optimization candidate。
- **Steering 消息角色分级**（system vs user vs tool-result）：foundational slice 统一赋 role=user（`SendMessageExecutor` 当前投递 String payload 无角色元数据，裁定 6）。发送方如需 system 级 steering，须在 payload 中约定结构化格式。角色分级是独立增强。Classification: optimization candidate。
- **TeamManager / TeamSpec DSL**（vision §8 / §10 Phase 3）：团队生命周期 + 成员编排。本计划只交付 steering 管道，不构建团队管理。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` 增加 steering 相关配置。design §四明确"当前不需要 DSL 支持"。Classification: optimization candidate。
- **steering 消息持久化到 session**：steering 消息注入到 ctx 内存消息列表用于当次执行，不持久化到 `nop_ai_session_message` 表。持久化是独立 successor（需 SEQ 协调，见 plan 205 Non-Blocking Follow-ups）。Classification: successor plan required。
- **多 steering 源**（非 Actor 路径的外部 steering API）：foundational slice 只接通 Actor mailbox → steering queue 路径。直接 API 层 steering（不经 Actor）是独立增强。Classification: optimization candidate。

## Scope

### In Scope

- `AgentExecutionContext` 新增 steering 消息队列字段 + enqueue/drain API
- `ReActAgentExecutor` ReAct 循环新增 steering 检查点（round 边界 drain + 注入）
- `InMemoryActorRuntime` 消费循环从 observation-only 升级为 steering-injection
- Actor → ctx steering queue 的引用传递机制（ReAct 执行入口将 ctx steering queue 关联到 Actor）
- design `02-execution-model.md` §四 + `nop-ai-agent-actor-runtime-vision.md` §10 更新
- focused 测试 + 端到端测试

### Out Of Scope

- 见 Non-Goals（call-agent 异步 / mid-round steering / 角色分级 / TeamManager / XDSL / 持久化 / 多 steering 源 均为显式 successor）

### 设计裁定

**裁定 1：Steering queue 位于 `AgentExecutionContext`（非 `AgentSession`）**——steering 消息队列放在 ctx 上（`ConcurrentLinkedQueue`），不在 session 上。理由：(1) ReAct 循环经 `ctx.getMessages()` 构建 LLM 请求（非 `session.getMessages()`），steering queue 在 ctx 上与消息列表同一访问域；(2) `AgentSession.messages` 是非同步 `ArrayList`（plan 218 裁定 6），不能并发修改；(3) ctx 是 per-execution 实例，steering queue 随执行结束自然回收，无需额外清理。

**裁定 2：Actor → ctx steering queue 引用传递**——ctx 由 `DefaultAgentEngine.buildBaseExecutionContext` 在 `supplyAsync` **之前**创建，作为参数传入 `ReActAgentExecutor.execute(ctx)`。在 `supplyAsync` lambda 内，`createActor`（返回 `AgentActor`）在 `execute(ctx)` **之前**调用——此时 ctx 已存在。因此关联 queue 的正确位置是 engine lambda 内 `createActor` 返回后、`execute(ctx)` 调用前：直接用 `createActor` 的返回值（无需经 `getActorBySession` 反查），调用 `actor.setSteeringQueue(ctx.getSteeringQueue())`。Actor 消费循环在 enqueue 前检查 steering queue 引用是否已关联（null = Actor 线程在关联前已 poll 到消息 → 退化为 observation-only record + ack，不丢弃消息；关联后切换为 steering-injection）。Actor 持有的 queue 引用字段须为 `volatile`（engine 线程写、Actor 消费线程读，保证可见性）。

**裁定 3：Steering 检查点 = round 边界（所有当前轮工具执行完成后）**——ReAct 循环在每轮工具执行完成（所有工具调用结果已写回消息列表）后、进入下一轮 LLM 调用前，drain steering queue。drain 出的消息 append 到 ctx 消息列表，然后进入下一轮推理。若 drain 结果为空，正常继续。理由：(1) 与 design §四"每轮工具执行后检查"一致；(2) round 边界注入避免中途打断并行工具执行（mid-round 是 Non-Goal optimization candidate）；(3) drain 语义保证一次性取走所有排队消息，避免逐条检查的竞态。

**裁定 4：Steering 注入 = append 新消息（非修改历史）**——drain 出的 steering 消息作为新消息 append 到 ctx 消息列表末尾，不修改已有消息。理由：design §四显式拒绝"通过修改消息历史注入"模式——steering 消息是新追加的消息，不是对已有消息的修改。这与 `ctx.getMessages()` 的 append-only 使用模式一致。

**裁定 5：Steering queue 元素类型 = ChatMessage + MailboxEntry→ChatMessage 转换点**——steering queue 持有 `ChatMessage` 对象（与 ctx 消息列表元素类型一致，drain 后可直接 append）。Actor 消费循环在 `poll` 取出 `MailboxEntry` 后、`enqueue` 前，将 envelope payload（`Object`，实际经 `SendMessageExecutor` 投递时为 String `messageBody`）转换为 `ChatMessage`——首版默认构造 `ChatUserMessage`（role=user）。理由：(1) queue 元素类型与 ctx 消息列表一致，drain → append 无需二次转换；(2) 转换发生在 Actor 消费线程（消息离开 mailbox 的唯一出口），职责清晰；(3) `SendMessageExecutor` 当前投递 String payload（无角色元数据），首版统一赋 role=user 使 steering 消息与用户指令同级——发送方如需 system 级 steering，须在 payload 中约定结构化格式（Non-Goal"角色分级" successor）。Non-Goal 3"角色由发送方设定"修正为：**首版统一 role=user**，角色分级为 successor（当前 payload 为 String，无角色信息可透传）。

**裁定 6：Shipped 默认零回归**——`NoOpActorRuntime`（`isEnabled()==false`）下无 Actor 消费循环运行，ctx steering queue 恒空，ReAct 循环 steering 检查 drain 空队列 → continue（开销为一次 `queue.isEmpty()` 检查）。不引入 WARN（steering 缺失非安全风险，与 Actor Runtime / IMailbox 裁定一致）。

## Execution Plan

### Phase 1 - Steering queue 契约 + 设计裁定落档 + 引用传递机制

Status: planned
Targets: `AgentExecutionContext`（新增 steering queue 字段 + API）、`AgentActor`（新增 steering queue 引用关联）、design `02-execution-model.md` §四、`nop-ai-agent-actor-runtime-vision.md` §10

- Item Types: `Decision` | `Proof`

- [ ] **裁定并落档** 裁定 1-5（steering queue 位置/引用传递/检查点/注入语义/默认行为）写入设计文档
- [ ] `AgentExecutionContext` 新增线程安全 steering 消息队列字段（`ConcurrentLinkedQueue` 或等价线程安全结构）+ `enqueueSteering(message)` / `drainSteering() → List` API；Javadoc 明确线程安全契约（Actor 线程 enqueue、ReAct 线程 drain）
- [ ] `AgentActor` 新增可选 steering queue 引用字段 + `setSteeringQueue(queue)` / `getSteeringQueue()` 访问器；Javadoc 明确引用关联时机（ReAct 执行入口在 ctx 创建后关联）与 null 退路（未关联时消费循环退化为 observation-only）
- [ ] `DefaultAgentEngine` 三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）的 `supplyAsync` lambda 中，在 `createActor` 返回 `AgentActor` 后、`execute(ctx)` 调用前（ctx 此时已由 `buildBaseExecutionContext` 在 `supplyAsync` 前创建），将 ctx steering queue 关联到 Actor（直接用 `createActor` 返回值调用 `actor.setSteeringQueue(ctx.getSteeringQueue())`，无需 getActorBySession 反查）；NoOp 默认下不执行（`isEnabled()==false`）
- [ ] `02-execution-model.md` §四**改写**以区分已落地与 successor 部分：round 边界检查（landed）与"跳过当前剩余工具"/ mid-round（successor），消除"声称跳过但实际不跳"的内部矛盾；标注 steering queue 位置（ctx）与检查点语义
- [ ] `nop-ai-agent-actor-runtime-vision.md` §10 Phase 2 steering 部分标注已落地

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `AgentExecutionContext` 含 steering queue 字段 + enqueue/drain API，Javadoc 明确线程安全契约
- [ ] `AgentActor` 含 steering queue 引用字段 + set/get 访问器，Javadoc 明确关联时机与 null 退路
- [ ] `DefaultAgentEngine` 三个入口点在 `createActor` 返回后、`execute(ctx)` 前关联 Actor steering queue（直接用返回值；`isEnabled()==true` 时；NoOp 时跳过）
- [ ] `02-execution-model.md` §四已改写区分 round 边界检查（landed）vs mid-round 跳过（successor）+ `nop-ai-agent-actor-runtime-vision.md` §10 标注已落地
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ReAct 循环 steering 检查 + Actor steering-injection 消费 + 测试

Status: planned
Targets: `ReActAgentExecutor`（steering 检查点）、`InMemoryActorRuntime`（steering-injection 消费循环）、测试

- Item Types: `Proof` | `Follow-up`

- [ ] `ReActAgentExecutor` ReAct 循环新增 steering 检查点：每轮工具执行完成（所有工具调用结果已写回 ctx 消息列表）后、进入下一轮 LLM 调用前，`ctx.drainSteering()` → 若结果非空，将 steering 消息 append 到 ctx 消息列表 → 进入下一轮推理；若结果为空，正常继续。drain 在 ReAct 线程执行（与 Actor 线程经 ConcurrentLinkedQueue 无锁协调）
- [ ] `InMemoryActorRuntime` Actor 消费循环升级：`poll` mailbox 消息后，若 Actor 的 steering queue 引用已关联（非 null）→ 将 MailboxEntry envelope payload 转换为 `ChatMessage`（首版 String payload → `ChatUserMessage` role=user，裁定 5）→ `enqueueSteering(chatMessage)` 到 ctx queue（同时保留 `receivedMessages` 记录用于可观测性）→ `ack`；若 steering queue 引用为 null → 退化为 observation-only（record + ack，不丢弃消息）。poll/ack 异常 → log + 状态转 FAILED（非静默吞没，与 plan 218 一致）
- [ ] 编写 focused 测试：steering queue enqueue/drain 线程安全（多线程 enqueue + 单线程 drain 结果一致）、ReAct 循环 steering 注入（drain 非空 → 消息出现在下一轮 LLM 请求消息列表）、Actor steering-injection 消费（poll → enqueue → ack）、Actor queue 未关联退路（null → observation-only record + ack）
- [ ] 编写端到端测试：配置 `InMemoryActorRuntime` + `DeferredAckMailbox` + `LocalAgentMessenger` → `engine.execute()` → Actor 创建 + queue 关联 → `messenger.send` → mailbox offer → Actor poll → enqueue 到 ctx steering queue → ReAct 循环 drain → steering 消息出现在下一轮 LLM 请求 → LLM 看到 steering 消息（mock LLM 断言请求消息列表含 steering 消息）
- [ ] 编写零回归测试：`NoOpActorRuntime` 默认 → steering queue 恒空 → ReAct 循环 steering 检查 no-op → 既有行为不变

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `ReActAgentExecutor` 在每轮工具执行后、下一轮 LLM 调用前 drain steering queue 并注入（drain 非空 → append steering 消息 → 下一轮推理；drain 空 → 正常继续）
- [ ] `InMemoryActorRuntime` Actor 消费循环执行 steering-injection（poll → enqueue 到 ctx queue + record → ack）；queue 未关联时退化为 observation-only（record + ack，不丢弃）
- [ ] **接线验证**（Minimum Rules #23）：Actor 消费循环经 `AgentActor.getSteeringQueue()` 向 ctx steering queue enqueue（断言 drain 结果非空）；ReAct 循环经 `ctx.drainSteering()` 读取（断言注入消息出现在 LLM 请求消息列表）
- [ ] **端到端验证**（Minimum Rules #22）：从 `engine.setActorRuntime(new InMemoryActorRuntime(engine))` + `engine.setMessenger(...)` 入口，经 Actor 创建 + queue 关联 → `messenger.send` → mailbox → Actor poll → enqueue → ReAct drain → steering 消息出现在下一轮 LLM 请求消息列表的完整路径跑通
- [ ] **无静默跳过**（Minimum Rules #24）：steering queue 未关联时 Actor 退化为 observation-only record + ack（不静默丢弃消息）；drain 空队列正常 continue（非异常路径）；poll/ack 异常 log + 状态转 FAILED
- [ ] shipped 默认（`NoOpActorRuntime`，`isEnabled()==false`）下既有测试零回归（steering queue 恒空，ReAct steering 检查 no-op）
- [ ] 新增功能各有对应 focused 测试覆盖（steering queue 线程安全 / ReAct steering 注入 / Actor steering-injection / queue 未关联退路 / NoOp 零回归各有测试）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `AgentExecutionContext` steering queue（线程安全 enqueue/drain）已落地
- [ ] ReAct 循环 steering 检查点（round 边界 drain + append 注入）已落地
- [ ] Actor 消费循环 steering-injection（poll → enqueue → ack + queue 未关联退路）已落地
- [ ] Actor → ctx steering queue 引用传递（DefaultAgentEngine 三入口点关联）已落地
- [ ] 端到端：engine.execute → Actor 创建 + queue 关联 → messenger.send → mailbox → Actor poll → enqueue → ReAct drain → steering 消息出现在下一轮 LLM 请求，完整路径跑通
- [ ] shipped 默认（NoOp，`isEnabled()==false`）下既有测试零回归
- [ ] 必要 focused verification 已完成（steering queue 线程安全 / ReAct steering 注入 / Actor steering-injection / queue 未关联退路 / NoOp 零回归各有测试）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（call-agent 异步 / mid-round steering / 角色分级 / TeamManager / XDSL / 持久化 / 多 steering 源 均显式在 Non-Goals 切出）
- [ ] 受影响 owner docs（`02-execution-model.md` §四 + `nop-ai-agent-actor-runtime-vision.md` §10）已同步到 live baseline
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）Actor 消费循环经 steering queue 向 ctx enqueue（不只类型存在），（b）ReAct 循环经 drain 读取并注入到 LLM 请求消息列表（不只是 record），（c）无空方法体/静默跳过作为正常实现
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；call-agent 异步 / mid-round steering / 角色分级 / TeamManager / XDSL / 持久化 / 多 steering 源均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **call-agent 异步 mailbox 模型**（vision §6 / §10 Phase 2 剩余）：将 `call-agent` 从 fork+exec 迁移到 Actor mailbox REQUEST/RESPONSE，消费本计划交付的 steering 管道。Classification: successor plan required。
- **Mid-round steering（工具执行中途打断）**：design §四"跳过当前剩余工具"的完整实现。Foundational slice 在 round 边界注入。Classification: optimization candidate。
- **Steering 消息持久化到 session**：steering 消息写入 `nop_ai_session_message` 表。Classification: successor plan required（需 SEQ 协调，见 plan 205）。
- **TeamManager + TeamSpec DSL**（vision §8 / §10 Phase 3）：团队生命周期 + 成员编排。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` 增加 steering 相关配置。design §四明确当前不需要。Classification: optimization candidate。

## Closure

Status Note: (待关闭时填写)
