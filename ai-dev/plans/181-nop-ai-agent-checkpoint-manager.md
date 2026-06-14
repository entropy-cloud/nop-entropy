# 181 nop-ai-agent ICheckpointManager Contract + NoOpCheckpoint + ToolExecutionCheckpoint

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L3-4 (ICheckpointManager + NoOpCheckpoint + ToolExecutionCheckpoint)

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 180 (`ai-dev/plans/180-nop-ai-agent-sticky-pause-recovery.md`) — `Deferred But Adjudicated` "Crash/restart durable session recovery" (`Successor Required: yes, Successor Path: L3-4 ICheckpointManager 独立计划`). Also referenced as Non-Blocking Follow-up in plan 177. Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3 row L3-4 (`❌`); §2.2 row 54 ("ICheckpointManager 检查点 | nop-ai-agent-reliability.md | ❌ 未开始"). Design owner doc: `nop-ai-agent-reliability.md` §5.4 (检查点与恢复) + §5.4a (Checkpoint Journal 格式).
> Related: Plan 180 (L3-6 sticky-pause recovery — direct predecessor; this plan is its crash/restart-recovery carry-over), Plan 177 (L3-6 denial-ledger contract — sibling contract-surface pattern), Plan 176 (L3-5 approval-gate — sibling pattern), Plan 179 (L3-6 DBDenialLedger — DB-persistence successor precedent), roadmap A4 (Checkpoint Journal format journal.md + snapshot.json — blocked-by this plan)

## Purpose

将可靠性层 `nop-ai-agent-reliability.md` §5.4（检查点与恢复）的 **检查点记录/检索能力** 收口到"契约已落地"状态：交付 `ICheckpointManager` 接口 + `NoOpCheckpoint` 透传默认 + `ToolExecutionCheckpoint` 功能化实现（in-memory）+ executor 分发循环保存侧接线。这是 roadmap L3-4 工作项，也是 plan 180 延期的 crash/restart durable session recovery 的前置依赖。

当前状态：`ICheckpointManager` 在仓库中 **0 个文件**（`grep -r "ICheckpointManager|CheckpointManager|NoOpCheckpoint|ToolExecutionCheckpoint" nop-ai/nop-ai-agent/src/main` → exit 1）。设计 §5.4 描述了检查点的目标（长任务可恢复、崩溃后可恢复到最近安全点、plan/todo/message/token budget 状态可继续使用），§5.4a 描述了 journal.md + snapshot.json 双文件格式（roadmap A4），但运行时没有任何代码记录或检索检查点。引擎和 executor 没有 checkpoint manager 字段、没有 Builder 接线、没有任何保存调用。

## Current Baseline

- **L1-10 AgentSession ✅ satisfied (dependency)**: `AgentSession` 基础会话对象已落地（含 sessionId、message history、status、token/iteration 计数）。L3-4 唯一依赖 L1-10 已满足
- **ReActAgentExecutor Builder pattern ✅ established**: `ReActAgentExecutor.java:141-447` 使用单一私有构造器 + `Builder`。每个扩展接口遵循固定模式：构造器参数（null-fallback 到默认）+ Builder 字段 + Builder setter + `Builder.build()` 传递。当前最后一个扩展参数是 `IPostDenialGuard postDenialGuard`（`:163`/`:230`/`:409-412`/`:445`）。checkpoint manager 按同一模式追加
- **Sibling Layer 3 reliability/security pattern ✅ established (L3-5/L3-6/L3-7)**: 三个已落地工作项均遵循 `接口 + NoOp 默认 + 功能化实现 + 分发路径/循环接线`：
  - L3-5 `IApprovalGate` + `AutoApproveGate`（plan 176）— executor dispatch-path 咨询
  - L3-6 `IDenialLedger` + `NoOpDenialLedger` + `DBDenialLedger`（plan 177/179）— dispatch-path recordDenial + reactLoop isPaused 检查 + sticky-pause recovery（plan 180）
  - L3-7 `IPostDenialGuard` + `PassThroughPostDenialGuard` + `FingerprintPostDenialGuard`（plan 178）— dispatch-loop blind-retry check
