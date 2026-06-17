# 182 nop-ai-agent Checkpoint Journal Format (journal.md + snapshot.json + watermark recovery)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: A4 (Checkpoint Journal 格式: journal.md + snapshot.json dual-file, watermark recovery)

> Last Reviewed: 2026-06-15
> Source: Carry-over from plan 181 (`ai-dev/plans/181-nop-ai-agent-checkpoint-manager.md`) — `Deferred But Adjudicated` "journal.md + snapshot.json 双文件格式 (roadmap A4)" (`Successor Required: yes, Successor Path: roadmap A4 独立计划（L3-4 ✅ 后可规划）`). Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 row A4 (`:148` — `❌`, deps L3-4 ✅ satisfied). Design owner doc: `nop-ai-agent-reliability.md` §5.4a (Checkpoint Journal 格式).
> Related: Plan 181 (L3-4 ICheckpointManager — direct predecessor; this plan is its A4 carry-over), Plan 180 (L3-6 sticky-pause recovery — crash/restart restore carry-over, consumes A4 files), Plan 179 (DBDenialLedger — DB-backed persistence pattern precedent), Plan 177 (L3-6 denial-ledger contract — sibling contract-surface pattern)

## Purpose

将可靠性层 `nop-ai-agent-reliability.md` §5.4a 的 **Checkpoint Journal 双文件持久化格式** 收口到"格式已落地 + 文件级 save→persist→reload→retrieve 往返可用"状态：交付 journal.md（追加写入，source of truth）+ snapshot.json（派生缓存，可重建）双文件格式 + 按 watermark 恢复的读取路径 + 文件级 `ICheckpointManager` 功能化实现（`ToolExecutionCheckpoint` 的文件持久化对应物，同一契约的 drop-in 替换）。

当前状态：L3-4（plan 181）已交付 `ICheckpointManager` 契约 + `NoOpCheckpoint` 透传默认 + `ToolExecutionCheckpoint` in-memory 功能化实现 + executor/engine 分发循环接线。但 checkpoint 仅存在于内存中——进程重启即丢失。§5.4a 的 journal.md + snapshot.json 双文件格式在仓库中 **0 个文件**（`grep -r "journal\.md\|snapshot\.json\|CheckpointJournal\|JournalEntry\|SnapshotWriter" nop-ai/nop-ai-agent/src/main` 匹配 0 个格式实现，仅匹配到 `ICheckpointManager` 等 L3-4 契约文件中引用 watermark 的 Javadoc）。本计划交付文件持久化格式 + watermark 恢复读取路径，使 checkpoint **跨进程重启存活成为可能**。

## Current Baseline

- **L3-4 ✅ satisfied (sole dependency)**: `ICheckpointManager` 接口 + `Checkpoint` 值类型（11 字段：sessionId/watermark/seq/timestamp/type/toolName/callId/inputSummary/outputSummary/messageCount/tokenEstimate）+ `CheckpointType` 枚举（TOOL_EXECUTION + 预留 LLM_TURN/COMPACTION）+ `NoOpCheckpoint` 透传默认 + `ToolExecutionCheckpoint` in-memory 功能化实现，全部存在于 `io.nop.ai.agent.reliability` 包
- **Executor/engine dispatch-path wiring ✅ established (L3-4)**: `ReActAgentExecutor` 在工具执行完成后调用 `checkpointManager.saveCheckpoint(...)`（`ReActAgentExecutor.java:918`，tool result 加入 context 之后、POST_ACTING hook 之前）。`DefaultAgentEngine` 持有 `checkpointManager` field + null-fallback setter + Builder pass。本计划的文件级实现是 **drop-in 替换**——同一契约，同一接线点，存储后端从 in-memory 换为文件
- **ToolExecutionCheckpoint pattern ✅ established**: per-session `ConcurrentHashMap<String, List<Checkpoint>>` 存储 + watermark 索引 `ConcurrentHashMap<String, Checkpoint>`。文件级实现按同一 per-session + watermark 索引语义，但持久化到 journal.md（追加）+ snapshot.json（派生缓存）
- **Sibling DB-backed persistence pattern ✅ established (plan 179)**: `DBDenialLedger`（plan 179）交付了 `IDenialLedger` 契约的 DB 持久化实现——raw JDBC + per-session COUNT/INSERT/DELETE + dispatch-path 写入验证。本计划的文件级 checkpoint 持久化参照同一"契约不变 + 存储后端替换 + dispatch-path 验证"模式，但使用文件 I/O（journal.md + snapshot.json）而非 JDBC
- **§5.4a spec exists but unimplemented**: reliability.md §5.4a 定义 journal.md 格式（追加写入 markdown，每个 checkpoint 为 `## CP-NNN` 段落，含 type/seq/timestamp/entries/watermark）+ snapshot.json 格式（派生缓存 JSON，含 snapshotId/sessionId/lastWatermark/messageCount/tokenEstimate/toolResults/createdAt）+ 恢复流程（定位最近 snapshot → 从 lastWatermark 后重建增量 → 加载 firstKeptEntryId 后消息）。**本计划交付 journal.md + snapshot.json 格式 + watermark 恢复读取路径；消息加载（firstKeptEntryId）属 crash/restart restore successor 职责**
- **A4 journal/snapshot format ❌ NOT implemented**: 0 个 Java 文件实现 journal.md 写入/读取或 snapshot.json 写入/读取。`io.nop.ai.agent.reliability` 包无 journal/snapshot 格式类型。这是本计划要收口的 gap
- **§5.4a "仅 long-running tool" 限定**: §5.4a 规定持久化触发时机为"工具执行前后（仅 long-running tool）"。L3-4 Decision 明确将此过滤"下沉到持久化层（roadmap A4 journal/snapshot）应用"。本计划需处理此限定——见 Phase 2 Decision

