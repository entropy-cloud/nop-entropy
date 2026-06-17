# 186 nop-ai-agent DB-backed Checkpoint Persistence

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: DBCheckpointManager

> Last Reviewed: 2026-06-15
> Source: Carry-over from plan 185 (`ai-dev/plans/185-nop-ai-agent-db-backed-session-store.md`) — `Deferred But Adjudicated` "DB-backed checkpoint persistence (DBCheckpointStore)" (`Successor Required: yes, Successor Path: 独立 plan（参照本计划 + plan 179 模式）`). 同一 carry-over 亦 deferred by plan 181 (`ai-dev/plans/181-nop-ai-agent-checkpoint-manager.md`) — `Deferred But Adjudicated` "DB-backed checkpoint persistence" (`Successor Required: yes, Successor Path: 独立 plan 参照 plan 179 DB-backed 模式`)。`ICheckpointManager.java:50` Javadoc + `Checkpoint.java:31-32` Javadoc + `ToolExecutionCheckpoint.java:20` Javadoc 均引用 DB-backed successor。Design owner doc: `nop-ai-agent-reliability.md` §5.4（`:264` — "DB-backed 持久化（如 `DBCheckpointStore`，参照 plan 179 `DBDenialLedger` 模式）是独立 successor"）。
> Related: Plan 185 (DBSessionStore — direct pattern precedent: raw JDBC + table constant class + ORM entity + H2 tests + hybrid/all-scalar column layout), Plan 182 (FileBackedCheckpointManager — the file-backed sibling this plan's DB-backed manager is the DB-backed drop-in for), Plan 181 (ICheckpointManager contract + ToolExecutionCheckpoint in-memory + dispatch-path wiring), Plan 179 (DBDenialLedger — second pattern precedent: raw JDBC + AiAgentDenialTable all-scalar columns), Plan 183 (restoreSession — consumes `getLatestCheckpoint` at `:827`)

## Purpose

将 `nop-ai-agent-reliability.md` §5.4 的 **DB-backed checkpoint persistence** 收口到"checkpoint 水位线持久化到数据库，跨进程 crash/restart 后任何服务实例都能检索到最近安全点"状态。Plan 181 交付了 `ICheckpointManager` 契约 + in-memory 功能化实现（`ToolExecutionCheckpoint`）；plan 182 交付了文件级持久化（`FileBackedCheckpointManager` — journal.md + snapshot.json 双文件格式），证明了 checkpoint 跨进程 **单文件系统** 重启存活。但文件级持久化受限于单文件系统——分布式/云部署中多个服务实例不共享文件系统，崩溃在节点 A 的 checkpoint 无法被节点 B 检索。本计划交付 `ICheckpointManager` 的 DB-backed 实现，将 checkpoint 持久化到 `ai_agent_checkpoint` 表，任何共享 DB 的服务实例都可以检索 checkpoint watermark。

这是可靠性持久化投资链的封顶节点——plan 185 已将 session state 持久化到 DB（`DBSessionStore` on `ai_agent_session`），本计划将 checkpoint state 持久化到 DB（`ai_agent_checkpoint`）。两者协同：session store 提供完整消息历史，checkpoint store 提供 resume-point watermark + 一致性校验。`DefaultAgentEngine.restoreSession`（`:827`）调用 `checkpointManager.getLatestCheckpoint(sessionId)` 获取 resume-point——DB-backed manager 使该调用在跨进程场景下返回与崩溃前一致的 watermark。

当前状态：`ICheckpointManager` 有 3 个实现（`NoOpCheckpoint` 默认 / `ToolExecutionCheckpoint` in-memory / `FileBackedCheckpointManager` 文件级），无 DB-backed 实现。`grep -r "DBCheckpointStore\|DBCheckpointManager\|DbCheckpoint" nop-ai/nop-ai-agent/src/main` 返回 **0 命中**（仅 `ToolExecutionCheckpoint.java:20` / `ICheckpointManager.java:50` / `Checkpoint.java:31` 的 Javadoc 引用 `DBCheckpointStore` 作为未来 successor）。raw JDBC + table constant class + ORM entity 的 DB-backed 实现模式已由 plan 185（`DBSessionStore`）/ plan 179（`DBDenialLedger`）/ plan 171（`DBMessageService`）建立。本计划交付 DB-backed `ICheckpointManager`。

## Current Baseline

- **`ICheckpointManager` contract ✅ stable (plan 181)**: 3 方法契约——`saveCheckpoint(Checkpoint)` / `getLatestCheckpoint(sessionId)` / `getCheckpoint(watermark)`。Javadoc 明确声明"the contract does not mandate persistence"（`:46-51`）——持久化是实现属性非接口契约（与 L3-6 finding L3-G5 收窄一致）。`NoOpCheckpoint` 默认不持久化；`ToolExecutionCheckpoint` in-memory 不持久化；`FileBackedCheckpointManager` 文件级持久化。DB-backed 实现遵循同一契约，仅存储后端从文件换为 DB
- **`Checkpoint` ✅ immutable value type (plan 181)**: 11 字段全简单类型——`sessionId`(String, nullable) / `watermark`(String, PK, non-null) / `seq`(int, per-session monotonic 0-based) / `timestamp`(long, epoch millis) / `type`(`CheckpointType` enum: TOOL_EXECUTION/LLM_TURN/COMPACTION) / `toolName`(String, nullable) / `callId`(String, nullable) / `inputSummary`(String, nullable) / `outputSummary`(String, nullable) / `messageCount`(int) / `tokenEstimate`(long)。无嵌套复杂对象（对比 `AgentSession` 含 `List<ChatMessage>` Jackson 多态）。`Checkpoint.of(...)` static factory + all-field equals/hashCode + no mutators。**关键差异**：`Checkpoint` 全简单类型意味着 DB 列布局可选择 all-scalar（无 CLOB 序列化）或 hybrid（scalar + CLOB），而 `AgentSession` 必须用 CLOB（因嵌套 ChatMessage 列表）
- **`CheckpointType` ✅ enum (plan 181)**: `TOOL_EXECUTION`(L3-4 已接线) / `LLM_TURN`(预留) / `COMPACTION`(预留)。DB 列存 enum name（VARCHAR），反序列化经 `CheckpointType.valueOf(name)`
- **`FileBackedCheckpointManager` ✅ (plan 182)**: 文件级 drop-in 替换 `ToolExecutionCheckpoint`。per-session `journal.md`(append-only source of truth) + `snapshot.json`(derived cache) 双文件格式。watermark 恢复路径：首次访问 session 时从文件加载（snapshot → journal 全量重建 byWatermark 索引）。checkpoint 跨进程 **单文件系统** 重启存活。本计划的 DB-backed manager 是 `FileBackedCheckpointManager` 的 DB-backed 对应——同契约、存储后端从文件换为 DB
- **nop-dao dependency ✅ present (plan 171)**: `nop-ai/nop-ai-agent/pom.xml` 已含 `nop-dao` 依赖（`DBSessionStore` / `DBDenialLedger` / `DBMessageService` 均使用）
- **DB-backed implementation pattern ✅ established (plan 185 + 179 + 171)**:
  - plan 185 `DBSessionStore`: raw JDBC + `AiAgentSessionTable`(table constant class) + `ai_agent_session` ORM entity + hybrid column layout(scalar columns + SESSION_DATA CLOB via `SessionFileWriter.serialize`/`SessionFileReader.deserialize`) + write-through `ConcurrentHashMap` cache + `MERGE INTO` upsert + H2 in-memory tests
  - plan 179 `DBDenialLedger`: raw JDBC + `AiAgentDenialTable`(table constant class) + `ai_agent_denial` ORM entity + **all-scalar column layout**（无 CLOB，每字段一列）+ H2 in-memory tests
  - plan 171 `DBMessageService`: raw JDBC + `AiAgentMessageTable` + `ai_agent_message` ORM entity + CLOB column(`MESSAGE_BODY`)
  - 两种列布局先例均存在——`Checkpoint` 全简单类型，两种布局均可
- **ORM model location ✅ three entities exist**: `_vfs/nop/ai/agent/orm/app.orm.xml` 含 `ai_agent_message` / `ai_agent_denial` / `ai_agent_session` 三个 entity + 15 domains。新增 `ai_agent_checkpoint` entity 编辑此文件。ORM 模型结构是 Protected Area（plan-first）——本计划即 plan-first 文档
- **Dispatch-path wiring ✅ exists (plan 181)**: `ReActAgentExecutor` dispatch loop 在工具执行完成后调用 `checkpointManager.saveCheckpoint(Checkpoint.of(...))`（`ReActAgentExecutor.java:946`）。`DefaultAgentEngine.checkpointManager` field（`:100`，default `NoOpCheckpoint.noOp()`）+ setter（`:453-455`，null-fallback）+ getter（`:463-464`）+ 传递给 `ReActAgentExecutor.Builder.checkpointManager(...)`（`:1160`）。DB-backed manager 通过 `setCheckpointManager(new DBCheckpointManager(dataSource))` 或 Builder 注入——**零引擎代码变更**
- **Restore path consumes checkpointManager ✅ (plan 183)**: `DefaultAgentEngine.restoreSession`（`:827`）调用 `checkpointManager.getLatestCheckpoint(sessionId)` 获取 resume-point metadata + 一致性校验（`checkpoint.messageCount ≤ session.messageCount`）。DB-backed manager 的 `getLatestCheckpoint` 使该调用跨进程返回一致 watermark——restore 协议不变，仅 checkpoint 来源从内存/文件变为 DB
- **`ToolExecutionCheckpoint.java:20` Javadoc ✅ references successor**: `"{@code DBCheckpointStore}) is an independent successor, following the plan"` —— 实现后更新此 Javadoc 从"successor"到"landed"

## Goals

- **DB-backed `ICheckpointManager`** 位于 `io.nop.ai.agent.reliability` 包——`ICheckpointManager` 的第四个实现（与 `NoOpCheckpoint` / `ToolExecutionCheckpoint` / `FileBackedCheckpointManager` 同级），将 `Checkpoint` 持久化到 `ai_agent_checkpoint` 表。通过 `setCheckpointManager(...)` 或 Builder 注入引擎——零引擎接线变更
- **`ai_agent_checkpoint` 表**: ORM 实体定义（`app.orm.xml` 新增 entity，plan-first per Protected Areas）+ table constant class（DDL + 列常量，遵循 `AiAgentSessionTable` / `AiAgentDenialTable` 模式）
- **持久化跨实例存活（核心价值）**: checkpoint 持久化到 DB 后，销毁 manager 实例并新建（共享同一 DB）→ `getCheckpoint(watermark)` / `getLatestCheckpoint(sessionId)` 返回与销毁前一致的 checkpoint——watermark 不因 manager 实例重建而丢失，任何服务实例可检索 resume-point
- **`getLatestCheckpoint` query semantics**: 返回指定 session 中 seq 最大的 checkpoint（最新安全点）。cache 命中时从 cache 返回 seq 最大者；cache-miss 时从 DB 加载该 session 全部 checkpoint 到 warm cache（`WHERE SESSION_ID = ? ORDER BY SEQ DESC`，**无 `LIMIT 1`**，同 `FileBackedCheckpointManager.ensureSessionLoaded` 的 warm-cache 模式）后返回 seq 最大者。非内存列表
- **`getCheckpoint` PK lookup**: 按 watermark（PK）直接检索单个 checkpoint
- **`saveCheckpoint` append semantics**: checkpoint 是 append-only（journal 模式），每次 `saveCheckpoint` 是 INSERT（非 upsert）——同 watermark 不重复保存（watermark 是唯一 PK）。与 `FileBackedCheckpointManager.saveCheckpoint` 的 append-only 语义一致（与 `DBSessionStore.save` 的 upsert 语义**不同**——session 是全量覆写，checkpoint 是追加）
- **向后兼容**: `NoOpCheckpoint` 保持 shipped 默认，`ToolExecutionCheckpoint` / `FileBackedCheckpointManager` 不受影响，全部现有测试通过
- **设计文档 §5.4 更新**: "DB-backed checkpoint persistence" 从"独立 successor"变为"已落地"；架构决策记录
- **Javadoc 更新**: `ICheckpointManager.java:50` / `Checkpoint.java:31` / `ToolExecutionCheckpoint.java:20` 中引用 `DBCheckpointStore` 作为 successor 的 Javadoc 更新为"已落地"

## Non-Goals

- **跨进程 / 多实例 checkpoint 接管锁**: 同 plan 185 Non-Goal。并发接管的锁机制依赖 L4-8 Actor Runtime（roadmap P3，未开始）。DB-backed manager 使 checkpoint **可被检索**（任何实例都能 `getCheckpoint` / `getLatestCheckpoint`），但"防止多实例同时从同一 checkpoint 恢复同一 session"的锁机制是 L4-8 的职责。本计划交付 DB-backed 存储 + 检索；takeover lock 是独立 successor
- **Checkpoint retention / rotation policy**: checkpoint 行在表中累积（每次工具执行后追加一行）。保留策略（TTL 删除、定期清理、旧 checkpoint 归档、journal 轮转）是维护优化。`SEQ` 列使 `DELETE WHERE SESSION_ID = ? AND SEQ < ?`（保留最近 N 个）成为可能，但执行清理的调度不在范围
- **Snapshot acceleration for `getLatestCheckpoint`**: `FileBackedCheckpointManager` 用 `snapshot.json` 加速 journal replay。DB-backed manager 的 `getLatestCheckpoint` 经 DB 加载该 session 全部 checkpoint 到 warm cache（`ORDER BY SEQ DESC`，无 `LIMIT 1`）后返回 seq 最大者，无需 snapshot 加速——DB 索引已是 acceleration layer。snapshot 概念不迁移到 DB-backed 实现
- **Connection pooling / DataSource management**: DB-backed manager 构造器接收外部 `DataSource`，不自行管理连接池。DataSource 的生命周期由部署层管理（与 `DBSessionStore` / `DBDenialLedger` / `DBMessageService` 一致）
- **LLM-turn / compaction checkpoint triggers**: `CheckpointType.LLM_TURN` / `COMPACTION` 枚举值已预留（plan 181），但 dispatch-loop 接线（LLM 响应后 / 压缩时发射 checkpoint）是独立增强（roadmap A4 deferred successors，同 plan 181/182 Non-Goal）
- **firstKeptEntryId compaction-aware 加载**: 恢复时按 compaction 截断点加载 checkpoint 子集。本计划加载全量 checkpoint（同 `FileBackedCheckpointManager` 的 journal 全量加载）。compaction-aware 截断是独立 successor
- **ORM migration tooling**: 本计划使用 raw DDL（`CREATE TABLE IF NOT EXISTS`），不引入 Flyway / Liquibase
- **提升到通用模块**: DB-backed manager 放在 nop-ai-agent（同 `DBSessionStore` / `DBDenialLedger` / `DBMessageService`）；若非 Agent 消费者出现再提升

## Scope

### In Scope

- `ai_agent_checkpoint` 表的 ORM entity（`_vfs/nop/ai/agent/orm/app.orm.xml` 新增）
- Table constant class（DDL + 列常量 + 索引常量，遵循 `AiAgentSessionTable` / `AiAgentDenialTable` 模式）
- DB-backed `ICheckpointManager` 实现（`io.nop.ai.agent.reliability` 包），实现 `saveCheckpoint` / `getLatestCheckpoint` / `getCheckpoint` 全部 DB-backed
- Write-through cache（`ConcurrentHashMap`，同 `FileBackedCheckpointManager` / `DBSessionStore` 模式）
- 跨实例存活（checkpoint 状态不因 manager 实例重建而丢失）
- 单元测试（H2 in-memory，无 mock）：schema 初始化 + save→get 往返 + 字段完整性 + getLatestCheckpoint + getCheckpoint + 跨 session 隔离 + 跨实例存活 + 损坏行隔离 + append-only 语义
- 端到端测试：持久化跨实例存活（销毁旧实例 → 建新实例共享 DB → checkpoint 一致）
- 接线验证：DB-backed manager 经 `setCheckpointManager` / Builder 注入引擎后，`restoreSession`（plan 183）使用 DB-backed `getLatestCheckpoint` 正常工作
- 向后兼容测试：`NoOpCheckpoint` / `ToolExecutionCheckpoint` / `FileBackedCheckpointManager` 默认行为不变，全部现有测试通过
- 设计文档 §5.4 更新 + Javadoc 更新 + roadmap carry-over 状态同步

### Out Of Scope

- 跨进程 / 多实例 checkpoint 接管锁（依赖 L4-8 Actor Runtime）
- Checkpoint retention / rotation / TTL policy
- Snapshot acceleration for `getLatestCheckpoint`（DB 索引替代 snapshot）
- Connection pooling / DataSource management
- LLM-turn / compaction checkpoint triggers（roadmap A4 deferred）
- firstKeptEntryId compaction-aware 截断加载
- ORM migration tooling
- 提升到通用模块

## Execution Plan

### Phase 1 - ORM entity + table constant class + schema tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml` (新增 `ai_agent_checkpoint` entity); `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/AiAgentCheckpointTable.java` (table constant class — new); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/`

- Item Types: `Decision | Proof`

- [x] **Decision（列布局——hybrid：scalar 列 + CLOB 用于 `inputSummary`/`outputSummary`，参照 `ai_agent_session` CLOB 先例 + `ai_agent_denial` scalar 先例）**: `Checkpoint` 全 11 字段均为简单类型（String / int / long / `CheckpointType` enum），**无嵌套复杂对象**（对比 `AgentSession` 含 `List<ChatMessage>` Jackson 多态）。但 `inputSummary` / `outputSummary` 承载的是**全量** tool arguments/response 内容（非"摘要"——`ReActAgentExecutor.java:946` dispatch loop 传入 `chatToolCall.getArgumentsText()`（per `ChatToolCall.java:63-67` 的全量 JSON-stringified arguments）+ `toolResponse.getContent()`（全量 tool response）），对 `file_write`/`file_read` 等工具可超出 VARCHAR 上限。因此 `ai_agent_checkpoint` 表采用 **hybrid 列布局**：其余 9 字段为 scalar 列（`watermark`/`sessionId`/`toolName`/`callId` 为 String、`checkpointType` 为 VARCHAR 存 enum name、`seq`/`messageCount` 为 INTEGER、`timestamp`/`tokenEstimate` 为 BIGINT），`inputSummary` / `outputSummary` 两字段为 CLOB（参照 plan 185 `ai_agent_session` 的 `SESSION_DATA` CLOB 先例，非 plan 179 `ai_agent_denial` 的 all-scalar 模式——denial 全字段短文本可 scalar，checkpoint 的两长文本字段承载全量 tool I/O 必须 CLOB）。理由：(1) 9 scalar 字段零序列化代码——save 时逐字段 `PreparedStatement.setX()`，get 时逐字段 `ResultSet.getX()`；(2) scalar 字段可直接 SQL 查询（`WHERE SESSION_ID = ? ORDER BY SEQ DESC`、`WHERE TYPE = ?` 等）；(3) 两 CLOB 字段经 `setCharacterStream`/`getCharacterStream` 处理，无 VARCHAR 长度上限风险。**替代方案（all-scalar，全字段 VARCHAR，参照 plan 179 `ai_agent_denial`）被拒绝**：`inputSummary`/`outputSummary` 承载全量 tool I/O（`ReActAgentExecutor.java:946`），对长输出工具会超出 VARCHAR 上限导致运行时 SQLException。具体 VARCHAR 长度在实现时裁定（`watermark` precision=100；其余 scalar 字段视类型定）
- [x] **Decision（类名——`DBCheckpointManager`，与 `FileBackedCheckpointManager` 平行）**: DB-backed 实现命名为 `DBCheckpointManager`（非 carry-over 引用的 `DBCheckpointStore`）。理由：(1) 直接平行于 `FileBackedCheckpointManager`（file-backed → DB-backed，同 "Manager" 后缀）；(2) 接口是 `ICheckpointManager`（"Manager" 后缀），实现保持一致。**注意**: 现有 Javadoc（`ICheckpointManager.java:50` / `Checkpoint.java:31` / `ToolExecutionCheckpoint.java:20`）和设计文档 §5.4（`:264`）引用 `DBCheckpointStore`——Phase 3 更新这些引用为 `DBCheckpointManager`（实现后的实际类名）。checkpoint 子系统已有命名不一致（`NoOpCheckpoint` / `ToolExecutionCheckpoint` 无 "Manager" 后缀，`FileBackedCheckpointManager` 有）——本裁定跟随最近的 `FileBackedCheckpointManager` 命名
- [x] 在 `_vfs/nop/ai/agent/orm/app.orm.xml` 新增 `ai_agent_checkpoint` entity（遵循现有 `AiAgentDenial` / `AiAgentSession` entity 的列定义风格）。复用现有 domain 或新增：`watermark`(precision=100, PK) / `sessionId`(复用 `sessionId` domain) / `seq`(INTEGER) / `checkpointTimestamp`(BIGINT, 复用 `denialTimestamp` 或 `epochMillis` domain) / `checkpointType`(precision=30) / `toolName`(复用 `toolName` domain) / `callId`(precision=100) / `inputSummary`(CLOB) / `outputSummary`(CLOB) / `messageCount`(INTEGER) / `tokenEstimate`(BIGINT)。**`SESSION_ID` 列必须 nullable（不加 NOT NULL）**——与 `AiAgentDenialTable`/`AiAgentSessionTable` 的 `SESSION_ID VARCHAR(100) NOT NULL` 不同，因为 `Checkpoint.sessionId` 可为 null（anonymous session，见 Phase 2 `saveCheckpoint` 写 null）。ORM 模型结构是 Protected Area——本计划即 plan-first 文档
- [x] 创建 table constant class `AiAgentCheckpointTable`（`io.nop.ai.agent.reliability` 包），定义：表名常量、列名常量、DDL CREATE TABLE（含 IF NOT EXISTS，遵循 `AiAgentSessionTable.DDL_CREATE_TABLE` 模式；**`SESSION_ID VARCHAR(100)` 不加 NOT NULL**——anonymous session 写 null，与 `AiAgentDenialTable`/`AiAgentSessionTable` 的 `SESSION_ID ... NOT NULL` 不同）、DDL CREATE INDEX（`IDX_AI_AGENT_CHECKPOINT_SESSION_SEQ ON ai_agent_checkpoint(SESSION_ID, SEQ)`——支撑 `getLatestCheckpoint` 的 `WHERE SESSION_ID = ? ORDER BY SEQ DESC` 查询）
- [x] 单元测试：DDL 在 H2 中成功建表 + 建索引（遵循 `TestAiAgentSessionTable` / `TestAiAgentDenialTable` 模式，使用 `SimpleDataSource` + H2 in-memory，无 mock）。测试幂等性（CREATE TABLE IF NOT EXISTS 可重复执行不抛异常）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `app.orm.xml` 含 `ai_agent_checkpoint` entity 定义，`./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `AiAgentCheckpointTable` 存在于 `io.nop.ai.agent.reliability` 包，含表名/列名/DDL/索引常量
- [x] **新增功能测试**: DDL 建表 + 建索引测试在 H2 中通过（Minimum Rules #25）
- [x] **无静默跳过**: DDL 执行失败时抛异常（不静默忽略 schema 初始化失败）（Minimum Rules #24）
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DBCheckpointManager implementation + unit tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java` (new); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（raw JDBC，遵循 DBSessionStore / DBDenialLedger / DBMessageService 模式）**: `DBCheckpointManager` 使用 raw JDBC（`DataSource` + `PreparedStatement`），非 `IOrmSession`。理由：(1) 与 plan 185 `DBSessionStore` + plan 179 `DBDenialLedger` + plan 171 `DBMessageService` 同级的兄弟实现模式，保持一致；(2) `ICheckpointManager` 操作是同步的 save/get，无后台轮询需求；(3) raw JDBC 对 CRUD 足够且无额外抽象层
- [x] **Decision（write-through cache，同 FileBackedCheckpointManager / DBSessionStore 模式）**: `DBCheckpointManager` 内部维护 `ConcurrentHashMap<String, Checkpoint>` byWatermark cache + `ConcurrentHashMap<String, List<Checkpoint>>` bySession cache（镜像 `FileBackedCheckpointManager` 的 `byWatermark` / `bySession` 结构）。`saveCheckpoint` write-through（更新 cache + DB INSERT）；`getCheckpoint` cache-miss 时从 DB SELECT（PK lookup）；`getLatestCheckpoint` 从 cache 返回（cache-miss 时从 DB 加载该 session 全部 checkpoint 到 bySession + byWatermark，同 `FileBackedCheckpointManager.ensureSessionLoaded` 模式）。**`loadedSessions` 负缓存**（`ConcurrentHashMap<String, Boolean>`，同 `FileBackedCheckpointManager.java:93` 的 `loadedSessions`）：记录已从 DB 加载过的 session，避免每次 `getLatestCheckpoint(unknown-sid)` 都重复 DB round-trip——session 首次查询后无论 DB 中有无 checkpoint 都标记为 loaded（空结果也标记，避免反复空查询）。cache 的必要性：ReAct dispatch loop 每次 `saveCheckpoint` 后可能紧跟 `getLatestCheckpoint`（restore 一致性校验），cache 避免每次 DB round-trip
- [x] **Decision（saveCheckpoint INSERT 语义——append-only，非 upsert）**: `saveCheckpoint(checkpoint)` 使用 SQL `INSERT INTO ai_agent_checkpoint (...) VALUES (...)`（非 `MERGE INTO`）。理由：(1) checkpoint 是 append-only 语义（同 `FileBackedCheckpointManager.saveCheckpoint` 的 journal append）——每次工具执行产生新 watermark 的新 checkpoint，不覆写已有 checkpoint；(2) `watermark` 是 PK 且全局唯一（`Checkpoint.of` 不允许 null watermark），同 watermark 的重复 INSERT 是违反不变量的编程错误（DB PK 约束会拒绝——抛 SQLException → `NopAiAgentException`，fail-fast 而非静默覆写）。**这与 `DBSessionStore.save` 的 `MERGE INTO` upsert 语义不同**——session 是全量覆写（同 sessionId 反复 save 最新状态），checkpoint 是追加（新 watermark 新行）。重复 watermark 场景：测试验证 INSERT 同 watermark 抛异常（fail-fast），而非静默覆写
- [x] **Decision（getLatestCheckpoint query semantics）**: `getLatestCheckpoint(sessionId)` 返回指定 session 中 `seq` 最大的 checkpoint。cache 命中（`bySession` 已加载该 session）时直接从 cache 返回 seq 最大者；cache-miss 时执行 `SELECT ... WHERE SESSION_ID = ? ORDER BY SEQ DESC`（**无 `LIMIT 1`**）加载该 session 全部 checkpoint 到 `bySession` + `byWatermark` cache（同 `FileBackedCheckpointManager.ensureSessionLoaded` 的 warm-cache 模式），然后返回 seq 最大者。与 `FileBackedCheckpointManager.getLatestCheckpoint` 的语义一致——seq 是 per-session 单调递增，最大 seq 即最新。anonymous session（`sessionId == null`）返回 `null`（同 `FileBackedCheckpointManager.getLatestCheckpoint` 的 null-session 处理）
- [x] **Decision（getCheckpoint PK lookup）**: `getCheckpoint(watermark)` 经 DB 查询 `SELECT ... WHERE WATERMARK = ?`（PK lookup，索引加速）。cache-miss 时 DB lazy-load。与 `FileBackedCheckpointManager.getCheckpoint` 的 `byWatermark.get(watermark)` 语义一致，但存储后端从内存 map 换为 DB PK 查询
- [x] **Decision（thread-safety——DB 操作 + ConcurrentHashMap）**: `DBCheckpointManager` 的线程安全由 DB 操作 + cache 并发保证。`saveCheckpoint` / `getLatestCheckpoint` / `getCheckpoint` 的 DB 操作是原子 SQL 语句（INSERT / SELECT），cache 是 `ConcurrentHashMap`。多 session 并发访问同一 manager 实例时，per-session 操作互不干扰（`WHERE SESSION_ID = ?` 隔离）。同 session 并发 `saveCheckpoint` 由 DB PK 约束 + cache `putIfAbsent` 保证（同 `FileBackedCheckpointManager` 的 per-session `synchronizedList` 模式）。无需额外锁（同 `DBSessionStore` / `DBDenialLedger` 的 DB-based thread-safety 模式）
- [x] 实现 `DBCheckpointManager implements ICheckpointManager`（`io.nop.ai.agent.reliability` 包）：
  - 构造器接收 `DataSource`（必填）；构造器初始化 schema（建表 + 建索引，遵循 `DBSessionStore` 构造器 `initSchema` 模式——`stmt.execute(AiAgentCheckpointTable.DDL_CREATE_TABLE)` + `stmt.execute(AiAgentCheckpointTable.DDL_CREATE_INDEX)`，SQLException 包裹为 `NopAiAgentException`）
  - `saveCheckpoint(checkpoint)`: null-check → INSERT 全 11 字段到 `ai_agent_checkpoint`（`inputSummary`/`outputSummary` 经 `setCharacterStream` 写 CLOB） → cache.put（byWatermark + bySession）。anonymous session（sessionId == null）仅写 byWatermark cache + DB（SESSION_ID 列存 null）。**与 `FileBackedCheckpointManager` 的差异**：file-backed 实现跳过 anonymous session 的 checkpoint（无 session 目录）；DB-backed 实现持久化 anonymous checkpoint（SESSION_ID 列存 null）——合理的增强，anonymous checkpoint 仍可经 `getCheckpoint(watermark)` PK lookup 检索
  - `getLatestCheckpoint(sessionId)`: null-check → cache 命中（bySession 非空）→ 返回 list 最后一个；cache-miss → `SELECT ... WHERE SESSION_ID = ? ORDER BY SEQ DESC`（无 `LIMIT 1`）加载该 session 全部 checkpoint 到 `bySession` + `byWatermark` cache → 返回 seq 最大者。session 无 checkpoint 返回 `null`
  - `getCheckpoint(watermark)`: null-check → cache 命中 → 返回；cache-miss → `SELECT ... WHERE WATERMARK = ?` → 有行则重建 `Checkpoint`（`inputSummary`/`outputSummary` 经 `getCharacterStream` 读 CLOB） + cache；无行返回 `null`
- [x] 单元测试（**save→get 往返——核心价值**）：`saveCheckpoint(Checkpoint with all fields)` → `getCheckpoint(watermark)` 返回完整 checkpoint（11 字段全一致——sessionId/watermark/seq/timestamp/type/toolName/callId/inputSummary/outputSummary/messageCount/tokenEstimate）。经 DB（非 cache）——测试中每次 get 前清空 cache 或用新实例
- [x] 单元测试（getLatestCheckpoint）：save 3 个 checkpoint（同 session，seq=0,1,2）→ `getLatestCheckpoint(sessionId)` 返回 seq=2 的 checkpoint。不同 session 的 checkpoint 互不干扰（cross-session isolation）
- [x] 单元测试（字段完整性）：覆盖所有 `Checkpoint` 字段——含 nullable 字段（toolName/callId/inputSummary/outputSummary 为 null）/ `CheckpointType` 各枚举值（TOOL_EXECUTION，LLM_TURN/COMPACTION 预留值也测试往返）/ 边界值（seq=0, messageCount=0, tokenEstimate=0）
- [x] 单元测试（append-only 语义 / 重复 watermark fail-fast）：`saveCheckpoint(cp-A)` → `saveCheckpoint(cp-A-same-watermark)` 抛 `NopAiAgentException`（DB PK 约束拒绝重复 INSERT）——验证 fail-fast 而非静默覆写。验证 DB 表行数 == 1（第一个 cp-A 仍在）
- [x] 单元测试（cross-session isolation）：session-A 的 checkpoint 不出现在 session-B 的 `getLatestCheckpoint` 中；`getCheckpoint(watermark-A)` 返回 session-A 的 checkpoint，不串
- [x] 单元测试（anonymous session）：`saveCheckpoint(Checkpoint with sessionId=null)` → `getCheckpoint(watermark)` 返回该 checkpoint（byWatermark 可检索）；`getLatestCheckpoint(null)` 返回 null（同 `FileBackedCheckpointManager` 的 null-session 处理）
- [x] 单元测试（empty session）：`getLatestCheckpoint(不存在的 sessionId)` 返回 null（非异常）；`getCheckpoint(不存在的 watermark)` 返回 null（非异常）
- [x] 向后兼容测试：`ToolExecutionCheckpoint` 行为不受影响（in-memory）；`FileBackedCheckpointManager` 行为不受影响（文件级）；全部现有 checkpoint 测试通过（`NoOpCheckpoint` 向后兼容由 plan 181 的 `TestNoOpCheckpointAndValue` 传递覆盖——无 DB 依赖，不需重复）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DBCheckpointManager` 存在于 `io.nop.ai.agent.reliability` 包，implements `ICheckpointManager`，实现 `saveCheckpoint`/`getLatestCheckpoint`/`getCheckpoint` 全部 DB-backed
- [x] `saveCheckpoint` 经 `INSERT`（非 upsert）将 checkpoint 持久化到 `ai_agent_checkpoint` 表（通过直接 SQL 查询表行数 + 各列内容验证，非仅 cache 状态）
- [x] `getLatestCheckpoint` 从 DB 返回指定 session 的 seq 最大 checkpoint（通过直接 SQL 查询表验证返回的是 DB 中的最新行，非 cache-only）
- [x] `getCheckpoint` 从 DB PK lookup（cache-miss 时 `SELECT WHERE WATERMARK = ?`），非仅 cache
- [x] **新增功能测试**（Minimum Rules #25）：save→get 往返 + getLatestCheckpoint + 字段完整性 + append-only/重复 watermark fail-fast + cross-session isolation + anonymous session + empty session——各有对应通过的测试
- [x] **无静默跳过**（Minimum Rules #24）：DB 操作失败抛异常（不吞 SQLException / 不静默返回 null）；重复 watermark INSERT 抛异常（fail-fast，不静默覆写）；schema 初始化失败抛异常（非静默忽略）
- [x] **接线验证**（Minimum Rules #23）：`DBCheckpointManager` 经 `setCheckpointManager(new DBCheckpointManager(dataSource))` 注入 `DefaultAgentEngine` 后可被构造 + `getLatestCheckpoint` 在 `restoreSession` 路径被调用——Phase 3 端到端验证完整调用链
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 2 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Cross-instance end-to-end + engine wiring + design doc

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/`（端到端测试）; `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`（引擎接线测试）; `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.4; `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`; Javadoc in `ICheckpointManager.java` / `Checkpoint.java` / `ToolExecutionCheckpoint.java`

- Item Types: `Proof | Follow-up`

- [x] 端到端测试（**持久化跨实例存活——核心价值**）：创建 `DBCheckpointManager` 实例 A（连接 H2 DB）→ `saveCheckpoint` 3 个 checkpoint（2 session，各 seq=0,1）→ `getLatestCheckpoint` 验证各 session 最新 → **销毁实例 A**（不再引用，cache 随 GC）→ 创建 `DBCheckpointManager` 实例 B（共享同一 H2 DB）→ `getCheckpoint(watermark)` 返回完整 checkpoint（11 字段一致）→ `getLatestCheckpoint(sessionId)` 返回 seq 最大的 checkpoint。**这证明持久化经 DB 而非内存——如果 DBCheckpointManager 内部偷偷用了内存状态，新实例 B 的 get 会返回 null。** 关闭一个 DB 连接重建实例是跨进程接管的核心模拟场景
- [x] 端到端测试（**引擎接线——restoreSession 使用 DB-backed checkpoint**）：构造 `DefaultAgentEngine`（注入 `DBSessionStore` + `DBCheckpointManager` 共享同一 `DataSource`）→ `execute` 生成 session + checkpoint（至少 1 个工具调用完成 → intra-execution `saveCheckpoint` 将 checkpoint 写入 DB）→ 模拟崩溃（新建 engine B + 新建 `DBCheckpointManager` 实例 B 共享同一 DB）→ `restoreSession(sessionId)` → `getLatestCheckpoint` 从 DB 加载 resume-point → session 从 DB 加载 + ReAct 循环续跑。**直接查询 `ai_agent_checkpoint` 表验证 checkpoint 行存在**（非仅 manager 对象接收了调用）
- [x] 端到端测试（向后兼容）：构造 `DefaultAgentEngine` 注入 `NoOpCheckpoint`（默认）→ 全部现有测试通过，0 行写入 `ai_agent_checkpoint` 表；构造 `DefaultAgentEngine` 注入 `FileBackedCheckpointManager` → 全部现有 restore 测试通过
- [x] 更新 `nop-ai-agent-reliability.md` §5.4：(1) "DB-backed 持久化（如 `DBCheckpointStore`，参照 plan 179 `DBDenialLedger` 模式）是独立 successor"（`:264`）→ 标记"已落地（plan 186，实现为 `DBCheckpointManager`）"；(2) 记录架构决策——raw JDBC（vs IOrmSession，与 DBSessionStore/DBDenialLedger/DBMessageService 一致）、hybrid 列布局（scalar 列 + CLOB 用于 `inputSummary`/`outputSummary`，因这两字段承载全量 tool I/O——对比 DBSessionStore 的单一 `SESSION_DATA` CLOB）、write-through cache（同 FileBackedCheckpointManager/DBSessionStore）、INSERT append-only（vs MERGE INTO upsert——checkpoint 是追加非覆写，与 DBSessionStore.save 的 upsert 不同）、getLatestCheckpoint 经 `ORDER BY SEQ DESC` SQL（vs FileBackedCheckpointManager 的 in-memory list 最后元素）；(3) 跨进程接管锁仍标注为 deferred（依赖 L4-8 Actor Runtime）
- [x] 更新 `nop-ai-agent-reliability.md` §1.1（如适用）：§1.1 的恢复模型已记录"plan 185 ✅ DB-backed session store"——追加 checkpoint 持久化的 DB-backed 状态（checkpoint watermark 也跨进程持久化，restore 协议的 `getLatestCheckpoint` 一致性校验在 DB-backed 场景下跨实例可用）
- [x] 更新 Javadoc：`ICheckpointManager.java:46-51`（"A DB-backed persistent checkpoint store is an independent successor" → 更新为已落地实现）、`Checkpoint.java:29-35`（"a future DB-backed successor will" → 更新为已落地）、`ToolExecutionCheckpoint.java:20`（"`DBCheckpointStore`) is an independent successor" → 更新为已落地 `DBCheckpointManager`）
- [x] 更新 `nop-ai-agent-roadmap.md`：A4 行（roadmap L148，"DB persistence"）标记 checkpoint DB 持久化已落地（实现为 `DBCheckpointManager`，plan 186）；同时 DBCheckpointStore carry-over（plans 181/185）标记已解决

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（持久化跨实例存活）：销毁旧 manager 实例 + 建新实例（共享 DB）→ getCheckpoint / getLatestCheckpoint 返回一致 checkpoint——证明持久化经 DB 而非内存（Minimum Rules #22 Anti-Hollow Rule）
- [x] **端到端验证**（restoreSession 使用 DB-backed checkpoint）：engine.execute → checkpoint 持久化到 DB → 模拟崩溃 → 新 engine（新 manager 实例共享 DB）→ restoreSession → `getLatestCheckpoint` 从 DB 加载 resume-point + session 从 DB 加载 + ReAct 续跑——从入口到 DB 持久化到跨实例恢复的完整路径已验证
- [x] **接线验证**（Minimum Rules #23）：`DBCheckpointManager` 经 `setCheckpointManager` 注入引擎后，`saveCheckpoint` 调用使 checkpoint **实际落入 `ai_agent_checkpoint` 表**（通过直接 SQL 查询表验证，非仅 manager 对象被传递）；`restoreSession` 的 `getLatestCheckpoint` 从 DB 加载 resume-point（非 cache-only）
- [x] **Anti-Hollow Check**: 跨实例存活测试证明 checkpoint 确实从 DB 读取（而非内存缓存）——两个独立 manager 实例无共享内存状态，仅共享 DB；如果 DBCheckpointManager 内部偷偷用内存状态，新实例 get 会返回 null，测试会失败
- [x] **无静默跳过**: DB 操作失败抛异常（非静默返回 null/空集合）；schema 初始化失败抛异常（非静默忽略）；重复 watermark INSERT 抛异常（非静默覆写）
- [x] **新增功能测试**: 持久化跨实例存活 + restoreSession DB-backed 接线（DB 写入验证）+ 向后兼容——各有对应通过的测试（Minimum Rules #25）
- [x] **向后兼容**: `NoOpCheckpoint` / `ToolExecutionCheckpoint` / `FileBackedCheckpointManager` 默认行为不变，全部现有测试通过
- [x] `nop-ai-agent-reliability.md` §5.4 已更新（DB-backed checkpoint persistence 标记为已落地 + 架构决策记录）
- [x] Javadoc（`ICheckpointManager.java` / `Checkpoint.java` / `ToolExecutionCheckpoint.java`）已从"successor"更新为"landed"
- [x] `nop-ai-agent-roadmap.md` carry-over 状态同步
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] DB-backed `ICheckpointManager` 已落地（saveCheckpoint/getLatestCheckpoint/getCheckpoint 全部 DB-backed）
- [x] `ai_agent_checkpoint` 表的 ORM entity + table constant class 存在
- [x] checkpoint 持久化到 DB（INSERT/SELECT 经 SQL，非内存状态）
- [x] 持久化跨实例存活已验证（销毁旧实例 + 建新实例共享 DB → getCheckpoint/getLatestCheckpoint 一致）
- [x] `restoreSession`（plan 183）使用 DB-backed `getLatestCheckpoint` 端到端工作（无引擎代码变更，drop-in 替换）
- [x] `NoOpCheckpoint` / `ToolExecutionCheckpoint` / `FileBackedCheckpointManager` 默认向后兼容（全部现有测试通过）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（reliability §5.4、§1.1 如适用、Javadoc、roadmap）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) saveCheckpoint → DB INSERT 调用链在运行时连通（经 DB 表，非自建内存状态），(b) 跨实例存活测试证明 checkpoint 确实从 DB 读取（两独立实例无共享内存仅共享 DB），(c) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/186-nop-ai-agent-db-backed-checkpoint-persistence.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 跨进程 / 多实例 checkpoint 接管锁

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: DB-backed manager 使 checkpoint **可被检索**（任何共享 DB 的服务实例都能 `getCheckpoint(watermark)` / `getLatestCheckpoint(sessionId)` 获取 resume-point）。但"防止多实例同时从同一 checkpoint 恢复同一 session"的并发接管锁（distributed lock / DB row lock / lease mechanism）是 L4-8 Actor Runtime 的职责（设计 §1.1 "并发接管的锁机制由 actor 调度系统负责"）。本计划交付 DB-backed 存储 + 检索能力；takeover lock 是独立 successor（同 plan 185 deferred）
- Successor Required: yes
- Successor Path: 依赖 L4-8 Actor Runtime 的独立 plan

### Checkpoint retention / rotation / TTL policy

- Classification: `optimization candidate`
- Why Not Blocking Closure: checkpoint 行在表中累积（每次工具执行后追加一行）。保留策略（保留最近 N 个 checkpoint、TTL 删除、旧 checkpoint 归档、journal 轮转）是维护优化，不改变 `DBCheckpointManager` 契约。`SEQ` 列使 `DELETE WHERE SESSION_ID = ? AND SEQ < ?`（保留最新 N 个）成为高效 SQL，但执行清理的调度不在范围（同 plan 185 session TTL Non-Goal）
- Successor Required: no

### LLM-turn / compaction checkpoint triggers

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `CheckpointType.LLM_TURN` / `COMPACTION` 枚举值已预留（plan 181），但 dispatch-loop 接线（LLM 响应后发射 `Checkpoint(type=LLM_TURN)` + 压缩时发射 `Checkpoint(type=COMPACTION)`）是独立增强（roadmap A4 deferred successors，同 plan 181/182 Non-Goal）。DB-backed manager 会持久化任何 `CheckpointType`（包括预留的 `LLM_TURN`/`COMPACTION`）——枚举值在 `ai_agent_checkpoint.CHECKPOINT_TYPE` 列中经 `CheckpointType.valueOf(name())` 往返。触发点的发射接线不在范围
- Successor Required: yes
- Successor Path: roadmap A4 独立 plan（LLM-turn/compaction dispatch-loop 接线）

### firstKeptEntryId compaction-aware 截断加载

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 恢复时按 compaction 截断点加载 checkpoint 子集（跳过已被 compaction 丢弃的历史 checkpoint）。本计划加载全量 checkpoint（同 `FileBackedCheckpointManager` 的 journal 全量加载）。compaction-aware 截断加载依赖 compaction 子系统的截断元数据交互，是独立 successor（同 plan 183 deferred）
- Successor Required: yes
- Successor Path: 依赖 compaction 子系统的独立 enhancement plan

## Non-Blocking Follow-ups

- 跨进程 / 多实例 checkpoint 接管锁（依赖 L4-8 Actor Runtime）
- Checkpoint retention / rotation / TTL policy（保留最近 N 个 checkpoint，定期 `DELETE WHERE SESSION_ID = ? AND SEQ < ?`）
- LLM-turn / compaction checkpoint triggers（roadmap A4，dispatch-loop 接线发射 `LLM_TURN`/`COMPACTION` 类型 checkpoint）
- firstKeptEntryId compaction-aware 截断加载（依赖 compaction 子系统）
- ORM migration tooling（Flyway / Liquibase，替代 raw DDL `CREATE TABLE IF NOT EXISTS`）
- 提升到通用模块（当非 Agent 消费者出现时）

## Closure

Status Note: DB-backed `ICheckpointManager` 已落地——`DBCheckpointManager` 将 `Checkpoint` 持久化到 `ai_agent_checkpoint` 表（raw JDBC + hybrid 列布局 + write-through cache + INSERT append-only）。跨实例存活已验证（销毁旧实例 + 建新实例共享 DB → getCheckpoint/getLatestCheckpoint 一致）。restoreSession（plan 183）使用 DB-backed getLatestCheckpoint 端到端工作。NoOpCheckpoint / ToolExecutionCheckpoint / FileBackedCheckpointManager 默认向后兼容。全部 1359 测试通过。独立 closure audit（task ses_13813483bffeH6UOTGAl4MbTz0）确认 sections 1-7 全部 PASS——real DB SQL、real cross-instance survival、real wiring、fail-fast semantics、doc sync。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: Independent closure-audit subagent (task ses_13813483bffeH6UOTGAl4MbTz0, general agent, fresh session)
- Audit Session: ses_13813483bffeH6UOTGAl4MbTz0
- Evidence:
  - **Exit Criterion — Phase 1**: PASS. `ai_agent_checkpoint` entity in `app.orm.xml:109-146` (11 columns, watermark PK). `AiAgentCheckpointTable.java` with TABLE_NAME/COL_*/DDL constants. `TestAiAgentCheckpointTable.java` 9 tests pass (DDL + ORM model + idempotency + null-session-id).
  - **Exit Criterion — Phase 2**: PASS. `DBCheckpointManager.java` implements `ICheckpointManager` with all 3 methods backed by real JDBC SQL. `saveCheckpoint` executes `INSERT INTO` (L110-122, L137). `getLatestCheckpoint` executes `SELECT ... WHERE SESSION_ID = ? ORDER BY SEQ DESC` on cache-miss (L191-204). `getCheckpoint` executes `SELECT ... WHERE WATERMARK = ?` on cache-miss (L231-243). `TestDBCheckpointManager.java` 16 tests pass (round-trip + field integrity + getLatest + append-only fail-fast + cross-session + anonymous + empty + backward compat).
  - **Exit Criterion — Phase 3**: PASS. `TestDBCheckpointManagerEngineWiring.java` 5 tests pass. Cross-instance survival (L119-164: mgrA→save→discard→mgrB→get returns consistent). Engine wiring (L177-212: execute→saveCheckpoint→SQL COUNT on table). restoreSession (L214-254: execute→crash simulate→new engine restoreSession from DB). Backward compat (L285-332: NoOp 0 rows + FileBacked 0 rows).
  - **Anti-Hollow Check**: PASS. saveCheckpoint → DB INSERT 调用链在运行时连通（经 DB 表，经直接 SQL 验证）。跨实例存活测试证明 checkpoint 确实从 DB 读取（两独立实例无共享内存仅共享 DB——`crossInstanceSurvivalViaSharedDb` 销毁 mgrA 后 mgrB 仍能检索）。无空方法体/静默跳过/no-op（`scan-hollow-implementations.mjs --severity high` → 0 findings，exit 0）。
  - **Wiring Verification**: PASS. `dbBackedEngineExecutionPersistsCheckpointToTable` 经 `engine.setCheckpointManager(ckptMgr)` 注入，`engine.execute` 触发 dispatch-loop `saveCheckpoint`，直接 SQL `SELECT COUNT(*) FROM ai_agent_checkpoint WHERE SESSION_ID=...` 验证行数 >= 1。
  - **checklist 完整性**: `check-plan-checklist.mjs --strict` → all 70 items checked, exit 0.
  - **Deferred 项分类检查**: 确认无 in-scope live defect 被降级——4 个 deferred 项均为 out-of-scope improvement 或 optimization candidate（跨进程接管锁/checkpoint retention/LLM-turn triggers/compaction-aware 加载），均附 Why Not Blocking Closure 理由。

Follow-up:

- 跨进程 / 多实例 checkpoint 接管锁（依赖 L4-8 Actor Runtime）
- Checkpoint retention / rotation / TTL policy（保留最近 N 个 checkpoint，定期 DELETE WHERE SESSION_ID = ? AND SEQ < ?）
- LLM-turn / compaction checkpoint triggers（roadmap A4，dispatch-loop 接线发射 LLM_TURN/COMPACTION 类型 checkpoint）
- firstKeptEntryId compaction-aware 截断加载（依赖 compaction 子系统）
- ORM migration tooling（Flyway / Liquibase，替代 raw DDL CREATE TABLE IF NOT EXISTS）

## Follow-up handled by 187-nop-ai-agent-llm-turn-compaction-checkpoint-triggers.md

The **LLM-turn / compaction checkpoint triggers** carry-over from this plan's `Deferred But Adjudicated` ("LLM-turn / compaction checkpoint triggers" — `Successor Required: yes, Successor Path: roadmap A4 独立 plan`) and `Non-Blocking Follow-ups` sections is handled by plan 187 (`ai-dev/plans/187-nop-ai-agent-llm-turn-compaction-checkpoint-triggers.md`). Plan 187 wires the two reserved-but-unemitted `CheckpointType` values into the ReAct dispatch loop: `LLM_TURN` (after each successful LLM response — assistant message added + token accounting complete) and `COMPACTION` (after context compaction succeeds — compacted messages replace the context). Both emission points follow the same intra-execution persistence pattern as the existing `TOOL_EXECUTION` checkpoint (plan 183: `replaceMessages` + `sessionStore.save`), maintaining the `checkpoint.messageCount ≤ session.messageCount` consistency invariant for crash/restart restore (plan 183). This completes the §5.4a trigger-point specification (all three trigger points now emitted: tool-execution ✅ plan 181, LLM-turn ✅ plan 187, compaction ✅ plan 187). Cross-process takeover lock, checkpoint retention/TTL, and firstKeptEntryId compaction-aware loading remain deferred successors.