- **DefaultAgentEngine engine-wiring pattern ✅ established**: `DefaultAgentEngine` 对 `denialLedger` 持有 field + null-fallback setter + getter + Builder pass（plan 177 evidence 引用 `DefaultAgentEngine.java:91/391-392/399-401/778`）。checkpoint manager 按同一模式接线
- **ICheckpointManager ❌ NOT implemented**: 0 Java 文件（grep exit 1）。`io.nop.ai.agent.security` 包无 checkpoint 类型；executor/engine 无 checkpoint 字段。这是本计划要收口的 gap
- **Design §5.4/§5.4a spec exists but unimplemented**: reliability.md §5.4 定义检查点目标 + §5.4a 定义 journal.md（追加写入，source of truth）+ snapshot.json（派生缓存）双文件格式 + 恢复流程（定位最近 snapshot → 从 lastWatermark 后重建增量 → 加载 firstKeptEntryId 后消息）。**§5.4a 的 journal/snapshot 双文件格式 = roadmap A4（blocked-by L3-4），不在本计划 scope**
- **Checkpoint trigger timing (§5.4a) — save-side scope**: 设计规定触发时机为"每个 LLM turn 完成后 + 压缩时 + 工具执行前后（仅 long-running tool）"。本计划交付 **工具执行后** 的 tool-level checkpoint 保存（对应 `ToolExecutionCheckpoint` 命名），这是 §5.4a 明确列出的触发点之一
- **Crash/restart durable recovery = deferred successor (NOT this plan)**: plan 180 明确延期 crash/restart 后从持久化状态恢复完整 session（message history + execution state + denial counts）。L3-4 交付 checkpoint 记录/检索能力（save side + functional store），使 crash/restart recovery **成为可能**（有 checkpoint 可恢复）；但实际的 restore-on-restart 协议（从 checkpoint 重建 `AgentExecutionContext`、跨进程 session 接管锁机制）是独立 successor

## Goals

- `ICheckpointManager` 接口（契约表面）：定义 checkpoint 的 **保存** 与 **检索** 契约（保存一个 checkpoint；检索指定 session 的最新 checkpoint；检索指定 watermark 的 checkpoint）。契约不强制持久化——持久化是功能化实现的属性（与 L3-6 `IDenialLedger` 的 L3-G5 收窄一致）
- `Checkpoint` 值类型 + checkpoint 类型枚举（如 `tool_execution` / `llm_turn`）：承载 checkpoint 的结构化数据（sessionId、watermark/checkpointId、seq、timestamp、类型、工具调用负载、message count/token estimate 快照）
- `NoOpCheckpoint`（透传默认）：不保存、不持久化、检索返回空。这是 shipped 默认（注入 executor/engine），使无人值守 Layer 1 自动化不受影响，除非显式注册功能化实现
- `ToolExecutionCheckpoint`（功能化实现，in-memory）：按 session 保存 checkpoint 列表，`getLatestCheckpoint` / `getCheckpoint` 真实返回已保存的 checkpoint。in-memory（不持久化）——这是最简功能化形式，证明 save/retrieve 往返可用（与 plan 177 内存计数验证 L3-6 链路非空壳的模式一致）
- `ReActAgentExecutor` 分发循环保存侧接线：**每次** 工具执行完成后调用 `checkpointManager.saveCheckpoint(...)` 记录 tool-level checkpoint（§5.4a 工具执行后触发点；in-memory 默认对全部工具生效、不套用 §5.4a "仅 long-running tool" 限定，详见 Phase 2 Decision）。Builder 字段 + setter + ctor 参数 + null-fallback + `Builder.build()` pass
- `DefaultAgentEngine` engine 接线：checkpointManager field + null-fallback setter + Builder pass（平行于 `denialLedger`）
- 测试覆盖：契约测试（NoOp 语义）、功能化测试（ToolExecutionCheckpoint save→retrieve 往返）、接线测试（executor 在运行时确实调用 saveCheckpoint）、向后兼容测试（NoOp 默认不影响现有行为）
- 设计文档 §5.4 更新：检查点记录/检索能力标记为"已落地"；架构决策记录（save-side 先行 + restore-on-restart 延期 + in-memory 不持久化裁定）
- roadmap L3-4 行状态从 `❌` 更新为 `✅`

## Non-Goals