## Goals

- **journal.md 格式**（追加写入，source of truth）：将 `Checkpoint` 序列化为追加写入的 markdown 段落（`## CP-NNN` + type/seq/timestamp/entries/watermark），符合 §5.4a 格式。journal.md 是 checkpoint 的持久化 source of truth——进程重启后可从文件完整重建 checkpoint 列表
- **snapshot.json 格式**（派生缓存，可重建）：将 checkpoint 聚合状态序列化为 JSON（snapshotId/sessionId/lastWatermark/messageCount/tokenEstimate/createdAt），符合 §5.4a 格式。snapshot.json 是恢复加速结构——加载 snapshot 后只需重放 lastWatermark 之后的 journal entries，而非扫描整个 journal.md
- **journal.md 写入器 + 读取器**：写入器将 Checkpoint 追加到 per-session journal.md 文件（`saveCheckpoint` 触发）；读取器解析 journal.md 重建 Checkpoint 列表（恢复路径消费）
- **snapshot.json 写入器 + 读取器**：写入器在配置阈值（每 N 个 checkpoint 或显式 flush）时生成 snapshot.json；读取器解析 snapshot.json 获取 lastWatermark + 上下文大小快照
- **watermark 恢复读取路径**：从 snapshot.json 加载 lastWatermark → 仅重放 journal.md 中 lastWatermark 之后的 entries → 重建完整 checkpoint 列表 → `getLatestCheckpoint` / `getCheckpoint(watermark)` 从重建后的内存索引返回。这是 §5.4a 恢复流程步骤 1-2（步骤 3 消息加载属 successor）
- **文件级 `ICheckpointManager` 功能化实现**（drop-in 替换）：实现同一 `ICheckpointManager` 契约（saveCheckpoint/getLatestCheckpoint/getCheckpoint），存储后端为 journal.md + snapshot.json。与 `ToolExecutionCheckpoint`（in-memory）是同一契约的两个功能化实现——选择哪个取决于是否需要跨进程持久化
- **dispatch-path drop-in 验证**：将 executor 的 `checkpointManager` 从 `ToolExecutionCheckpoint` 替换为文件级实现 → 同一 dispatch-loop 接线（`saveCheckpoint` 调用点不变）→ checkpoint 持久化到文件而非仅内存。证明 drop-in 兼容性
- **测试覆盖**：格式往返测试（journal.md write→read round-trip、snapshot.json write→read round-trip）、文件级 save→persist→reload→retrieve 测试、watermark 恢复测试（snapshot + journal replay）、dispatch-path drop-in 接线测试、向后兼容测试（NoOp/ToolExecution 不受影响）
- **设计文档 §5.4a 更新**：journal.md + snapshot.json 格式标记为"已落地"；架构决策记录（文件级 drop-in 模式、snapshot 生成时机、long-running 过滤裁定、Event Log 关系）
- **roadmap A4 行状态**从 `❌` 更新为 `✅`

## Non-Goals

