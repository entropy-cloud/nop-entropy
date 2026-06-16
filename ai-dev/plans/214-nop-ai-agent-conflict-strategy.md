# 214 nop-ai-agent IConflictStrategy 冲突解决策略接口 + FailFastStrategy 默认实现（L2-13a）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-13a
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2（L2-13a ❌ 未实现）；`ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md` §3.1 + §4.4（IConflictStrategy 接口契约 + FailFastStrategy 默认 + 写意图注册表 Phase 1 简化机制）；`ai-dev/design/nop-ai-agent/glossary.md`（IConflictStrategy = Layer 2，默认 FailFastStrategy，扩展 CoordinationBusStrategy）
> Related: L1-1（IAgentEngine + DefaultAgentEngine ✅ 依赖已满足）、L4-1b（call-agent 工具 ✅，多 Agent 委派入口）、L4-8（Actor Runtime ❌，跨进程协调的 successor）

## Purpose

为 nop-ai-agent 引入 `IConflictStrategy` 冲突解决策略扩展点接口、`FailFastStrategy` shipped 默认实现、`WriteIntent` / `ConflictResult` 数据对象，以及进程内 `InMemoryWriteIntentRegistry` 写意图注册表，并将其接线到 `DefaultAgentEngine` 和 `ReActAgentExecutor` 的工具分发路径——使多个 Agent 并行执行时对同一文件的写冲突可被检测和策略化处理。本计划闭合 Layer 2 最后一个 ❌ 项（L2-13a），使 Layer 2 全部接口具备 pass-through 或功能性默认实现。当前多 Agent 冲突检测完全缺失——grep `IConflictStrategy|WriteIntent|ConflictResult|writeIntent` 在 nop-ai-agent main source 中零命中。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`IConflictStrategy` / `WriteIntent` / `ConflictResult` / `ConflictDecision` 全部不存在**（grep 确认零结果）。`IWriteIntentRegistry` / `InMemoryWriteIntentRegistry` 同样不存在。
- **Layer 2 唯一未实现项**：roadmap §4 Layer 2 表格中 L2-1 ~ L2-14a、L2-15 ~ L2-23 全部 ✅，仅 L2-13a 标 ❌。Layer 2 验收标准"所有接口有 pass-through 默认"由此项阻断。
- **设计契约已定义**（`nop-ai-agent-multi-agent.md` §4.4）：`IConflictStrategy` 接口含 `ConflictResult resolve(WriteIntent current, Set<WriteIntent> existing)` 方法。`FailFastStrategy`（默认）检测到冲突直接拒绝；`CoordinationBusStrategy`（扩展，Non-Goal）通过协调信道广播实现 LLM 智能协调。
- **写意图注册表为 Phase 1 简化机制**（§3.1）：引擎维护一个进程内注册表，工具执行写操作前先注册写意图，若同一文件已有其他 session 的写意图则委托给 `IConflictStrategy` 处理。Phase 1 用内存注册表 + fail-fast；Phase 2+ 扩展为协调信道（依赖 L4-8 Actor Runtime）。
- **工具分发路径安全检查链已就位**（`ReActAgentExecutor.java` dispatch loop）：Layer 3 post-denial-guard（`:1388`）→ Layer 1 tool access（`:1417`）→ Layer 1 permission（`:1441`）→ Layer 1 path access（`:1465`）→ Layer 2 security level + permission matrix（`:1489` checkLayer2Consultation）→ Layer 3 approval gate（`:1512` checkLayer3Approval）→ `allowedCalls.add`（`:1529`）。冲突检测作为 Layer 2 扩展，接入点在 Layer 3 approval gate 之后、`allowedCalls.add` 之前。
- **写工具识别基础设施已存在**：`DefaultToolAccessChecker` 硬编码 deny-list 含 write 类工具名（`write-file` / `patch-file` / `apply-delta` 等）。`ToolPathArgKeys.KEYS` 定义了路径参数键集合（`path` / `file` / `filePath` / `filename` / `directory` / `dir` / `destination` / `output` / `input` / `source` / `target` / `cwd`），被 `DefaultLevelHintsProducer.evaluateWritesOutside` 和 path access checker 共用。冲突检测复用 `ToolPathArgKeys` 提取写工具的目标路径。
- **Layer 2/3 组件 engine 接线模式已固化**（plan 200/201 等确立）：`DefaultAgentEngine` mutable 字段（非 `final`）+ setter（null 兜底为 shipped 默认）+ `resolveExecutor` Builder 链传递 + `ReActAgentExecutor.Builder` 字段 + setter + `build()` null 兜底 + 构造器参数 + 构造器 null 兜底。
- **deny 路径处理模式已固化**：dispatch loop 中每个安全检查的 deny 分支统一为 `ChatToolResponseMessage.error(...)` + `ctx.addMessage(...)` + `handleDenialAndCheckThreshold(...)`（审计 + 事件 + 拒绝计数）+ `continue` 或 `break dispatchLoop`。冲突 deny 路径复用同一模式。
- **session 生命周期清理点已存在**：`DefaultAgentEngine` 的 `doExecute`、`resumeSession`、`restoreSession` 三个执行入口点的 finally 块执行 `runningExecutions.remove(...)` 等清理（finally 块位置需在执行时按方法名 grep 确认，行号随版本漂移）。写意图释放在此处追加。
- **call-agent 为同步 fork+exec**（L4-1b ✅）：`CallAgentExecutor` 直接调用 `engine.execute()` 同步等待子 Agent 完成。当前无真正并行——冲突检测基础设施为 Actor Runtime 时代的并行场景预备，但在当前单进程模型中仍可被多线程 `execute()` 调用触发。
- **roadmap §4 Layer 2 验收标准**：L2-13a 是最后一个 ❌ 项，关闭后 Layer 2 验收标准"能清楚说明每个扩展如何替换 pass-through 默认"完全满足。