- **`journal.md` + `snapshot.json` 双文件格式 (roadmap A4)**: §5.4a 的完整 journal/snapshot 格式 + 按 watermark 恢复流程是独立工作项 A4（roadmap 明确 blocked-by L3-4）。L3-4 交付 in-memory 契约 + 功能化实现，不交付文件格式
- **Crash/restart durable session restore protocol (carry-over from plan 180)**: 从持久化 checkpoint 跨进程边界重建 `AgentExecutionContext`（message history + execution state 恢复）+ session 接管锁机制。L3-4 交付 checkpoint 记录/检索能力（使恢复成为可能），但 restore-on-restart 协议是独立 successor。本计划交付 save side + functional store，**不**交付 restore side 的运行时消费（executor 不在启动时从 checkpoint 恢复——那是 successor plan 的职责）
- **DB persistence of checkpoints**: 类比 L3-6（plan 177 交付契约 + NoOp，plan 179 单独交付 DBDenialLedger）。L3-4 的功能化实现 `ToolExecutionCheckpoint` 为 in-memory；DB-backed checkpoint 持久化（如 `DBCheckpointStore`）是独立 successor，参照 plan 179 / plan 171（DB-backed）模式
- **LLM-turn checkpoint + compaction-triggered snapshot**: §5.4a 列出多个触发点（LLM turn 完成后、压缩时）。L3-4 交付 tool-execution 触发点（对应 `ToolExecutionCheckpoint` 命名）。LLM-turn checkpoint 和 compaction snapshot 是后续增强（roadmap A4 范畴）
- **Checkpoint retention/rotation policy**: checkpoint 数量上限、自动清理旧 checkpoint、保留窗口策略。当前 in-memory 实现无界保存（测试规模下安全）；retention 是后续增强
- **Automatic restore-on-engine-startup**: 引擎启动时自动检测未完成的 session 并从 checkpoint 恢复。这是 crash/restart durable recovery successor 的职责

## Scope

### In Scope

- `ICheckpointManager` 接口（保存 + 检索契约，线程安全约定，持久化非强制声明）
- `Checkpoint` 值类型 + checkpoint 类型枚举（至少 `tool_execution`）
- `NoOpCheckpoint` 透传默认实现
- `ToolExecutionCheckpoint` in-memory 功能化实现（per-session 保存 + 真实检索）
- `ReActAgentExecutor` 分发循环保存侧接线（工具执行后 saveCheckpoint）+ Builder/ctor 扩展
- `DefaultAgentEngine` engine 接线（field + setter + Builder pass）
- 契约测试 + 功能化测试 + 接线测试 + 向后兼容测试
- 端到端验证：工具执行 → checkpoint 保存 → getLatestCheckpoint 检索到（save→retrieve 往返）
- 设计文档 §5.4 更新 + roadmap L3-4 行状态更新

### Out Of Scope

- `journal.md` + `snapshot.json` 双文件格式（roadmap A4）
- Crash/restart durable session restore protocol（plan 180 carry-over successor）
- DB-backed checkpoint 持久化（独立 successor，参照 plan 179 模式）
- LLM-turn checkpoint + compaction-triggered snapshot
- Checkpoint retention/rotation policy
- Automatic restore-on-engine-startup

## Execution Plan