- **Crash/restart durable session restore protocol (carry-over from plan 180/181)**: 从 checkpoint 文件重建 `AgentExecutionContext`（message history + execution state 恢复）+ 跨进程 session 接管锁机制 + firstKeptEntryId 后消息加载。本计划交付 checkpoint 文件持久化 + watermark 恢复读取路径（使恢复 **成为可能**），但 restore-on-restart 协议（executor 启动时消费 checkpoint 文件重建上下文）是独立 successor。平行于 L3-6 模式：plan 177 交付 save-side，plan 180 单独交付 restore-side
- **DB-backed checkpoint persistence**: 类比 L3-6（plan 177 契约 + NoOp，plan 179 DBDenialLedger）。本计划交付文件级持久化（journal.md + snapshot.json）。DB-backed 持久化（如 `DBCheckpointStore`，checkpoint 表 + DAO）是独立 successor，参照 plan 179 模式
- **LLM-turn checkpoint trigger point**: §5.4a 列出 LLM turn 完成后写入 journal entry。L3-4 仅交付 tool-execution 触发点（`CheckpointType.TOOL_EXECUTION`）。LLM-turn checkpoint（`CheckpointType.LLM_TURN` 发射 + dispatch-loop 接线）是后续增强
- **Compaction-triggered snapshot generation**: §5.4a 规定"压缩时生成完整 snapshot"。本计划交付 snapshot.json 格式 + 周期性/按需生成。compaction-triggered snapshot（在上下文压缩时自动生成）是后续增强
- **Checkpoint retention/rotation policy**: journal.md 无界追加（测试规模下安全）；旧 checkpoint 自动清理、journal 轮转、保留窗口策略是后续增强
- **Automatic restore-on-engine-startup**: 引擎启动时自动检测未完成 session 并从 checkpoint 文件恢复。这是 crash/restart durable recovery successor 的职责
- **Full snapshot.json aggregation (planStatus + toolResults)**: §5.4a snapshot.json 含 planStatus（plan 阶段/进度）+ toolResults（聚合工具结果）。这些字段需要 session 级状态访问（超出 checkpoint 值类型承载范围）。本计划交付 recovery-critical 字段（lastWatermark + messageCount + tokenEstimate + createdAt）；planStatus/toolResults 聚合属 crash/restart restore successor 或后续增强
- **Event Log (events.jsonl) integration**: §5.4a 提及 Journal 是 Event Log 的运行时加速结构。Event Log 集成（journal 从 Event Log 派生或同步）是独立增强，不在本计划 scope

## Scope

### In Scope

- journal.md 格式（markdown 序列化规范，per §5.4a）+ 写入器 + 读取器
- snapshot.json 格式（JSON 序列化规范，per §5.4a）+ 写入器 + 读取器
- watermark 恢复读取路径（snapshot lastWatermark → journal replay → checkpoint 列表重建）
- 文件级 `ICheckpointManager` 功能化实现（drop-in 替换 `ToolExecutionCheckpoint`，存储后端为文件）
- dispatch-path drop-in 验证（executor `saveCheckpoint` 调用点不变，存储后端替换为文件）
- 契约测试 + 格式往返测试 + 文件级 save→persist→reload→retrieve 测试 + watermark 恢复测试 + drop-in 接线测试 + 向后兼容测试
- 端到端验证：engine.execute → 工具调用 → saveCheckpoint → journal.md 写入 → 重新加载 → getLatestCheckpoint 从文件检索到
- 设计文档 §5.4a 更新 + roadmap A4 行状态更新

### Out Of Scope

- Crash/restart durable session restore protocol（plan 180/181 carry-over successor）
- DB-backed checkpoint persistence（独立 successor，参照 plan 179 模式）
- LLM-turn checkpoint trigger point（`CheckpointType.LLM_TURN` 发射）
- Compaction-triggered snapshot generation
- Checkpoint retention/rotation policy
- Automatic restore-on-engine-startup
- Full snapshot.json aggregation（planStatus + toolResults）
- Event Log (events.jsonl) integration

## Execution Plan

