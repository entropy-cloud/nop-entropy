# nop-ai-agent checkpoint idempotency_key 非确定性检测（W2-2）

> Plan Status: completed
> Mission: nop-ai-agent-harness-evolution
> Work Item: W2-2（checkpoint 增加 idempotency_key 列 + 唯一约束；restore 时发散检测：同水位 key 不一致 → 拒绝该 checkpoint，降级 session 重放）
> Last Reviewed: 2026-08-01
> Draft Review: round-1 独立子 agent 审查发现 2 Blocker（比较机制未定义、"拒绝+降级"无可观测行为）+ 5 Major（不可变性 vs 写入、of() 100+ 调用点、序列化范围低估、DB 兼容、hash 公式 per type），全部已修：重构为 design-elaboration Phase 1（7 项裁定 A-G）+ Phase 2/3 实现。round-2 独立子 agent 审查 verdict READY FOR ACTIVE（7 项全部 RESOLVED，Phase 1 design-only 合理，无新 Blocker/Major）。共识达成。
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.2（`:703-715`，idempotency_key = `hash(toolName + callId + 输入指纹)`，restore 拒绝 + 唯一约束 + WATERMARK 主键不变量）；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W2-2
> Related: W2-1 WAIT_FOR（§13.1，§13.5 推荐在前但无硬依赖，本计划独立）；security `actionFingerprint`（SHA-256 of `toolName+argv`，`DenialResult.java:56-99`，最近似的 hash 模式参考）；grok-build req_hash（Journal 发散检测，同源语义）

## Purpose

补齐 checkpoint 的非确定性检测能力：今日 restore **只软校验 messageCount**（不一致仅 `LOG.warn` 后 best-effort 继续，从不拒绝），无法检测"同水位 checkpoint 对应的 tool 调用输入已发散"（崩溃/恢复后输入指纹不一致 = 状态损坏）。本计划增加 `idempotency_key = hash(toolName + callId + 输入指纹)` 列 + restore 时同水位 key 不一致 → **拒绝该 checkpoint，降级 session 重放**（design §13.2）。

## Current Baseline

> 已逐条核对 live repo（独立 explore 子 agent 报告 ses_043018517ffeR2qKSd3FMIvkrE）。

**已落地（核对属实）**：