### Phase 1 - Contract surface + NoOp default + value types + unit tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/` (new package — parallel to `security/` for reliability-layer types) — `ICheckpointManager.java`, `Checkpoint.java`, checkpoint-type enum, `NoOpCheckpoint.java`; `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（reliability 包归属）**: checkpoint 类型放入 `io.nop.ai.agent.reliability` 包（新包），与 `io.nop.ai.agent.security`（安全/治理层）平行。可靠性层（L3-1 circuit breaker / L3-2 retry / L3-3 goal tracker / L3-4 checkpoint / L3-8 sustainer）属可靠性增强，非安全治理，包边界反映这一区分
- [x] **Decision（持久化非接口契约）**: `ICheckpointManager` 契约不强制持久化——与 L3-6 `IDenialLedger` 的 L3-G5 收窄裁定一致。`NoOpCheckpoint` 默认不持久化；`ToolExecutionCheckpoint` 功能化实现为 in-memory（不持久化）；DB-backed 持久化是独立 successor（参照 plan 179 DBDenialLedger 模式）。Javadoc 明确声明此裁定
- [x] **Decision（save-side 先行，restore-side 延期）**: L3-4 交付 checkpoint **记录/检索** 能力（save + retrieve 契约 + functional store）。restore-on-restart（executor 启动时从 checkpoint 重建 `AgentExecutionContext`）是 plan 180 carry-over successor 的职责。本计划的 retrieve 方法（`getLatestCheckpoint` / `getCheckpoint`）是契约表面 + 功能化实现内部往返验证，**不**在 executor 启动路径被消费——这与 L3-6 plan 177 交付 recordDenial save-side 而 resume（plan 180）单独交付 restore 的模式一致
- [x] 新增 `Checkpoint` 值类型：承载 checkpoint 结构化数据（sessionId、watermark/checkpointId、seq、timestamp、类型、工具调用负载——toolName/callId/input/output 摘要、message count/token estimate 快照）。值类型（不可变），参照 `DenialRecord` 的设计模式
- [x] 新增 checkpoint 类型枚举：至少含 `TOOL_EXECUTION`（对应 `ToolExecutionCheckpoint` 命名）。枚举设计允许后续扩展 `LLM_TURN` / `COMPACTION`（roadmap A4 范畴，不在本计划实现）
- [x] 新增 `ICheckpointManager` 接口，Javadoc 说明：(1) 可靠性层 §5.4 检查点契约；(2) 保存 checkpoint（dispatch-path 工具执行后调用）；(3) 检索最新 checkpoint / 指定 watermark checkpoint（供 crash/restart recovery successor 消费）；(4) 线程安全约定（多 session 并发访问同一实例，per-session checkpoint 列表独立）；(5) 持久化非强制声明；(6) 默认 `NoOpCheckpoint` shipped
- [x] 新增 `NoOpCheckpoint` 透传默认实现：`saveCheckpoint` = no-op（不保存、不抛异常）；`getLatestCheckpoint` / `getCheckpoint` = 返回 null/empty。提供静态 `noOp()` 工厂（参照 `NoOpDenialLedger.noOp()` 模式）
- [x] 单元测试：`NoOpCheckpoint.saveCheckpoint` 不抛异常（接受任意 checkpoint，无副作用）
- [x] 单元测试：`NoOpCheckpoint.getLatestCheckpoint` / `getCheckpoint` 返回 null/empty（即使 saveCheckpoint 曾被调用）
- [x] 单元测试：`NoOpCheckpoint` 线程安全（多线程并发调用 saveCheckpoint/getLatestCheckpoint 无异常）
- [x] 单元测试：`Checkpoint` 值类型构造 + 字段访问 + 不可变性（参照 `DenialRecord` 测试模式）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ICheckpointManager.java`、`Checkpoint.java`、checkpoint 类型枚举、`NoOpCheckpoint.java` 存在于 `io.nop.ai.agent.reliability` 包
- [x] `ICheckpointManager` 契约含保存 + 检索方法（getLatestCheckpoint / getCheckpoint），Javadoc 声明线程安全 + 持久化非强制
- [x] `NoOpCheckpoint` 实现 `ICheckpointManager`：saveCheckpoint no-op、检索返回 null/empty
- [x] **新增功能测试**: NoOpCheckpoint 的 no-op 语义 + 线程安全 + Checkpoint 值类型不可变性——各有对应通过的测试（Minimum Rules #25）
- [x] **无静默跳过**: NoOpCheckpoint 的 no-op 是设计语义（透传默认，非缺失功能），Javadoc 明确声明；不是"应处理而静默跳过"（Minimum Rules #24）——NoOp 是合法的 pass-through 默认，与 `NoOpDenialLedger` / `AutoApproveGate` 同类
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 1 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ToolExecutionCheckpoint functional impl + executor/engine wiring + end-to-end + backward compat

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/ToolExecutionCheckpoint.java`, `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java` (Builder/ctor/dispatch-loop wiring), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (field/setter/Builder-pass), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/` + `engine/`

- Item Types: `Decision | Fix | Proof`

- [x] 新增 `ToolExecutionCheckpoint` 功能化实现（implements `ICheckpointManager`）：per-session 保存 checkpoint 列表（in-memory，线程安全——`ConcurrentHashMap<String, List<Checkpoint>>` 或等效），`saveCheckpoint` 真实追加到 session 列表，`getLatestCheckpoint` 返回该 session 列表的最后一个 checkpoint，`getCheckpoint(watermark)` 按 watermark 检索。参照 `DBDenialLedger` 的 per-session 隔离语义（但 in-memory）
- [x] `ReActAgentExecutor` 扩展：新增 `checkpointManager` 字段 + 构造器参数（null-fallback 到 `NoOpCheckpoint.noOp()`）+ Builder 字段 + Builder setter（`checkpointManager(ICheckpointManager)`，Javadoc 参照 `denialLedger` setter）+ `Builder.build()` pass。追加为最后一个扩展参数（在 `postDenialGuard` 之后），保持构造器签名稳定性约定
- [x] **Decision（checkpoint 触发范围——in-memory 不套用 §5.4a "仅 long-running tool" 限定）**: `ToolExecutionCheckpoint` 对 **所有** 工具执行记录 checkpoint（非仅 long-running tool），原因：(1) in-memory 存储无 I/O 成本，§5.4a "仅 long-running tool" 限定本是为约束持久化 I/O 频率，对 in-memory 默认不适用；(2) long-running-tool 过滤将下沉到持久化层（roadmap A4 journal/snapshot）应用。与 L3-6 plan 177 内存计数不限触发源、过滤延后至 plan 179 DB 层的模式一致。本 Decision 澄清下方"工具执行完成后 saveCheckpoint"接线（所有工具、无过滤）与 §5.4a 的关系，消除 §5.4a 引用与"每次工具执行"之间的表面矛盾
- [x] `ReActAgentExecutor` 分发循环保存侧接线：工具执行完成后（tool result 已生成、已加入 context 后），调用 `checkpointManager.saveCheckpoint(...)` 记录 tool-level checkpoint。构造 `Checkpoint`（类型 `TOOL_EXECUTION`，含 toolName/callId/input-output 摘要、当前 message count/token estimate 快照、递增 seq、watermark）。**Position rationale**: 在 tool result 加入 context 之后、下一次 LLM call 之前——这是 §5.4a "工具执行后写入 tool-level checkpoint" 的语义位置
- [x] `DefaultAgentEngine` engine 接线：新增 `checkpointManager` field + null-fallback setter（参照 `setDenialLedger` 模式，null 时 fallback 到 `NoOpCheckpoint.noOp()`）+ getter + `Builder.build()` pass 到 `ReActAgentExecutor.Builder.checkpointManager(...)`
- [x] 功能化测试（**save→retrieve 往返——核心价值**）：`ToolExecutionCheckpoint.saveCheckpoint` 后 `getLatestCheckpoint` 返回刚保存的 checkpoint（字段完整：watermark/seq/timestamp/type/payload）；多次 saveCheckpoint 后 `getLatestCheckpoint` 返回最后一个；`getCheckpoint(watermark)` 按 watermark 精确检索
- [x] 功能化测试（per-session 隔离）：session A 的 checkpoint 不影响 session B（`getLatestCheckpoint("A")` 不返回 session B 的 checkpoint）——参照 `DBDenialLedger` per-session 独立性测试
- [x] 接线测试（**Anti-Hollow Wiring——运行时调用连通性**）：构造 executor + 功能化 `ToolExecutionCheckpoint` + 至少 1 个工具调用 → 执行 ReAct 循环 → 工具执行后 `checkpointManager.saveCheckpoint` **确实被调用**（经 spy/计数器/getLatestCheckpoint 非空验证）。证明接线连通，非仅 Builder 字段存在
- [x] 向后兼容测试：executor/engine **不**设 checkpointManager（默认 `NoOpCheckpoint`）→ ReAct 循环正常执行 → 0 副作用 → 全部现有测试通过。证明 NoOp 默认不影响现有行为
- [x] 端到端测试（**Anti-Hollow 端到端——Minimum Rules #22**）：从用户入口点 `engine.execute(request)` → ReAct 循环 → 工具调用 → 工具执行 → `saveCheckpoint` 记录 → `getLatestCheckpoint` 检索到含正确 tool payload 的 checkpoint。证明从入口到 checkpoint 保存/检索的完整路径连通

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ToolExecutionCheckpoint` 实现 `ICheckpointManager`：saveCheckpoint 真实保存、getLatestCheckpoint/getCheckpoint 真实检索（非空壳）
- [x] `ReActAgentExecutor` 含 `checkpointManager` 字段 + ctor 参数（null-fallback NoOp）+ Builder setter + `Builder.build()` pass
- [x] `DefaultAgentEngine` 含 `checkpointManager` field + setter（null-fallback NoOp）+ Builder pass
- [x] **接线验证**（Minimum Rules #23）：executor 在运行时（工具执行后）确实调用 `checkpointManager.saveCheckpoint`——经功能化 `ToolExecutionCheckpoint` 的 `getLatestCheckpoint` 非空或 spy 计数验证，非仅 Builder 字段存在
- [x] **端到端验证**（Minimum Rules #22 Anti-Hollow）：`engine.execute` → 工具调用 → saveCheckpoint → getLatestCheckpoint 检索到含正确 payload 的 checkpoint——从入口到 checkpoint 保存/检索的完整路径已验证
- [x] **Anti-Hollow Check**: ToolExecutionCheckpoint save→retrieve 往返 + executor 运行时调用连通 + per-session 隔离——三者均有通过测试证明（非仅类型系统存在）
- [x] **无静默跳过**（Minimum Rules #24）：ToolExecutionCheckpoint 的 save/retrieve 是真实实现（非空方法体/非 placeholder）；NoOp 默认是合法透传（Javadoc 声明，非缺失功能）
- [x] **新增功能测试**（Minimum Rules #25）：ToolExecutionCheckpoint save→retrieve 往返 + per-session 隔离 + executor 接线 + 向后兼容 + 端到端——各有对应通过的测试
- [x] **向后兼容**: NoOp 默认注入，全部现有测试通过，0 副作用
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Design doc update + roadmap status sync

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.4, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §2.2 row 54 + §4 L3-4 row 197