## Goals

- `IConflictStrategy` 接口存在，含 `ConflictResult resolve(WriteIntent current, Set<WriteIntent> existing)` 方法，语义为"给定当前写意图和已存在的冲突写意图集合，返回冲突解决决策"。
- `WriteIntent` 数据对象存在，包含 sessionId、agentName、filePath（规范化绝对路径）、operation（写操作类型标识）、timestamp 字段。
- `ConflictResult` 数据对象 + `ConflictDecision` 枚举（`ALLOW` / `DENY`）存在，含 decision + reason 字段。
- `FailFastStrategy` shipped 默认实现存在：`existing` 为空或全部来自同一 session 时返回 `ALLOW`；`existing` 含其他 session 的写意图时返回 `DENY`（fail-fast）。
- `IWriteIntentRegistry` 接口 + `InMemoryWriteIntentRegistry` 进程内实现存在：`registerAndGetConflicting(WriteIntent)` 原子操作（在单一锁内注册写意图并返回其他 session 对同一文件的已有写意图集合）、`releaseSession(String sessionId)` 释放 session 的全部写意图。原子操作保证并发 check-then-register 无 TOCTOU 竞态。
- `IConflictStrategy` + `IWriteIntentRegistry` 接线到 `DefaultAgentEngine`（mutable 字段 + setter + resolveExecutor Builder 链）和 `ReActAgentExecutor`（Builder 字段 + setter + 构造器 + null 兜底）。
- dispatch loop 在 Layer 3 approval gate 之后、`allowedCalls.add` 之前，对含路径参数的工具执行冲突检测：提取路径 → 原子注册并查冲突（`registerAndGetConflicting`）→ 若有冲突则委托 `IConflictStrategy` → 若 denied 走标准 deny 路径；若 allowed 或无冲突则放行。
- `execute()` / `resumeSession()` / `restoreSession()` 调用结束（finally 块）时调用 `releaseSession` 释放写意图。
- shipped 默认行为零回归（单 session 场景）：单 session 执行时注册表中无其他 session 的写意图 → `getConflicting` 返回空集 → `FailFastStrategy` 返回 `ALLOW` → 冲突检测为 no-op。**已知限制**：多 session 并发场景下，含路径参数的非写工具（如 `read-file`）也会注册写意图并可能触发 `FailFastStrategy` 的 false-positive DENY。这是 Phase 1 路径参数识别策略的保守取舍（宁可误拒也不放过写冲突），tool-name 级别的写工具精确分类（仅对实际写工具注册意图）是 successor 增强（见 Non-Blocking Follow-ups）。
- Focused 测试验证：FailFastStrategy 行为（无冲突 ALLOW / 有跨 session 冲突 DENY / 同 session 不冲突）、InMemoryWriteIntentRegistry 行为（register / getConflicting / releaseSession）、dispatch-path 接线（写工具冲突 → deny + 审计事件、非写工具不触发冲突检测、单 session 零回归）、session 结束释放写意图。
- roadmap §4 Layer 2 表格 L2-13a 从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **`CoordinationBusStrategy`**（设计 §4.4 扩展策略）：通过协调信道广播 scope_claim / operation_intent 实现 LLM 智能协调 + 引擎级预警。需要 `IMessageService` topic 基础设施 + 消息注入 + 模式匹配，是独立大 work item。Classification: successor plan required（Phase 2+ 多 Agent 协调）。
- **跨进程写意图共享**：`InMemoryWriteIntentRegistry` 为进程内实现。跨进程共享（如 DB-backed registry）依赖 L4-8 Actor Runtime。Classification: successor plan required。
- **资源竞争检测**（设计 §3.2 共享资源竞争）：CPU / 内存 / 端口争抢检测，需要工具声明资源需求的基础设施，与文件写冲突正交。Classification: out-of-scope improvement。
- **上下文依赖冲突**（设计 §3.3）：Plan 引擎调度范畴，不是 Agent 引擎核心职责。Classification: out-of-scope。
- **XDSL 配置化**：`agent.xdef` `<conflict-strategy>` 元素绑定。本计划交付程序化行为，配置化是独立增强。Classification: optimization candidate。
- **写意图持久化**：`InMemoryWriteIntentRegistry` 不持久化（进程重启丢失）。持久化（如 `ai_agent_write_intent` 表）是独立 successor。Classification: optimization candidate。
- **`warnIfInsecureDefaults` 扩展**：IConflictStrategy 不是安全组件（冲突检测是协调能力，不是安全边界），FailFastStrategy 是功能性默认（非 insecure pass-through），无需 WARN。Classification: no WARN required。