### Phase 1 - journal.md format + snapshot.json format + serializers + round-trip tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/` (journal/snapshot format types + writers/readers); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（格式类型归属）**: journal/snapshot 格式类型放入 `io.nop.ai.agent.reliability` 包（与 L3-4 checkpoint 类型同包），因为 journal/snapshot 是 checkpoint 持久化的格式层，属可靠性层而非安全治理层
- [x] **Decision（journal.md 序列化规范）**: 每个 `Checkpoint` 序列化为 `## CP-{seq}` markdown 段落，含 type / seq / timestamp（ISO-8601）/ entries（toolName/callId/inputSummary/outputSummary for TOOL_EXECUTION）/ watermark，per §5.4a 格式。文件首行为 `# Checkpoint Journal - {sessionId}` header。追加写入（append-only）——saveCheckpoint 仅追加段落，不重写已有内容
- [x] **Decision（snapshot.json 序列化规范）**: snapshot 序列化为 JSON 对象，含 snapshotId / sessionId / lastWatermark / messageCount / tokenEstimate / createdAt，per §5.4a 格式（recovery-critical 字段子集——planStatus/toolResults 聚合属 Non-Goal）。snapshot.json 是派生缓存——可从 journal.md 完整重建，丢失不导致数据损失
- [x] **Decision（snapshot 生成时机）**: snapshot.json 在配置阈值（默认每 N 个 checkpoint，N 可配置）或显式 flush 时生成。compaction-triggered snapshot 属 Non-Goal。默认阈值确保 snapshot 不会过时（恢复时 journal replay 范围有界），同时避免每次 saveCheckpoint 都重写 snapshot.json 的 I/O 成本
- [x] 新增 journal.md 写入器：将 `Checkpoint` 序列化为 §5.4a markdown 段落并追加到 per-session journal.md 文件。线程安全（多线程 saveCheckpoint 时追加写入需同步——per-session 文件锁或等效）
- [x] 新增 journal.md 读取器：解析 journal.md 文件，重建 `Checkpoint` 列表（按 seq 排序）。支持全量读取（恢复时从文件头扫描）和增量读取（从指定 watermark 之后开始，用于 snapshot-accelerated recovery）
- [x] 新增 snapshot.json 写入器：将聚合状态（lastWatermark + 最新 messageCount/tokenEstimate + createdAt）序列化为 JSON 写入 per-session snapshot.json 文件
- [x] 新增 snapshot.json 读取器：解析 snapshot.json 文件，返回 lastWatermark + 上下文大小快照
- [x] 格式往返测试：journal.md write→read round-trip——写入 N 个 Checkpoint → 读取 → 还原的 Checkpoint 列表与原始一致（字段完整：sessionId/watermark/seq/timestamp/type/toolName/callId/inputSummary/outputSummary/messageCount/tokenEstimate）
- [x] 格式往返测试：snapshot.json write→read round-trip——写入 snapshot → 读取 → 字段一致（snapshotId/sessionId/lastWatermark/messageCount/tokenEstimate/createdAt）
- [x] 格式往返测试：journal.md 增量读取——写入 N 个 Checkpoint → 从 watermark=W 的 entry 之后读取 → 仅返回 W 之后的 entries（用于 snapshot-accelerated recovery）
- [x] 格式往返测试：journal.md append-only 语义——多次写入不覆盖已有内容，文件持续增长
- [x] 边界测试：空 journal.md（0 entries）读取返回空列表不抛异常；损坏/截断的 journal entry 解析行为明确（跳过损坏 entry 并记录，或快速失败——Decision 在实现时裁定）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] journal.md 写入器 + 读取器存在且可独立工作（不依赖 ICheckpointManager 接线）
- [x] snapshot.json 写入器 + 读取器存在且可独立工作
- [x] journal.md 序列化遵循 §5.4a 格式（`## CP-NNN` 段落 + type/seq/timestamp/entries/watermark）
- [x] snapshot.json 序列化遵循 §5.4a 格式（recovery-critical 字段子集）
- [x] **新增功能测试**（Minimum Rules #25）：journal.md 往返 + snapshot.json 往返 + journal 增量读取 + append-only 语义 + 边界（空文件/截断）——各有对应通过的测试
- [x] **无静默跳过**（Minimum Rules #24）：损坏 entry 处理是显式的（记录 + 跳过 或 快速失败），非静默吞掉；空文件读取返回空列表不抛异常是合法语义（Javadoc 声明）
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 1 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - File-backed ICheckpointManager + watermark recovery + dispatch-path drop-in + end-to-end + backward compat

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/` (file-backed ICheckpointManager impl); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/` + `engine/`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（文件级实现是 drop-in 替换，非新契约）**: 文件级 `ICheckpointManager` 实现与 `ToolExecutionCheckpoint` 实现同一契约（saveCheckpoint/getLatestCheckpoint/getCheckpoint）。executor/engine 的 dispatch-path 接线（L3-4 已完成）不变——`saveCheckpoint` 调用点（`ReActAgentExecutor.java:918`）不变。存储后端从 in-memory 换为 journal.md + snapshot.json。选择哪个实现取决于是否需要跨进程持久化（Builder 注入点不变）
- [x] **Decision（§5.4a "仅 long-running tool" 过滤裁定）**: §5.4a 规定持久化触发时机为"仅 long-running tool"。文件级实现默认记录 **所有** tool-execution checkpoint（与 L3-4 in-memory 行为一致），通过可配置过滤（tool 名称集合或谓词）支持生产环境仅持久化 long-running tool。理由：(1) "long-running" 是部署特定概念，框架层无法统一定义；(2) 默认全记录与 L3-4 一致，降低认知负担；(3) 过滤是非阻塞配置增强，不影响格式契约。Javadoc 声明此裁定 + §5.4a 关系
- [x] **Decision（文件存储位置）**: per-session journal.md + snapshot.json 存放于可配置的 checkpoint 根目录下的 `{sessionId}/` 子目录。默认根目录通过 Nop 配置注入。per-session 隔离确保并发 session 不互相干扰（与 `ToolExecutionCheckpoint` per-session 隔离语义一致）
- [x] 新增文件级 `ICheckpointManager` 功能化实现：`saveCheckpoint` 调用 journal.md 写入器追加 checkpoint 段落 + 按 snapshot 阈值调用 snapshot.json 写入器；`getLatestCheckpoint` / `getCheckpoint` 从内存索引返回（索引在初始化时从文件加载或在首次访问时 lazy-load）
- [x] watermark 恢复读取路径：文件级实现初始化（或 lazy-load）时——(1) 读取 snapshot.json 获取 lastWatermark；(2) 从 journal.md 中 lastWatermark 之后增量读取 entries；(3) 重建完整 checkpoint 列表 + watermark 索引。若 snapshot.json 不存在，全量扫描 journal.md（降级路径，正确但不加速）
- [x] 功能化测试（**save→persist→reload→retrieve 往返——核心价值**）：文件级实现 saveCheckpoint N 个 checkpoint → 关闭实例 → 新建实例（模拟进程重启）→ getLatestCheckpoint 返回最后一个 + getCheckpoint(watermark) 按 watermark 精确检索。证明跨实例 checkpoint 存活（与 `ToolExecutionCheckpoint` 仅进程内存存活形成对比）
- [x] 功能化测试（watermark 恢复加速）：写入 N 个 checkpoint + 生成 snapshot（lastWatermark=W）→ 关闭实例 → 新建实例 → 验证实例从 snapshot + journal replay 加载（而非全量扫描）→ getLatestCheckpoint 返回正确结果
- [x] 功能化测试（降级恢复——无 snapshot）：写入 N 个 checkpoint（不生成 snapshot）→ 关闭实例 → 新建实例 → 全量扫描 journal.md 重建 → getLatestCheckpoint / getCheckpoint 正确返回
- [x] 功能化测试（per-session 隔离）：session A 的 journal.md 不影响 session B（`getLatestCheckpoint("A")` 不返回 session B 的 checkpoint）——参照 `ToolExecutionCheckpoint` per-session 独立性测试
- [x] drop-in 接线测试（**Anti-Hollow Wiring——运行时调用连通性 + 存储后端替换**）：构造 executor + 文件级 `ICheckpointManager`（替换 `ToolExecutionCheckpoint`）+ 至少 1 个工具调用 → 执行 ReAct 循环 → 工具执行后 `saveCheckpoint` **确实被调用**且 checkpoint **持久化到 journal.md 文件**（验证文件存在且内容正确）。证明 dispatch-path 接线与 L3-4 一致，存储后端替换不影响调用链连通性
- [x] 向后兼容测试：executor/engine 设 `ToolExecutionCheckpoint`（in-memory）→ ReAct 循环正常 → 不写文件；设 `NoOpCheckpoint`（默认）→ ReAct 循环正常 → 0 副作用 → 全部现有测试通过。证明文件级实现是可选 drop-in，不影响已有行为
- [x] 端到端测试（**Anti-Hollow 端到端——Minimum Rules #22**）：从用户入口点 `engine.execute(request)` → ReAct 循环 → 工具调用 → `saveCheckpoint` 持久化到 journal.md → 关闭 engine/executor → 新建 engine + 文件级 manager（指向同一 checkpoint 目录）→ `getLatestCheckpoint` 从文件检索到含正确 tool payload 的 checkpoint。证明从入口到文件持久化到跨实例恢复的完整路径连通

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 文件级 `ICheckpointManager` 实现存在且实现 saveCheckpoint/getLatestCheckpoint/getCheckpoint（非空壳）
- [x] `saveCheckpoint` 将 checkpoint 持久化到 journal.md（文件存在且内容符合 §5.4a 格式）
- [x] watermark 恢复读取路径可用：snapshot + journal replay 重建 checkpoint 列表（跨实例存活）
- [x] **接线验证**（Minimum Rules #23）：executor dispatch-loop `saveCheckpoint` 调用点（L3-4 已接线）与文件级实现兼容——drop-in 替换后 checkpoint 持久化到文件（非仅内存）
- [x] **端到端验证**（Minimum Rules #22 Anti-Hollow）：engine.execute → saveCheckpoint → journal.md 写入 → 跨实例 reload → getLatestCheckpoint 检索到——从入口到文件持久化到恢复的完整路径已验证
- [x] **Anti-Hollow Check**: 文件级 save→persist→reload→retrieve 往返 + dispatch-path drop-in 连通性 + per-session 文件隔离 + 降级恢复（无 snapshot 全量扫描）——四者均有通过测试证明（非仅类型系统存在）
- [x] **无静默跳过**（Minimum Rules #24）：文件级 save/retrieve/recovery 是真实实现（非空方法体/非 placeholder）；NoOp 默认是合法透传（Javadoc 声明）；损坏 entry 处理是显式的
- [x] **新增功能测试**（Minimum Rules #25）：文件级往返 + watermark 恢复 + 降级恢复 + per-session 隔离 + drop-in 接线 + 向后兼容 + 端到端——各有对应通过的测试
- [x] **向后兼容**: NoOp/ToolExecution 默认不受影响，全部现有测试通过
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Design doc §5.4a update + roadmap A4 status sync

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.4a, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 row A4 (`:148`)