- Item Types: `Follow-up`

- [x] 更新 `nop-ai-agent-reliability.md` §5.4：(1) 检查点记录/检索能力标记为"已落地"（L3-4 contract + NoOp + ToolExecutionCheckpoint functional + executor/engine wiring）；(2) 记录架构决策——save-side 先行（restore-on-restart 延期至 successor，平行 L3-6 plan 177→180 模式）、in-memory 不持久化裁定（DB-backed 持久化参照 plan 179 独立 successor）、reliability 包归属（与 security 包平行）、持久化非接口契约（与 L3-6 L3-G5 收窄一致）；(3) §5.4a journal/snapshot 格式仍标注为 roadmap A4（blocked-by L3-4，非本计划交付）
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 3 row L3-4（`:197`）：状态从 `❌` 改为 `✅`（简注：contract + NoOp + ToolExecutionCheckpoint + wiring landed；A4 journal format + crash/restart restore = deferred successors）
- [x] 更新 `nop-ai-agent-roadmap.md` §2.2 row 54（`:54`）：`ICheckpointManager 检查点` 状态从 `❌ 未开始` 改为 `✅ 已开始/已落地`（与 §4 L3-4 行不矛盾）
- [x] 确认 §4 A4 行（`:148`）"blocked by L3-4" 现在依赖已满足（L3-4 ✅ 后 A4 可规划，但 A4 本身不在本计划交付）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-reliability.md` §5.4 已更新（记录/检索能力标记已落地 + 架构决策记录 + journal/snapshot 仍标注为 A4 deferred）
- [x] `nop-ai-agent-roadmap.md` §4 L3-4 行状态为 `✅`，与 §2.2 row 54 不矛盾
- [x] `nop-ai-agent-roadmap.md` §2.2 row 54 状态更新（与 §4 一致）
- [x] A4 行的 blocked-by L3-4 依赖现满足（L3-4 ✅），A4 本身仍未交付（不在本计划 scope）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`（文档变更无需重跑测试，但确认编译通过）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `ICheckpointManager` 接口 + `Checkpoint` 值类型 + checkpoint 类型枚举已落地
- [x] `NoOpCheckpoint` 透传默认已落地（shipped default，注入 executor/engine）
- [x] `ToolExecutionCheckpoint` 功能化实现已落地（in-memory，save→retrieve 往返可用）
- [x] `ReActAgentExecutor` 分发循环保存侧接线已落地（工具执行后 saveCheckpoint）+ Builder/ctor 扩展
- [x] `DefaultAgentEngine` engine 接线已落地（field + setter + Builder pass）
- [x] 接线验证：executor 运行时确实调用 saveCheckpoint（非仅类型存在）
- [x] 端到端验证：engine.execute → 工具调用 → saveCheckpoint → getLatestCheckpoint 完整路径连通
- [x] 向后兼容：NoOp 默认不影响现有行为，全部现有测试通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（reliability §5.4、roadmap L3-4 / §2.2 row 54）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) executor → checkpointManager.saveCheckpoint → ToolExecutionCheckpoint 存储 调用链在运行时连通，(b) save→retrieve 往返经测试证明（非空壳），(c) NoOp 默认是合法透传（非缺失功能静默跳过）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/181-nop-ai-agent-checkpoint-manager.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### `journal.md` + `snapshot.json` 双文件格式 (roadmap A4)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: §5.4a 的完整 journal（追加写入 source of truth）+ snapshot（派生缓存）双文件格式 + 按 watermark 恢复流程是 roadmap 独立工作项 A4（明确 blocked-by L3-4）。L3-4 交付 in-memory 契约 + 功能化实现，证明 checkpoint 记录/检索能力可用。文件格式 + watermark 索引恢复是 A4 的职责。两者解耦：L3-4 提供内存中的 checkpoint 抽象，A4 提供文件持久化 + 高效恢复索引
- Successor Required: yes
- Successor Path: roadmap A4 独立计划（L3-4 ✅ 后可规划）