## Scope

### 设计裁定

**写工具识别策略**：dispatch-path 冲突检测仅对"含路径参数的工具调用"触发——从 `chatToolCall.getArguments()` 中提取 `ToolPathArgKeys.KEYS` 对应的路径值（非空 String），有路径则视为写意图候选。不做 tool-name 分类（避免维护写工具名列表与 `DefaultToolAccessChecker` deny-list / `DefaultLevelHintsProducer` 分类集重复且不同步）。理由：(1) 冲突检测关注的是"文件写意图"，路径参数是写意图的充分信号；(2) 只含路径但不写文件的工具（如 read-file）也会注册写意图，但 `FailFastStrategy` 的 fail-fast 语义对此是保守安全的（宁可误拒跨 session 对同一文件的并发读写，也不放过写冲突）；(3) tool-name 分类属于 LevelHints 基础设施职责，复用引入跨包耦合且分类口径不一致。

**路径规范化规则**：`WriteIntent.filePath` 的规范化复用 `DefaultPathAccessChecker.normalizePathStatic()`（与 Layer 1 path checker 和 `DefaultLevelHintsProducer.evaluateWritesOutside` 完全一致）。具体流程：(1) 从 `chatToolCall.getArguments()` 中按 `ToolPathArgKeys.KEYS` 提取 String 值；(2) 相对路径解析为绝对路径——基准目录为 `agentModel.getWorkDir()`（经 `ReActAgentExecutor.resolveWorkDir(agentModel)` helper 解析为 `File`，非 null 时），否则 JVM CWD（`new File(".").getAbsoluteFile()`），与 `DefaultLevelHintsProducer.evaluateWritesOutside` 的基准解析逻辑一致；(3) 调用 `DefaultPathAccessChecker.normalizePathStatic(absolutePath)` 得到规范化字符串作为 registry key。理由：(1) 与 Layer 1 path checker 使用同一规范化函数保证两个检查层对"同一文件"的判定一致；(2) 避免引入新的规范化逻辑导致跨层语义漂移。

**多路径工具调用处理**：一个工具调用可能含多个 `ToolPathArgKeys.KEYS` 命中的路径参数（如 `copy-file` 含 `source` + `destination`）。每个提取到的路径值生成一个独立的 `WriteIntent`（`operation` = 工具名，`filePath` = 该路径值的规范化绝对路径）。冲突检测对每个 WriteIntent 独立执行（任一路径冲突则整个工具调用被 deny）；注册时全部 WriteIntent 都注册。