- Item Types: `Follow-up`

- [x] 更新 `nop-ai-agent-reliability.md` §5.4a：(1) journal.md + snapshot.json 格式标记为"已落地"（格式 + 写入器/读取器 + 文件级 ICheckpointManager + watermark 恢复）；(2) 记录架构决策——文件级 drop-in 模式（同契约、存储后端替换）、snapshot 生成时机（周期性/按需，compaction 延期）、long-running 过滤裁定（默认全记录 + 可配置过滤）、Event Log 关系（journal 是 checkpoint source of truth，Event Log 是 event source of truth，两者独立）、recovery-critical 字段子集（planStatus/toolResults 延期）；(3) crash/restart restore protocol 仍标注为独立 successor
- [x] 更新 `nop-ai-agent-roadmap.md` §4 row A4（`:148`）：状态从 `❌` 改为 `✅`（简注：journal.md + snapshot.json format + watermark recovery + file-backed ICheckpointManager landed；crash/restart restore + DB persistence + LLM-turn/compaction triggers = deferred successors）
- [x] 确认 crash/restart restore protocol 依赖现部分满足（A4 文件格式 ✅，但 restore-on-restart 运行时消费仍需独立 plan）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-reliability.md` §5.4a 已更新（格式标记已落地 + 架构决策记录 + crash/restart restore 仍标注为 successor）
- [x] `nop-ai-agent-roadmap.md` §4 row A4 状态为 `✅`
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`（文档变更无需重跑测试，但确认编译通过）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] journal.md 格式 + 写入器 + 读取器已落地（§5.4a 格式，追加写入 source of truth）
- [x] snapshot.json 格式 + 写入器 + 读取器已落地（§5.4a 格式，派生缓存）
- [x] watermark 恢复读取路径已落地（snapshot + journal replay 重建 checkpoint 列表）
- [x] 文件级 `ICheckpointManager` 功能化实现已落地（drop-in 替换 ToolExecutionCheckpoint，文件持久化）
- [x] dispatch-path drop-in 验证：executor `saveCheckpoint` 调用点与文件级实现兼容（L3-4 接线不变）
- [x] 端到端验证：engine.execute → saveCheckpoint → journal.md → 跨实例 reload → getLatestCheckpoint 完整路径连通
- [x] 向后兼容：NoOp/ToolExecution 默认不受影响，全部现有测试通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（reliability §5.4a、roadmap A4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) dispatch-loop → saveCheckpoint → journal.md 写入 → snapshot.json 写入 调用链在运行时连通，(b) 跨实例 save→persist→reload→retrieve 往返经测试证明（非空壳），(c) watermark 恢复（snapshot + journal replay）经测试证明，(d) NoOp/ToolExecution 默认不受影响
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/182-nop-ai-agent-checkpoint-journal.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Crash/restart durable session restore protocol

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: plan 180/181 明确延期此项。A4 交付 checkpoint 文件持久化格式 + watermark 恢复读取路径（使 crash/restart 恢复 **成为可能**——有文件可恢复）。但实际的 restore-on-restart 协议——executor 启动时从 checkpoint 文件重建 `AgentExecutionContext`（message history + execution state 恢复）+ firstKeptEntryId 后消息加载 + 跨进程 session 接管锁机制——是独立 successor。A4 的恢复读取路径是文件级 ICheckpointManager 内部的 checkpoint 列表重建，**不**在 executor 启动路径被消费（平行于 L3-4 plan 181 交付 save-side 而 crash/restart successor 单独交付 restore 的模式）
- Successor Required: yes
- Successor Path: 独立 governance/reliability plan（参照 plan 180 sticky-recovery 模式；consumes A4 文件格式）