### Crash/restart durable session restore protocol

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: plan 180 明确延期此项。L3-4 交付 checkpoint 记录/检索能力（save side + functional store），使 crash/restart 恢复 **成为可能**（有 checkpoint 可恢复）。但实际的 restore-on-restart 协议——从 checkpoint 重建 `AgentExecutionContext`（message history + execution state 恢复）+ 跨进程 session 接管锁机制（actor 调度系统职责）——是独立 successor。L3-4 的 retrieve 方法是契约表面 + 功能化往返验证，**不**在 executor 启动路径被消费（平行于 L3-6 plan 177 交付 save-side 而 plan 180 单独交付 restore 的模式）
- Successor Required: yes
- Successor Path: 独立 governance/reliability plan（参照 plan 180 sticky-recovery 模式）

### DB-backed checkpoint persistence

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: L3-4 契约不强制持久化（与 L3-6 L3-G5 收窄一致）。`ToolExecutionCheckpoint` 功能化实现为 in-memory（证明 save/retrieve 往返可用）。DB-backed 持久化（如 `DBCheckpointStore`，checkpoint 表 + DAO）使 checkpoint 跨进程重启存活，是独立 successor——参照 plan 179（DBDenialLedger）和 plan 171（DBMessageService）的 DB-backed 实现模式。in-memory 默认在单进程内完全可用；跨进程持久化是生产部署增强
- Successor Required: yes
- Successor Path: 独立 plan（参照 plan 179 DB-backed 模式）