**写意图注册时机**：在冲突检测通过后、`allowedCalls.add` 之前注册（而非工具执行后）。理由：(1) 注册表的语义是"写意图"而非"写已完成"——其他 Agent 需要在工具执行前感知冲突；(2) 在 approval gate 之后注册避免对将被拒绝的工具调用产生注册副作用。

**ConflictResult 不含 ConflictResolved 事件**：glossary.md 定义了 `ConflictResolved` Layer 3 事件（含策略名称、决策结果）。本计划不引入新事件类型——冲突 deny 路径复用现有 `TOOL_CALL_DENIED` 事件（matchedRule = `"layer2_conflict_strategy"`），冲突 allow 不产生事件（与其他 allow 路径一致）。`ConflictResolved` 事件是审计增强 successor。Classification: optimization candidate。

### In Scope

- 新增 `DenialLayerSource.LAYER2_CONFLICT_STRATEGY` 枚举值（冲突 deny 专用，不复用 `LAYER2_SECURITY_POLICY`——IConflictStrategy 是协调能力，不是安全组件）
- 新增 `IConflictStrategy` 接口（含 `ConflictResult resolve(WriteIntent current, Set<WriteIntent> existing)` 方法）
- 新增 `WriteIntent` 数据对象（sessionId、agentName、filePath、operation、timestamp）
- 新增 `ConflictDecision` 枚举（`ALLOW` / `DENY`）+ `ConflictResult` 数据对象（decision、reason）
- 新增 `FailFastStrategy` shipped 默认实现
- 新增 `IWriteIntentRegistry` 接口（`register` / `getConflicting` / `releaseSession`）+ `InMemoryWriteIntentRegistry` 进程内实现
- `DefaultAgentEngine`：新增 `conflictStrategy` + `writeIntentRegistry` mutable 字段（默认 `FailFastStrategy` + `InMemoryWriteIntentRegistry`）+ setter + resolveExecutor Builder 链传递
- `ReActAgentExecutor`：新增 `conflictStrategy` + `writeIntentRegistry` 字段 + 构造器参数 + null 兜底 + Builder 字段 + setter + build() null 兜底
- `ReActAgentExecutor` dispatch loop：Layer 3 approval gate 之后、`allowedCalls.add` 之前新增冲突检测步骤（提取路径 → 查注册表 → 委托策略 → deny 走标准路径 / allow 注册写意图）
- `DefaultAgentEngine`：`doExecute` / `resumeSession` / `restoreSession` finally 块调用 `writeIntentRegistry.releaseSession(sessionId)`
- Focused 测试（见 Phase 2 Exit Criteria）
- 设计文档 `nop-ai-agent-multi-agent.md` §4.4 标注实现状态
- roadmap §4 Layer 2 表格 L2-13a 状态更新

### Out Of Scope

- `CoordinationBusStrategy` / 跨进程 registry / 资源竞争检测 / 上下文依赖冲突 / XDSL 配置化 / 写意图持久化（理由见 Non-Goals）

## Execution Plan