### DB-backed checkpoint persistence

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: A4 交付文件级持久化（journal.md + snapshot.json），证明跨进程 checkpoint 存活。DB-backed 持久化（如 `DBCheckpointStore`，checkpoint 表 + DAO）提供生产级持久化 + 查询能力，是独立 successor——参照 plan 179（DBDenialLedger）和 plan 171（DBMessageService）的 DB-backed 实现模式。文件级持久化在单机部署中完全可用；DB-backed 是分布式/云部署增强
- Successor Required: yes
- Successor Path: 独立 plan（参照 plan 179 DB-backed 模式）

### LLM-turn checkpoint trigger point

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: §5.4a 列出多个 checkpoint 触发点。L3-4 交付 tool-execution 触发点（`CheckpointType.TOOL_EXECUTION`）。A4 交付文件格式（journal.md 序列化所有 `CheckpointType`，包括预留的 `LLM_TURN`/`COMPACTION`）。LLM-turn checkpoint 的 **发射**（dispatch-loop 在 LLM 响应后构造 `Checkpoint(type=LLM_TURN)` 并调用 saveCheckpoint）是后续增强，需要额外的 dispatch-loop 接线点
- Successor Required: yes
- Successor Path: 独立 enhancement plan

### Compaction-triggered snapshot generation

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: §5.4a 规定"压缩时生成完整 snapshot"。A4 交付 snapshot.json 格式 + 周期性/按需生成。compaction-triggered snapshot（在 `IContextCompactor` 压缩时自动生成 snapshot.json）需要 compaction 子系统与 checkpoint 子系统的协作接线，是后续增强
- Successor Required: yes
- Successor Path: 独立 enhancement plan（依赖 L2-3 IContextCompactor 协作）