### LLM-turn checkpoint + compaction-triggered snapshot

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: §5.4a 列出多个 checkpoint 触发点（每个 LLM turn 完成后、压缩时、工具执行前后）。L3-4 交付 tool-execution 触发点（对应 `ToolExecutionCheckpoint` 命名 + dispatch-loop 接线）。LLM-turn checkpoint（LLM 响应后记录 prompt/completion token + toolCalls）和 compaction snapshot（压缩时生成完整快照）是 §5.4a 的其他触发点，属 roadmap A4 范畴的后续增强
- Successor Required: yes
- Successor Path: roadmap A4 独立计划

## Non-Blocking Follow-ups

- `journal.md` + `snapshot.json` 双文件格式 + watermark 恢复（roadmap A4）
- Crash/restart durable session restore protocol（从 checkpoint 重建 `AgentExecutionContext` + session 接管锁机制）
- DB-backed checkpoint persistence（`DBCheckpointStore` + ORM 模型 + DAO，参照 plan 179 模式）
- LLM-turn checkpoint + compaction-triggered snapshot（§5.4a 其他触发点）
- Checkpoint retention/rotation policy（数量上限 + 自动清理旧 checkpoint + 保留窗口）
- Automatic restore-on-engine-startup（检测未完成 session 并从 checkpoint 恢复）