- `Checkpoint` 值对象（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/Checkpoint.java`）：不可变，私有构造 + `Checkpoint.of(...)` 工厂（`:52-112`）。字段（`:40-50`）：`sessionId`/`watermark`（PK，检索键 `:96`）/`seq`/`timestamp`/`type`/`toolName`/`callId`/`inputSummary`/`outputSummary`/`messageCount`/`tokenEstimate`。**无 `wait_for` 字段，无 `idempotency_key` 字段**。
- ORM 实体 `AiAgentCheckpoint`（`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:119-159`，表 `ai_agent_checkpoint`，**手写源模型非生成**）。列 propId 1-12，PK=`WATERMARK`（`:122`），二级索引 `IDX_AI_AGENT_CHECKPOINT_SESSION_SEQ` on `(SESSION_ID, SEQ)`。
- DDL 常量 `AiAgentCheckpointTable`（`.../reliability/AiAgentCheckpointTable.java`）：`DDL_CREATE_TABLE`（`:56-71`，12 列）、`DDL_CREATE_INDEX`（`:73-75`）。**无 idempotency_key 列**。
- `CheckpointType` 枚举（`.../reliability/CheckpointType.java`）：`TOOL_EXECUTION`/`LLM_TURN`/`COMPACTION`（无 `WAIT_FOR`）。
- 存储契约是 **`ICheckpointManager`**（`.../reliability/ICheckpointManager.java`，118 行，**非 `ICheckpointStorage`——不存在**）：`saveCheckpoint`（`:65`）/`getLatestCheckpoint`（`:82`）/`getCheckpoint`（`:92`）/`remove`（`:116`）。实现：`NoOpCheckpoint`/`ToolExecutionCheckpoint`/`FileBackedCheckpointManager`/`DBCheckpointManager`。

**架构事实（restore 路径，核对属实）**：

- restore 在 `AgentSessionLifecycle.restoreSession`（`.../engine/AgentSessionLifecycle.java:406`）。checkpoint 消费块 `:438-458`：
  - `:446` 取 `getLatestCheckpoint(sessionId)`；`:450-451` 取 `checkpointMsgCount`/`sessionMsgCount`；`:452-457` **唯一一致性检查**——`checkpointMsgCount > sessionMsgCount` 时 `LOG.warn`（"restoreSession checkpoint consistency warning..."）后 **best-effort 继续，恢复不被阻断**。
  - persisted session 是 source of truth；checkpoint 是"verification supplement, not a message source"（`:443-445`）。
  - **无 idempotency_key/fingerprint 比较，无 wait_for 条件求值，恢复从不拒绝 checkpoint**。
- `DefaultAgentEngine.restoreSession`（`DefaultAgentEngine.java:904`）薄委托给 `lifecycle.restoreSession(...)`。

**最近似 hash 模式（security 层，不同关注点但模式可参考）**：`DenialResult.actionFingerprint`（`security/DenialResult.java:56-99`）= SHA-256 of `toolName + argv`，供 `FingerprintPostDenialGuard` 阻断 blind-retry。这是 per-tool-call denial 追踪，非 per-checkpoint restore 校验，但 hash 公式可参考。

**真正剩余的 gap**：

1. `Checkpoint` 无 `idempotency_key` 字段；`app.orm.xml` 表无该列；`AiAgentCheckpointTable` DDL 无该列。
2. 无 hash 计算逻辑（`hash(toolName + callId + 输入指纹)`）。
3. restore **从不拒绝 checkpoint**——需新增"同水位 key 不一致 → 拒绝 → 降级 session 重放"路径（今日无拒绝路径）。
4. 唯一约束落在 idempotency_key（design §13.2 `:711`，幂等去重落此列非 `session_id+seq`）；`seq` 是 per-execution-local 不可作唯一键（§13.2 `:715`），WATERMARK 保持 PK。

## Goals

- `Checkpoint` 增加 `idempotency_key` 字段 + ORM 列 + DDL 列 + 唯一约束（落 idempotency_key，WATERMARK 保持 PK，seq 不作唯一键）。
- saveCheckpoint 时计算 `idempotency_key = hash(toolName + callId + inputSummary)`（输入指纹），写入 checkpoint。
- restore 时同水位 idempotency_key 不一致 → **拒绝该 checkpoint，降级 session 重放**（design §13.2），非 best-effort 继续。
- 零回归：无 idempotency_key 的旧 checkpoint 兼容（退回今日 best-effort 行为，不因新列缺失而全部拒绝）。

## Non-Goals

- **W2-1 WAIT_FOR 长等待原语**（§13.1）——独立 successor（suspend/wake 执行语义，需 design 首切）；本计划不动 `CheckpointType`（不新增 WAIT_FOR 类型），不动 ReAct 执行循环。
- **W2-3 三级失败升级 / W2-4 跨 provider 故障转移**——属 W2 其他 work item。
- **改造 hash 算法本身**——用平台既有 hash 工具（参考 security actionFingerprint 的 SHA-256），不引入新依赖。
- **checkpoint 持久化形态变更**——保持今日 `ICheckpointManager` 多实现（NoOp/File/DB）结构，只增列。
- W1 / W3+ 全部。

## Scope

### In Scope

- `Checkpoint` 值对象增 `idempotency_key` 字段（工厂同步）。
- `app.orm.xml`（手写源模型）`ai_agent_checkpoint` 增 `idempotency_key` 列 + 唯一约束；`AiAgentCheckpointTable` DDL 同步。
- saveCheckpoint 时计算并写入 idempotency_key（`hash(toolName + callId + inputSummary)`）。
- restore 时同水位 key 不一致 → 拒绝 checkpoint + 降级 session 重放（`AgentSessionLifecycle.restoreSession`）。
- 旧 checkpoint 兼容（无 key 列时退回 best-effort，零回归）。
- 全部 `ICheckpointManager` 实现的 save/restore 路径覆盖（至少 NoOp + DB + FileBacked）。

### Out Of Scope

- W2-1 WAIT_FOR（suspend/wake）。
- W2-3/W2-4。
- W1 / W3+ 全部。
- ORM 迁移工具/版本化（本计划只增列，依赖既有 ORM 加载机制）。

## Risks And Rollback

- **§13.2 design 过薄（13 行草图）**：design 未定义比较机制（restore 时 key 与什么比）、"拒绝 + 降级"的可观测行为、不可变性策略、序列化范围、DB 兼容。这些是 **design gap**，Phase 1 须先补齐 design 再实现，否则执行者臆造语义。
- **唯一约束与现有数据冲突**：若已有 DB 数据无 idempotency_key，加唯一约束可能冲突（且不同 DB 对 NULL 唯一约束行为不同：PostgreSQL/Oracle/H2 允许多 NULL，MySQL <8.0.16 仅允许一 NULL）。Phase 1 须裁定兼容策略 + 目标 DB。
- **restore 拒绝改变恢复语义**：从 best-effort 变为可拒绝是行为变更（feature，非回归）。须确保拒绝后降级路径（session 重放）真的可走，否则恢复卡死。
- **`Checkpoint.of()` 调用点爆炸**：仓库内有 100+ 处 `Checkpoint.of(` 调用（含 `ReActAgentExecutor.java:649`、`AgentToolDispatcher.java:341`、`AgentCompactionCoordinator.java:119`、`DBCheckpointManager` 反序列化、`CheckpointJournalReader` 反序列化 + ~70 测试调用点）。Phase 1 须裁定 of() 参数策略（重载 vs 全改）。
- **序列化范围**：JournalWriter/Reader、DBCheckpointManager SQL（INSERT + SELECT + readCheckpoint）、Checkpoint equals/hashCode/toString 均须同步，否则往返丢失 key。

## Execution Plan

### Phase 1 - design elaboration：比较机制 + 拒绝语义 + 策略裁定（Decision）

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.2（`:703-715` 13 行草图 → 含比较机制/拒绝行为/不可变性/序列化/DB 兼容的 elaboration）

- Item Types: `Decision`

- [x] **Decision A：比较机制（核心 design gap）**。裁定 restore 时 idempotency_key **与什么比较**。候选：(i) replay 到同水位时从当前 session 状态重算 key 并比较（须定义如何从 ChatMessage 派生 toolName/callId/inputSummary）；(ii) checkpoint 间比较（但 WATERMARK 是 PK，无重复 checkpoint——此路不通，须排除）；(iii) 与 tool-call 执行点对照（replay 执行 tool 前，从待执行输入算 key，与同水位 checkpoint 的 key 比）。裁定须给出可实现的派生路径，正视"restore 从持久化消息重建、非重新执行 tool"这一事实。回写 design §13.2。
- [x] **Decision B：拒绝 + 降级的可观测行为（design gap）**。今日 restore **从不把 checkpoint 当数据源**——session 重放已是唯一恢复路径（`AgentSessionLifecycle.restoreSession:483` buildBaseExecutionContext + `:537` dispatch），checkpoint 是"verification supplement"（`:443-445`）。"拒绝 + 降级 session 重放"须定义**与今日 warn+continue 的可观测区别**——候选：不同日志级别/事件类型/跳过恢复事件水印/标记 session 为 divergent。裁定须确保拒绝后恢复**不卡死**（降级路径可走）。回写 design §13.2。
- [x] **Decision C：不可变性策略**。`Checkpoint` 严格不可变（final 字段、私有构造、无 setter/builder），三实现接收已构造对象。裁定 key 写入方式：(i) `Checkpoint.of()` 工厂内算 key（调用方传原料）；(ii) `withIdempotencyKey()` 重建；(iii) saveCheckpoint 重建。回写 design §13.2。
- [x] **Decision D：`Checkpoint.of()` 参数策略**。裁定新增字段如何接入 100+ 调用点——重载 `of()`（旧签名委托新签名传 null）vs 全改调用点。枚举反序列化调用方（DBCheckpointManager readCheckpoint、CheckpointJournalReader parseSection）须从存储读新字段。回写 design §13.2。
- [x] **Decision E：序列化范围枚举**。列出全部须同步的序列化接触点：`CheckpointJournalWriter.serializeSection`（写 key 行）、`CheckpointJournalReader.parseSection`（读 key）、`DBCheckpointManager` SQL（INSERT 加列 + SELECT 加列 + readCheckpoint 读 ResultSet）、`Checkpoint.equals/hashCode/toString`（含新字段）、`CheckpointSnapshot`（若恢复需）。回写 design §13.2。
- [x] **Decision F：hash 公式 per CheckpointType**。`TOOL_EXECUTION` 有 toolName/callId/inputSummary → `hash(toolName+callId+inputSummary)`；`LLM_TURN`/`COMPACTION` 的 toolName/callId/inputSummary 为 null。裁定非 TOOL_EXECUTION 类型的 key 计算——候选：(i) `hash(type+watermark)`（确定性但每行不同）；(ii) null（不参与校验，明示非 TOOL_EXECUTION 不做发散检测）。裁定须标明正确性影响（若选 null，发散检测仅对 TOOL_EXECUTION 生效）。回写 design §13.2。
- [x] **Decision G：唯一约束 DB 兼容策略**。裁定可空唯一约束 vs 回填迁移 + 目标 DB（测试 DB 是 H2）。须兼容 PostgreSQL/H2（多 NULL 允许）。回写 design §13.2。

Exit Criteria:

- [x] design §13.2 含 7 项裁定（A-G）结论 + 理由，从 13 行草图升级为可执行规格
- [x] **裁定 A 比较机制可实现**：明确 restore 时 key 的比较对象 + 派生路径，且与 live 事实一致（WATERMARK 是 PK 无重复 checkpoint；restore 从持久化消息重建非重新执行）
- [x] **裁定 B 拒绝行为可观测且不卡死**：明确与今日 warn+continue 的区别 + 降级路径可走
- [x] 裁定已为 Phase 2/3 设界：所选策略须使实现单计划可关闭
- [x] No owner-doc update beyond design（checkpoint 内部可靠性，非平台用户可见 API）
- [x] No new test required: design-only phase（Rule #25）
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 裁定

### Phase 2 - idempotency_key 数据模型 + 计算 + 序列化（无 restore 行为变更）（Fix | Proof）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/Checkpoint.java`、`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:119-159`、`.../reliability/AiAgentCheckpointTable.java`、`.../reliability/ICheckpointManager.java`、`CheckpointJournalWriter`/`CheckpointJournalReader`、`DBCheckpointManager` SQL、`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（§13.2 hash 公式 + 兼容策略回写）

- Item Types: `Fix | Proof`

> **ORM 结构变更属 protected area（plan-first）**——本计划即 plan-first 产物。`app.orm.xml` 是手写源模型（非 `_`-prefixed 生成），是正确编辑目标。

- [x] `Checkpoint` 增 `idempotency_key`（String，可空）字段 + 按 Phase 1 裁定 C/D 接入工厂（重载或重建）+ `equals()/hashCode()/toString()` 含新字段。
- [x] `app.orm.xml` `ai_agent_checkpoint` 增 `idempotency_key` 列（propId 13）+ 按 Phase 1 裁定 G 的唯一约束（兼容策略）；`AiAgentCheckpointTable` DDL 同步。
- [x] **hash 计算**（Phase 1 裁定 F 公式）：TOOL_EXECUTION → `hash(toolName + callId + inputSummary)`（平台既有 SHA-256，参考 `DenialResult.actionFingerprint`）；非 TOOL_EXECUTION 按裁定 F。
- [x] 按 Phase 1 裁定 C 写入 key（工厂/重建/saveCheckpoint），三实现（ToolExecutionCheckpoint/DBCheckpointManager/FileBackedCheckpointManager；NoOp 可跳过）。
- [x] **序列化同步**（Phase 1 裁定 E 全部接触点）：JournalWriter 写 key 行、JournalReader 读 key、DBCheckpointManager INSERT/SELECT/readCheckpoint、CheckpointSnapshot（若需）。
- [x] 单测：tool execution checkpoint → idempotency_key 非空且确定性；不同输入不同 key；旧 of() 工厂兼容（null）；序列化往返保留 key。

Exit Criteria:

- [x] `Checkpoint` 存在 `idempotency_key` 字段，工厂/equals/hashCode 同步，单测断言确定性 + 可空兼容
- [x] `app.orm.xml` + `AiAgentCheckpointTable` DDL 含 idempotency_key 列 + 唯一约束；WATERMARK 仍 PK；兼容策略已裁定并回写 design §13.2
- [x] **序列化全部接触点同步**（JournalWriter/Reader + DBCheckpointManager SQL ×3 + equals/hashCode），往返测试保留 key
- [x] saveCheckpoint 三实现（NoOp 除外）写入 idempotency_key，单测断言非空
- [x] hash 公式（含非 TOOL_EXECUTION 裁定）回写 design §13.2
- [x] **无静默跳过**：非 TOOL_EXECUTION 类型按裁定 F 显式处理（明示策略，非吞掉）
- [x] **零回归**：旧 of() 调用点编译通过；无 key 的 checkpoint 可保存/读取；restore 行为本 phase 不变（Phase 3 才改）
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase

### Phase 3 - restore 发散检测 + 拒绝降级（行为变更）（Fix | Proof）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java:438-458`（restore 校验块）、对应测试

- Item Types: `Fix | Proof`

- [x] `AgentSessionLifecycle.restoreSession`（`:438-458`）改造：按 Phase 1 裁定 A 的比较机制——restore 时比较 idempotency_key，不一致 → 按 Phase 1 裁定 B 的可观测行为**拒绝该 checkpoint，降级 session 重放**（design §13.2 `:710`），非 best-effort 继续。
- [x] **旧 checkpoint 兼容**：checkpoint 的 idempotency_key 为 null（旧数据/NoOp/非 TOOL_EXECUTION 按裁定 F）时，退回今日 best-effort messageCount 软校验行为（零回归）。
- [x] **降级路径验证**：拒绝 checkpoint 后 session 重放真的可走（恢复不卡死，Phase 1 裁定 B 已设界）。
- [x] 单测：key 一致 → 接受 checkpoint（今日行为）；key 不一致 → 按裁定 B 的可观测行为拒绝 + 降级重放；key 为 null → 退回 best-effort（兼容）。

Exit Criteria:

- [x] restore 路径存在"key 不一致 → 拒绝 + 降级 session 重放"分支，单测断言 Phase 1 裁定 B 定义的可观测拒绝行为（非 warn 后继续）
- [x] **端到端验证**：构造发散 checkpoint（按裁定 A 的比较机制触发不匹配）→ restoreSession 拒绝该 checkpoint → session 重放恢复（Minimum Rules #22：从 restoreSession 入口到恢复完成完整跑通）
- [x] **接线验证**：拒绝路径确实触发降级重放（非静默返回 null/空 session，非卡死）
- [x] **无静默跳过**：key 不一致时显式拒绝（按裁定 B 可观测行为）；key null 时显式退回 best-effort（明示策略）
- [x] **零回归**：key 一致行为不变；key null（旧数据）退回今日 best-effort
- [x] design §13.2 restore 拒绝语义 + 比较机制已回写
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase

## Closure Gates

> 本计划涉及代码 + ORM 结构 + design 变更，构建验证条目保留。

- [x] checkpoint idempotency_key 列 + 唯一约束存在（ORM + DDL），WATERMARK 保持 PK
- [x] saveCheckpoint 写入确定性 idempotency_key（hash 公式落地）
- [x] restore 发散检测成立：key 不一致 → 拒绝 + 降级 session 重放（非 best-effort）
- [x] 零回归：key 一致行为不变；旧 checkpoint（null key）退回 best-effort
- [x] 无静默跳过：拒绝/兼容路径均有显式策略
- [x] design §13.2 已回写（hash 公式 + 唯一约束兼容策略 + restore 拒绝语义）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）saveCheckpoint 确实计算并写入 key，（b）restore 确实比较 key 并在发散时拒绝，（c）拒绝后降级重放完整跑通
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw compile` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### W2-1 WAIT_FOR 长等待原语（§13.1）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WAIT_FOR 需 suspend/wake 执行语义（ReAct 循环挂起不占线程 → 条件满足唤醒），是独立大型 design+实现，§13.5 推荐在前但与本计划无硬依赖。本计划不动 CheckpointType / ReAct 循环。
- Successor Required: yes
- Successor Path: W2 roadmap work item W2-1（WAIT_FOR design-first successor）

### W2-3 三级失败升级 / W2-4 跨 provider 故障转移

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属 W2 其他 work item，与 checkpoint 非确定性检测正交。
- Successor Required: yes
- Successor Path: W2 roadmap work item W2-3 / W2-4

## Non-Blocking Follow-ups

- idempotency_key 唯一约束的 DB 迁移回填脚本（若 Phase 1 裁定回填策略）。
- inputSummary 粒度调优（全量 vs 摘要指纹）——不影响发散检测正确性，仅影响精度。

## Closure

Status Note: W2-2 完成。checkpoint 增 idempotency_key 数据模型（Checkpoint 字段 + ORM 列 + DDL + unique index）+ 确定性 hash 计算（TOOL_EXECUTION → sha256Hex(toolName+callId+inputSummary)[:32]，非 TOOL_EXECUTION → null）+ 全序列化接触点同步（Journal/DB 透传存储 key 不重算）+ restore 发散检测（从持久化 session tool-call 消息重算 key 比较，不一致 → 拒绝 checkpoint + 降级 session 重放，不卡死；null key 退回 best-effort 零回归）。三 Phase 全部落地，3024 tests 0 failures，独立 closure-audit PASS（12 项全核验 live code，无缺陷）。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session ses_042c0a0aeffem0fojqzId13EtF，general 类型，未参与实现）
- Audit Session: ses_042c0a0aeffem0fojqzId13EtF
- Evidence:
  - **CLOSURE_AUDIT: PASS**（12 项 Exit Criterion/Closure Gate 逐条核验 live code，PASS）
  - Phase 2 数据模型：`Checkpoint.java:66/122-200`（idempotencyKey 字段 + 11 参 of() 算 key 委托 12 参 + computeIdempotencyKey TOOL_EXECUTION→32 hex / 其他→null + equals/hashCode/toString 含字段）；`app.orm.xml:159` propId 13 + `AiAgentCheckpointTable.java:93/106-108` DDL 列 + unique index（WATERMARK 仍 PK `:94`）
  - Phase 2 序列化：`DBCheckpointManager.java:186/420/456-469`（INSERT index 12 / SELECT / readCheckpoint 透传 12 参 of() 不重算）+ `initSchema:137` 执行 unique-index DDL；`CheckpointJournalWriter.java:100` 写 key 行 + `CheckpointJournalReader.java:220-224` 读 key（缺字段→null legacy 兼容）
  - Phase 3 restore 发散检测：`AgentSessionLifecycle.java:475-489`（非 null key → recomputeToolExecutionKey 比较；mismatch/not-found → divergenceDetected=true）+ `recomputeToolExecutionKey:732-754`（遍历 session 消息找匹配 callId 重算）+ `:481-487` distinct warn + `:510-512` SESSION_RESTORED payload divergenceDetected/rejectedCheckpointWatermark；拒绝不阻断（`buildBaseExecutionContext:522` + `executor.execute:576` 无条件执行）
  - **Anti-Hollow 检查**：(a) saveCheckpoint 计算+写入 key CONFIRMED（3 dispatch site 用 11 参 of() 自动算 key + DBCheckpointManager 持久化）；(b) restore 比较 key 发散时拒绝 CONFIRMED；(c) reject+degrade 端到端不卡死 CONFIRMED（`restoreSession_keyMismatch_rejectsCheckpointAndDegradesToReplay` 断言 completed + divergenceDetected=true）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 critical/high/medium/low findings）
  - Deferred 项分类检查：W2-1/W2-3/W2-4 为 out-of-scope improvement（独立 successor，非 in-scope live defect 降级）
  - 测试：`TestCheckpointIdempotencyKey`（12 tests）+ `TestRestoreSessionIdempotencyDivergence`（4 tests）独立子 agent 重跑确认 0 failures；全模块 `./mvnw test -pl nop-ai/nop-ai-agent -am` = 3024 tests 0 failures

Follow-up:

- idempotency_key unique index 的 MySQL<8.0.16 回填迁移脚本（裁定 G 已裁定，H2/PostgreSQL 目标 DB 无需；MySQL 单 NULL 限制为 Non-Blocking Follow-up）
- inputSummary 粒度调优（全量 vs 摘要指纹）——不影响发散检测正确性，仅影响精度
- 无剩余 plan-owned debt