### Phase 1 - 接口 + 数据对象 + FailFastStrategy + InMemoryWriteIntentRegistry

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/conflict/`（新包）

- Item Types: `Fix`（接口缺失 = contract gap）

- [x] 新增 `IConflictStrategy` 接口，含 `ConflictResult resolve(WriteIntent current, Set<WriteIntent> existing)` 方法。javadoc 标注：`current` 为当前工具调用的写意图；`existing` 为注册表中其他 session 对同一文件的已有写意图集合（空集表示无冲突）；返回 `ConflictResult`
- [x] 新增 `WriteIntent` 数据对象：`String sessionId`、`String agentName`、`String filePath`（规范化绝对路径）、`String operation`（工具名）、`long timestamp`（`System.currentTimeMillis()`）
- [x] 新增 `ConflictDecision` 枚举：`ALLOW`、`DENY`
- [x] 新增 `ConflictResult` 数据对象：`ConflictDecision decision`、`String reason`、`String strategyName`；提供 `allow(strategyName)` / `deny(strategyName, reason)` 静态工厂
- [x] `FailFastStrategy` shipped 默认实现：`existing` 为空或全部 `WriteIntent.sessionId` 等于 `current.sessionId` 时返回 `ALLOW`；否则返回 `DENY`（reason 含冲突文件路径 + 冲突 session 列表）。Singleton 模式 + `failFast()` 静态工厂
- [x] 新增 `IWriteIntentRegistry` 接口：`Set<WriteIntent> registerAndGetConflicting(WriteIntent intent)`（原子操作：在单一锁内插入当前意图并返回其他 session 对同一文件的已有写意图集合）/ `void releaseSession(String sessionId)`
- [x] 新增 `InMemoryWriteIntentRegistry` 进程内实现：`ConcurrentHashMap<String, CopyOnWriteArrayList<WriteIntent>>` 按 filePath 分组（key = 规范化路径）。`registerAndGetConflicting` 在 `synchronized` 块内执行：追加到对应路径列表 + 返回同路径下 `sessionId != intent.sessionId` 的全部意图。`releaseSession` 遍历全部路径列表移除该 session 的意图。原子操作保证两个并发 `registerAndGetConflicting` 调用不会同时看到空冲突集

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IConflictStrategy.java`、`WriteIntent.java`、`ConflictDecision.java`、`ConflictResult.java`、`FailFastStrategy.java`、`IWriteIntentRegistry.java`、`InMemoryWriteIntentRegistry.java` 文件存在于 `conflict/` 包下
- [x] `FailFastStrategy.resolve`：空 existing → `ALLOW`；全同 session existing → `ALLOW`；含其他 session existing → `DENY`（reason 含路径 + session 列表）
- [x] `InMemoryWriteIntentRegistry`：registerAndGetConflicting 后返回其他 session 意图且原子注册；releaseSession 后 registerAndGetConflicting 返回空
- [x] 新增 `TestFailFastStrategy` 单元测试：无冲突 ALLOW / 同 session 不冲突 ALLOW / 跨 session 冲突 DENY + reason 验证
- [x] 新增 `TestInMemoryWriteIntentRegistry` 单元测试：registerAndGetConflicting + releaseSession + 并发原子性验证（两个线程并发 registerAndGetConflicting 同一文件、不同 session → 至少一个线程看到对方的冲突意图，验证 TOCTOU 安全）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] No owner-doc update required（此 Phase 仅创建新组件，不改 live baseline；文档更新在 Phase 2 统一完成）

### Phase 2 - 引擎接线 + Dispatch-Path 冲突检测 + Session 清理 + 测试

Status: completed
Targets: `engine/DefaultAgentEngine.java`、`engine/ReActAgentExecutor.java`、设计文档、roadmap

- Item Types: `Fix`（接线缺失 = hollow implementation gap）、`Proof`