## Closure

Status Note: L3-4 检查点记录/检索能力已完整落地。`ICheckpointManager` 契约 + `NoOpCheckpoint` 透传默认 + `ToolExecutionCheckpoint` in-memory 功能化实现 + executor/engine 分发循环接线 + 28 个新增测试（含端到端 Anti-Hollow 验证）。全部 in-scope 项已完成；deferred 项（A4 journal 格式、crash/restart restore 协议、DB 持久化、LLM-turn/compaction 触发点、retention 策略）均为已裁定的 out-of-scope improvement，有明确 successor path。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session, task_id: ses_1391bb7f4ffeZhnV7j0RjhaSr8, subagent_type: explore）
- Audit Session: ses_1391bb7f4ffeZhnV7j0RjhaSr8
- Evidence:
  - **Phase 1 Exit Criteria**: PASS — `CheckpointType.java` (enum with TOOL_EXECUTION), `Checkpoint.java` (immutable value type, all 11 fields, factory of(), all-field equals/hashCode), `ICheckpointManager.java` (interface with saveCheckpoint/getLatestCheckpoint/getCheckpoint, Javadoc declares thread-safety + persistence non-mandate), `NoOpCheckpoint.java` (singleton noOp() factory, documented no-op). `TestNoOpCheckpointAndValue` 14 tests pass (no-op semantics + 8-thread×200-call thread-safety + value-type immutability + factory validation).
  - **Phase 2 Exit Criteria**: PASS — `ToolExecutionCheckpoint.java` real impl (ConcurrentHashMap per-session store + watermark index, save/retrieve verified). `ReActAgentExecutor` field + ctor param (null-fallback NoOp) + Builder setter + build() pass. `DefaultAgentEngine` field + setter (null-fallback) + getter + Builder pass. Wiring verified: `saveCheckpoint` called at `ReActAgentExecutor.java:918` inside the `for (CompletableFuture<ToolCallOutput> f : futuresArray)` loop, after `ctx.addMessage(toolResponse)` (line 907), before POST_ACTING hook. E2e verified: `TestCheckpointDispatchPathWiring.endToEndEngineExecuteRecordsCheckpointWithToolPayload` proves `engine.execute(request)` → checkpoint with correct tool payload. `TestToolExecutionCheckpoint` 7 tests + `TestCheckpointDispatchPathWiring` 7 tests pass.
  - **Phase 3 Exit Criteria**: PASS — `nop-ai-agent-reliability.md` §5.4 updated (✅ 已落地 + 4 architecture decisions + §5.4a marked A4 deferred). `nop-ai-agent-roadmap.md` §4 L3-4 row ✅, §2.2 row 54 ✅. A4 row correctly remains ❌.
  - **Closure Gates**: all 17 PASS (verified by independent audit above).
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/181-nop-ai-agent-checkpoint-manager.md --strict` exit code: 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit code: 0 (0 high/critical findings)
  - Anti-Hollow check: PASS — (a) dispatch loop calls `checkpointManager.saveCheckpoint(Checkpoint.of(...))` at runtime (line 918, traced by audit), (b) save→retrieve round-trip proven by TestToolExecutionCheckpoint + TestCheckpointDispatchPathWiring, (c) NoOp is documented legitimate pass-through default (Javadoc + inline comment).
  - Deferred 项分类检查: all 4 deferred items are correctly classified as `out-of-scope improvement` with explicit `Why Not Blocking Closure` + `Successor Path` — no in-scope live defect downgraded.

Follow-up:

- `journal.md` + `snapshot.json` 双文件格式 + watermark 恢复（roadmap A4，blocked-by L3-4 ✅ 已满足，可规划）
- Crash/restart durable session restore protocol（plan 180 carry-over successor）
- DB-backed checkpoint persistence（参照 plan 179 模式，独立 successor）
- LLM-turn checkpoint + compaction-triggered snapshot（§5.4a 其他触发点，roadmap A4 范畴）
- Checkpoint retention/rotation policy（数量上限 + 自动清理 + 保留窗口）
- Automatic restore-on-engine-startup（crash/restart successor 的职责）