### Full snapshot.json aggregation (planStatus + toolResults)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: §5.4a snapshot.json 含 planStatus（plan 阶段/进度）+ toolResults（聚合工具结果）。这些字段需要 session 级状态访问（plan 状态管理子系统 + 工具结果聚合），超出 checkpoint 值类型承载范围。A4 交付 recovery-critical 字段子集（lastWatermark + messageCount + tokenEstimate + createdAt），足够支撑 watermark 恢复。planStatus/toolResults 聚合属 crash/restart restore successor（需要重建完整 session 状态）或独立增强
- Successor Required: yes
- Successor Path: crash/restart restore successor 或独立 enhancement plan

### Checkpoint retention/rotation policy

- Classification: `optimization candidate`
- Why Not Blocking Closure: journal.md 无界追加（测试规模下安全）。生产部署需要 journal 轮转（如按大小/时间切分）+ 旧 checkpoint 清理 + 保留窗口策略。这是运维增强，不影响格式契约或恢复路径正确性
- Successor Required: no

## Non-Blocking Follow-ups

- Crash/restart durable session restore protocol（plan 180/181 carry-over successor；consumes A4 文件格式）
- DB-backed checkpoint persistence（参照 plan 179 模式，独立 successor）
- LLM-turn checkpoint trigger point（`CheckpointType.LLM_TURN` 发射 + dispatch-loop 接线）
- Compaction-triggered snapshot generation（`IContextCompactor` 协作接线）
- Full snapshot.json aggregation（planStatus + toolResults）
- Checkpoint retention/rotation policy（journal 轮转 + 旧 checkpoint 清理 + 保留窗口）
- Automatic restore-on-engine-startup（crash/restart successor 的职责）
- Event Log (events.jsonl) integration（journal 作为 Event Log 加速结构）

## Closure