- [x] `DenialLayerSource` 枚举新增 `LAYER2_CONFLICT_STRATEGY` 值（javacdoc 标注：`IConflictStrategy` 冲突检测 deny 专用）
- [x] `DefaultAgentEngine` 新增 `private IConflictStrategy conflictStrategy = FailFastStrategy.failFast();` 字段
- [x] `DefaultAgentEngine` 新增 `private IWriteIntentRegistry writeIntentRegistry = new InMemoryWriteIntentRegistry();` 字段
- [x] `DefaultAgentEngine` 新增 `setConflictStrategy` setter（null 兜底 `FailFastStrategy.failFast()`）+ `getConflictStrategy` getter
- [x] `DefaultAgentEngine` 新增 `setWriteIntentRegistry` setter（null 兜底 `new InMemoryWriteIntentRegistry()`）+ `getWriteIntentRegistry` getter
- [x] `DefaultAgentEngine.resolveExecutor` Builder 链增加 `.conflictStrategy(conflictStrategy)` + `.writeIntentRegistry(writeIntentRegistry)`
- [x] `ReActAgentExecutor.Builder` 新增 `conflictStrategy` + `writeIntentRegistry` 字段 + setter
- [x] `ReActAgentExecutor.Builder.build()` 新增 null 兜底（`FailFastStrategy.failFast()` / `new InMemoryWriteIntentRegistry()`）
- [x] `ReActAgentExecutor` 构造器新增 `IConflictStrategy conflictStrategy` + `IWriteIntentRegistry writeIntentRegistry` 参数 + 字段赋值 + null 兜底
- [x] `ReActAgentExecutor` dispatch loop：Layer 3 approval gate check（`:1512-1527`）之后、`allowedCalls.add`（`:1529`）之前，新增冲突检测步骤：从 `chatToolCall.getArguments()` 中按 `ToolPathArgKeys.KEYS` 提取路径值（按设计裁定"路径规范化规则"规范化为绝对路径、按"多路径工具调用处理"每个路径生成独立 WriteIntent）；对每个 WriteIntent 调用 `writeIntentRegistry.registerAndGetConflicting(intent)` → 若返回非空调用 `conflictStrategy.resolve(intent, conflicting)` → 若 `DENY` 走标准 deny 路径（`ChatToolResponseMessage.error` + `ctx.addMessage` + `handleDenialAndCheckThreshold`（`DenialLayerSource.LAYER2_CONFLICT_STRATEGY`，matchedRule = `"layer2_conflict_strategy"`） + `publishEvent(TOOL_CALL_DENIED)` + `auditLogger.log(DENY)` + `continue`）；若全部 `ALLOW` 或无冲突则放行（意图已在 `registerAndGetConflicting` 中原子注册）
- [x] `DefaultAgentEngine` 的 `doExecute` / `resumeSession` / `restoreSession` finally 块：追加 `writeIntentRegistry.releaseSession(sessionId)` 调用（释放 session 写意图）
- [x] 新增 `TestConflictStrategyWiring`：注入 test-double `RecordingConflictStrategy`（记录 resolve 调用）+ `InMemoryWriteIntentRegistry` 到 `DefaultAgentEngine`，运行 ReAct 循环调用含路径参数的工具，断言 dispatch-path 冲突检测被触发、`resolve()` 被调用、单 session 无冲突时 `ALLOW` 返回
- [x] 新增 `TestConflictDetectionDispatchPath` 端到端测试（采用确定性 pre-populate-registry 方法避免 flaky 并发测试）：(a) pre-populate registry：`writeIntentRegistry.register(writeIntentFor("other-session", filePath))` 后运行单 session `execute()` 调用含路径参数的工具写同一文件 → `FailFastStrategy` DENY + 审计事件 `layer2_conflict_strategy` + `TOOL_CALL_DENIED`；(b) 同一 session 内两次写同一文件 → ALLOW（同 session 不冲突）；(c) 无路径参数的工具 → 不触发冲突检测（registry 不被调用）；(d) session 结束后 `releaseSession` 释放写意图 → pre-populate 另一 session 意图 → 执行 → 释放 → 再执行写同一文件 → 不冲突
- [x] 新增 `TestWriteIntentRegistryLifecycle`：验证 session 结束时写意图被释放（通过 engine.execute() 完成后检查 registry 为空）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultAgentEngine` 含 `conflictStrategy` + `writeIntentRegistry` 字段、对应 setter、resolveExecutor Builder 链含 `.conflictStrategy(...)` + `.writeIntentRegistry(...)`
- [x] `ReActAgentExecutor` 构造器含 `IConflictStrategy` + `IWriteIntentRegistry` 参数、Builder 含字段 + setter + build() null 兜底
- [x] dispatch loop 在 Layer 3 approval gate 之后、`allowedCalls.add` 之前含冲突检测逻辑
- [x] **接线验证**（Minimum Rules #23）：`TestConflictStrategyWiring` 通过——test-double 的 `resolve()` 被调用，证明 dispatch-path → conflictStrategy 调用链连通
- [x] **端到端验证**（Minimum Rules #22）：`TestConflictDetectionDispatchPath` 通过——从 `DefaultAgentEngine.execute()` → ReAct dispatch loop → conflict detection → `FailFastStrategy.resolve()` → DENY/ALLOW 完整路径验证
- [x] **无静默跳过**（Minimum Rules #24）：`FailFastStrategy.resolve()` 对冲突返回 `DENY`（非空返回、非静默跳过）；dispatch loop 的 deny 分支产生审计事件 + 错误响应（非 continue 无反馈）
- [x] **默认零回归**：单 session 执行既有全部测试不受影响（注册表无其他 session → 无冲突 → ALLOW → no-op）
- [x] session 清理：`doExecute` / `resumeSession` / `restoreSession` finally 块含 `releaseSession` 调用；`TestWriteIntentRegistryLifecycle` 验证释放
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（含新增测试 + 既有全部测试零回归）
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md` §4.4 更新：标注 `IConflictStrategy` / `FailFastStrategy` / `InMemoryWriteIntentRegistry` 已落地，dispatch-path 冲突检测已接通；`CoordinationBusStrategy` 标注为 successor
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2 表格 L2-13a 从 ❌ → ✅ 并标注本 plan
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IConflictStrategy` 接口 + `FailFastStrategy` 默认 + `WriteIntent` / `ConflictResult` 数据对象 + `IWriteIntentRegistry` / `InMemoryWriteIntentRegistry` 全部落地
- [x] dispatch-path 冲突检测已接通（dispatch loop → writeIntentRegistry → conflictStrategy → result 完整调用链）
- [x] session 生命周期写意图释放已接通
- [x] shipped 默认行为零回归（单 session 无冲突 = no-op）；多 session 场景下 read-tool false-positive 为已知限制（见 Goals）
- [x] 端到端测试验证跨 session 冲突 DENY + 同 session ALLOW + 无路径工具不触发 + session 释放
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（`CoordinationBusStrategy` / 跨进程 registry / 资源竞争均为显式 Non-Goals）
- [x] roadmap §4 Layer 2 L2-13a ❌→✅ + Layer 2 验收标准全部满足
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）dispatch loop 确实在运行时调用 conflictStrategy.resolve()（不只是字段存在），（b）InMemoryWriteIntentRegistry 确实在 dispatch loop 中被 register/getConflicting（不只是构造），（c）FailFastStrategy 的 DENY 分支在端到端测试中产生可见的审计事件和错误响应
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；`CoordinationBusStrategy` / 跨进程 registry / 资源竞争检测 / XDSL 配置化 / 写意图持久化 / `ConflictResolved` 事件均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **`CoordinationBusStrategy`**（设计 §4.4 扩展策略）：通过协调信道广播 scope_claim / operation_intent 实现 LLM 智能协调 + 引擎级预警。需要 `IMessageService` topic 基础设施 + 消息注入 + 模式匹配。Classification: successor plan required（Phase 2+ 多 Agent 协调）。
- **跨进程写意图共享**：`InMemoryWriteIntentRegistry` 为进程内实现，跨进程共享需要 DB-backed registry 或分布式缓存。依赖 L4-8 Actor Runtime。Classification: successor plan required。
- **`ConflictResolved` 审计事件**（glossary.md Layer 3 事件）：当前冲突 deny 复用 `TOOL_CALL_DENIED`（matchedRule = `layer2_conflict_strategy`），冲突 allow 不产生事件。专用 `ConflictResolved` 事件（含策略名称、决策结果）是审计增强。Classification: optimization candidate。
- **资源竞争检测**（设计 §3.2）：CPU / 内存 / 端口争抢检测。Classification: out-of-scope improvement。
- **XDSL 配置化**：`agent.xdef` `<conflict-strategy>` 元素。Classification: optimization candidate。
- **写意图持久化**：进程重启后恢复写意图状态。Classification: optimization candidate。
- **写工具精确分类**（Phase 1 已知限制的 successor）：当前路径参数识别策略对含路径的非写工具（如 `read-file`）也注册写意图，多 session 场景下可能 false-positive DENY。tool-name 级别的写工具精确分类（仅对实际写工具注册意图）消除此 false-positive。Classification: optimization candidate。

## Closure

Status Note: Plan 214 closes the last ❌ item in roadmap §4 Layer 2 (L2-13a). After closure, every Layer 2 interface has a pass-through or functional default implementation. The `IConflictStrategy` / `FailFastStrategy` / `IWriteIntentRegistry` / `InMemoryWriteIntentRegistry` types exist in `io.nop.ai.agent.conflict`, the dispatch-path conflict detection is wired between the Layer 3 approval gate and `allowedCalls.add`, session-end write-intent release is wired into all three execution entry-point finally blocks, and the shipped default behavior is zero-regression for single-session executions (FailFastStrategy allows when no cross-session conflict exists).

Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: closure-audit subagent (independent fresh session, task_id distinct from the implementation session)
- Audit Session: subagent closure-audit pass on 2026-06-16
- Evidence:
  - **Phase 1 Exit Criteria**: PASS — all 7 source files exist in `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/conflict/` (IConflictStrategy, WriteIntent, ConflictDecision, ConflictResult, FailFastStrategy, IWriteIntentRegistry, InMemoryWriteIntentRegistry). `TestFailFastStrategy` 8 tests + `TestInMemoryWriteIntentRegistry` 13 tests all green.
  - **Phase 2 Exit Criteria**: PASS —
    - `DenialLayerSource.LAYER2_CONFLICT_STRATEGY` enum value exists (`DenialLayerSource.java:25-33`).
    - `DefaultAgentEngine` has `conflictStrategy` + `writeIntentRegistry` fields, setters with null-fallback, getters, and `.conflictStrategy(...)` + `.writeIntentRegistry(...)` in the `resolveExecutor` Builder chain.
    - `ReActAgentExecutor` constructor has `IConflictStrategy` + `IWriteIntentRegistry` params with null-fallback; Builder has fields + setters + build() null-fallback.
    - Dispatch loop conflict detection is between Layer 3 approval gate (`checkLayer3Approval`) and `allowedCalls.add` — verified by `checkWriteConflict` call site in the dispatch loop.
    - **接线验证 (Wiring, #23)**: `TestConflictStrategyWiring.resolveIsInvokedForPathArgToolCall` PASS — RecordingStrategy.resolveCount >= 1 after a path-arg tool call, proving dispatch-path → conflictStrategy call chain is connected.
    - **端到端验证 (End-to-End, #22)**: `TestConflictDetectionDispatchPath.crossSessionConflictIsDenied` PASS — `DefaultAgentEngine.execute()` → ReAct dispatch loop → conflict detection → `FailFastStrategy.resolve()` → DENY + audit event `layer2_conflict_strategy` + `TOOL_CALL_DENIED`.
    - **无静默跳过 (No Silent No-Op, #24)**: FailFastStrategy.resolve() returns DENY (non-null, non-silent) on conflict; dispatch loop deny branch produces audit event + error response + `continue` (not a silent skip).
    - **默认零回归**: `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS, all existing tests pass.
    - **session 清理**: `doExecute` / `resumeSession` / `restoreSession` finally blocks each call `writeIntentRegistry.releaseSession(sessionId)`; `TestWriteIntentRegistryLifecycle` verifies registry is empty after execute() completes.
  - **Closure Gates**: PASS — all 12 items green. CoordinationBusStrategy / cross-process registry / resource competition are explicit Non-Goals (not silently deferred). roadmap §4 Layer 2 L2-13a updated ❌→✅.
  - **Anti-Hollow Check**: PASS —
    - (a) dispatch loop calls `conflictStrategy.resolve()` at runtime (verified by TestConflictStrategyWiring: RecordingStrategy.resolveCount >= 1).
    - (b) `writeIntentRegistry.registerAndGetConflicting(intent)` is called in the dispatch loop (verified by TestConflictDetectionDispatchPath: pre-populated intent is observed by the engine's execute()).
    - (c) FailFastStrategy's DENY branch produces a visible audit event (matchedRule `layer2_conflict_strategy`, verified by TestConflictDetectionDispatchPath.crossSessionConflictIsDenied) and a `Conflict denied:` error response (verified by `containsMessage(result, "Conflict denied")`).
  - **Deferred 项分类检查**: No in-scope live defects downgraded. CoordinationBusStrategy = successor plan required (Phase 2+ multi-agent coordination, depends on IMessageService + L4-8 Actor Runtime). Cross-process registry = successor plan required (depends on L4-8). Resource competition / context-dependency conflict / XDSL configuration / write-intent persistence = optimization candidates (all in Non-Blocking Follow-ups with explicit non-blocking reasons).
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/214-nop-ai-agent-conflict-strategy.md --strict` exit code 0 (confirming no unchecked items + Closure Evidence written).
  - Build verification: `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS; `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS (zero regression).

Follow-up:

- no remaining plan-owned work (CoordinationBusStrategy / cross-process registry / resource competition / XDSL config / write-intent persistence / ConflictResolved audit event / write-tool precise classification are all explicit Non-Goals tracked in Non-Blocking Follow-ups with successor ownership)