Status Note: plan 182 交付 roadmap A4 全部 in-scope 项——journal.md（追加写入 source of truth）+ snapshot.json（派生缓存）双文件格式 + 读写器 + 文件级 `ICheckpointManager`（`FileBackedCheckpointManager`，drop-in 替换 `ToolExecutionCheckpoint`）+ watermark 恢复读取路径 + dispatch-path drop-in 接线验证 + 端到端跨实例存活验证 + 向后兼容验证。checkpoint 跨进程重启存活已经测试证明。crash/restart restore protocol、DB-backed persistence、LLM-turn/compaction triggers 等明确标注为 deferred successors（Non-Blocking Follow-ups）。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: opencode (执行 agent self-audit per user instruction; independent subagent closure audit was conducted as a secondary verification pass)
- Audit Session: this execution session
- Evidence:
  - Phase 1 Exit Criteria (all PASS):
    - `CheckpointJournalWriter` (journal.md append writer) + `CheckpointJournalReader` (full + incremental reader) exist and work independently — `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/CheckpointJournalWriter.java`, `CheckpointJournalReader.java`
    - `CheckpointSnapshotWriter` + `CheckpointSnapshotReader` exist and work independently — same package
    - journal.md follows §5.4a format (`## CP-NNN` + type/seq/timestamp/entries/watermark) — verified by `TestCheckpointJournalSnapshotFormat.journalWriteReadRoundTripAllFieldsPreserved`
    - snapshot.json follows §5.4a recovery-critical field subset — verified by `TestCheckpointJournalSnapshotFormat.snapshotWriteReadRoundTripAllFieldsPreserved`
    - 14 new tests in `TestCheckpointJournalSnapshotFormat` all PASS (round-trip + incremental read + append-only + boundary/empty/corrupted)
  - Phase 2 Exit Criteria (all PASS):
    - `FileBackedCheckpointManager` implements saveCheckpoint/getLatestCheckpoint/getCheckpoint (non-hollow) — `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/FileBackedCheckpointManager.java`
    - saveCheckpoint persists to journal.md (§5.4a format) — verified by `TestFileBackedCheckpointDispatchPath.dropInWiringPersistsCheckpointToJournalFile` (file exists + content verified)
    - Watermark recovery path: snapshot + journal replay rebuilds checkpoint list across instances — verified by `TestFileBackedCheckpointManager.savePersistReloadRetrieveSurvivesNewInstance` + `snapshotAcceleratedRecoveryReturnsCorrectLatestAfterReload`
    - Degraded recovery (no snapshot) full-scan — verified by `TestFileBackedCheckpointManager.degradedRecoveryWithoutSnapshotFullScanIsCorrect`
    - Per-session file isolation — verified by `TestFileBackedCheckpointManager.perSessionCheckpointsAreIsolatedAcrossInstances`
    - Wiring verification (Minimum Rules #23): executor dispatch-loop saveCheckpoint call point compatible with file-backed impl — verified by `TestFileBackedCheckpointDispatchPath.dropInWiringPersistsCheckpointToJournalFile`
    - End-to-end (Minimum Rules #22 Anti-Hollow): engine.execute → saveCheckpoint → journal.md → cross-instance reload → getLatestCheckpoint — verified by `TestFileBackedCheckpointDispatchPath.endToEndEngineExecutePersistsAndReloadsCheckpointAcrossInstances`
    - Backward compat: NoOp/ToolExecution defaults unaffected — verified by `TestFileBackedCheckpointDispatchPath.toolExecutionCheckpointWritesNoFiles` + `noOpDefaultProducesNoSideEffectsAndRunsNormally` + `builderDefaultsWithoutCheckpointManager`
    - 12 new tests (7 + 5) all PASS
  - Phase 3 Exit Criteria (all PASS):
    - `nop-ai-agent-reliability.md` §5.4a updated (format marked landed + architecture decisions recorded + crash/restart restore still noted as successor)
    - `nop-ai-agent-roadmap.md` §4 row A4 status is ✅
  - Closure Gates (all PASS):
    - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/182-nop-ai-agent-checkpoint-journal.md --strict` → exit code 0
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` → exit code 0 (0 Critical / 0 High / 0 Medium / 0 Low findings)
    - `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS (1237 tests, 0 failures)
  - Anti-Hollow check result: traced dispatch-loop → `ReActAgentExecutor.java:918` `checkpointManager.saveCheckpoint(...)` → `FileBackedCheckpointManager.saveCheckpoint` → `CheckpointJournalWriter.appendCheckpoint` (file write verified). Snapshot generation traced through `FileBackedCheckpointManager.writeSnapshotForSession` → `CheckpointSnapshotWriter.write`. All code paths contain real logic (no empty method bodies, no silent skips, no TODO placeholders). NoOp pass-through is explicitly documented as legitimate default semantics.
  - Deferred items classification check: all 7 deferred items are correctly classified as `out-of-scope improvement` or `optimization candidate` with explicit non-blocking rationale. No in-scope live defect or contract drift was downgraded to non-blocking.

Follow-up:

- Crash/restart durable session restore protocol (plan 180/181 carry-over successor; consumes A4 file format)
- DB-backed checkpoint persistence (independent successor, refer to plan 179 pattern)
- LLM-turn checkpoint trigger point (`CheckpointType.LLM_TURN` emission + dispatch-loop wiring)
- Compaction-triggered snapshot generation (`IContextCompactor` collaboration wiring)
- Full snapshot.json aggregation (planStatus + toolResults)
- Checkpoint retention/rotation policy
- Automatic restore-on-engine-startup
- Event Log (events.jsonl) integration

## Follow-up handled by 183-nop-ai-agent-crash-restart-session-restore.md

The **Crash/restart durable session restore protocol** carry-over from this plan's `Deferred But Adjudicated` (Crash/restart durable session restore protocol — `Successor Required: yes, Successor Path: 独立 governance/reliability plan`) and `Non-Blocking Follow-ups` sections is handled by plan 183 (`ai-dev/plans/183-nop-ai-agent-crash-restart-session-restore.md`). This is the same carry-over also deferred from plans 180 and 181. Plan 183 delivers the crash/restart restore end-to-end: a `FileBackedSessionStore` (per-session JSON persistence, drop-in for `InMemorySessionStore`) as the restore foundation + `IAgentEngine.restoreSession(sessionId, approver, reason)` entry point (distinct from sticky-pause `resumeSession`) + restore protocol (load persisted session → `buildBaseExecutionContext` rebuild → `FileBackedCheckpointManager.getLatestCheckpoint` consistency check → resume ReAct loop) + `SESSION_RESTORED` event + end-to-end crash/restart survival test. This plan is where plan 182's checkpoint subsystem is **first consumed at runtime by a restore path** (not just save-side). Cross-process takeover lock (depends on L4-8 Actor Runtime), DB-backed session store (plan 179 pattern), automatic restore-on-startup detection, and compaction-aware loading remain deferred successors.
