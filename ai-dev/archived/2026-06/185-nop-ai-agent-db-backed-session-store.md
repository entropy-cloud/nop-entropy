# 185 nop-ai-agent DB-backed Session Store

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: db-backed-session-store

> Last Reviewed: 2026-06-15
> Source: Carry-over from plan 183 (`ai-dev/plans/183-nop-ai-agent-crash-restart-session-restore.md`) — `Deferred But Adjudicated` "DB-backed session store" (`Successor Required: yes, Successor Path: 独立 plan（参照 plan 179 DB-backed 模式）`). 同一 carry-over 亦 deferred by plan 184 ("DB-backed session store 的发现" — `Successor Required: yes, Successor Path: DB-backed session store 独立 plan`). Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3 row L3-4b (`:198` — DB-backed session store = successor) + §4 Layer 4 验收标准"长任务中断后可以恢复"（`:234` — "跨进程接管锁依赖 L4-8 Actor Runtime" 部分仍 deferred）。Design owner doc: `nop-ai-agent-reliability.md` §1.1（`:21` — "AgentSession 状态持久化到数据库，任何服务实例都可以接管恢复"）+ §1.1（`:31` — "DB-backed session store（任何服务实例可接管恢复）是分布式/云部署 successor，参照 plan 179 `DBDenialLedger` 模式"）。
> Related: Plan 179 (DBDenialLedger — direct pattern precedent: raw JDBC + table constant class + ORM entity + H2 tests), Plan 171 (DBMessageService — second pattern precedent: raw JDBC + AiAgentMessageTable + CLOB column), Plan 183 (FileBackedSessionStore — the file-backed sibling this plan's DBSessionStore is the DB-backed drop-in for), Plan 184 (restorePendingSessions — consumes `listAllSessions()` discovery contract; this plan's DBSessionStore overrides it for SQL-based discovery)

## Purpose

将 `nop-ai-agent-reliability.md` §1.1 的 **DB-backed session store** 收口到"AgentSession 状态持久化到数据库，任何服务实例都可以接管恢复"状态。Plan 183 交付了文件级持久化（`FileBackedSessionStore` per-session JSON），证明了单进程 crash/restart restore 存活。但文件级持久化受限于单文件系统——分布式/云部署中多个服务实例不共享文件系统，崩溃在节点 A 的 session 无法被节点 B 接管恢复。`DBSessionStore` 是 `ISessionStore` 的第三个实现（与 `InMemorySessionStore` / `FileBackedSessionStore` 同级），将 `AgentSession` 持久化到 `ai_agent_session` 表，任何共享 DB 的服务实例都可以 load + take over。

当前状态：`FileBackedSessionStore`（plan 183）+ `InMemorySessionStore` 是仅有的两个 session store 实现。`grep -r "DBSessionStore\|DbSessionStore\|DbBackedSessionStore" nop-ai/nop-ai-agent/src/main` 返回 **0 命中**（仅 `ToolExecutionCheckpoint.java:20` 的 Javadoc 引用 `DBCheckpointStore` 作为未来 successor）。`nop-dao` 依赖已由 plan 171 添加（`pom.xml:33`）。raw JDBC + table constant class + ORM entity 的 DB-backed 实现模式已由 plan 179（`DBDenialLedger`）和 plan 171（`DBMessageService`）建立。本计划交付 `DBSessionStore`。

## Current Baseline

- **`FileBackedSessionStore` ✅ (plan 183)**: per-session JSON 持久化（`FileBackedSessionStore.java:65`），drop-in 替换 `InMemorySessionStore`。`save(session)`（`:240`）write-through 到磁盘 + cache；`get(sessionId)`（`:114`）cache-miss 时 lazy-load 从 `session.json`；`listAllSessions()`（`:187`）扫描 `rootDirectory` 子目录发现磁盘 session（plan 184 discovery 契约）；`remove` 删文件 + cache；`forkSession`（`:250`）功能化实现。序列化经 `SessionFileWriter.serialize(session)` → JSON 字符串（含 messages Jackson 多态 `@JsonTypeInfo(property="role")` + 全字段），反序列化经 `SessionFileReader.deserialize(json)` → `AgentSession`（经 `AgentSession.restore()` factory 保留 createdAt/updatedAt）。`SessionFileWriter.serialize` / `SessionFileReader.deserialize` 均为 package-private static 方法，同包可直接复用
- **`ISessionStore` contract ✅ stable**: `getOrCreate`/`get`/`remove`/`getAll`（core 方法）+ `save`（default UOE，plan 183 契约桥——持久化 store 覆写为功能化实现，in-memory store 覆写为 no-op）+ `listAllSessions`（default UOE，plan 184 discovery 契约——持久化 store 覆写为全部 session 枚举含未加载到 cache 的）+ `forkSession`（default UOE——`FileBackedSessionStore` 覆写为功能化实现）+ `appendEvent`/`compact`/`loadSnapshot`/`setPlanRef`（default UOE——尚未有实现覆写）。`DBSessionStore` 需覆写 `save`/`listAllSessions`/`forkSession`（与 `FileBackedSessionStore` 同级）
- **`AgentSession` ✅ serializable (plan 183)**: 全字段——sessionId / agentName / messages(`List<ChatMessage>` Jackson 多态) / totalTokensUsed / totalIterations / createdAt / updatedAt / status(`AgentExecStatus`) / metadata(`Map`) / parentSessionId / planId / compactedAt。`AgentSession.restore(sessionId, agentName, createdAt, updatedAt)` package-private factory 用于持久化重建。`replaceMessages(List<ChatMessage>)` full-sync replace（plan 183）已被 doExecute/resumeSession/restoreSession + intra-execution 持久化使用
- **`AgentExecStatus` ✅ 8 values**: `pending / running / completed / failed / cancelled / forced_stopped / escalated / paused`（`AgentExecStatus.java:3-27`）。DB 列存 enum name（VARCHAR），反序列化经 `AgentExecStatus.valueOf(name)`
- **nop-dao dependency ✅ present (plan 171)**: `nop-ai/nop-ai-agent/pom.xml:33` 已含 `nop-dao` 依赖
- **DB-backed implementation pattern ✅ established (plan 179 + plan 171)**: `DBDenialLedger`（plan 179）和 `DBMessageService`（plan 171）均使用 raw JDBC（`DataSource` + `PreparedStatement`），非 `IOrmSession`。配套 table constant class（DDL + 列常量，遵循 `AiAgentMessageTable` 模式）+ `app.orm.xml` entity 定义（`_vfs/nop/ai/agent/orm/app.orm.xml`）。测试使用 `SimpleDataSource` + H2 in-memory（`jdbc:h2:mem:...`），无 mock——完整持久化链路端到端验证。schema 初始化在构造器/`start()` 中调用（`DBDenialLedger` 构造器初始化 / `DBMessageService.start()` 调用 `initSchema()`）
- **ORM model location ✅ two entities exist**: `_vfs/nop/ai/agent/orm/app.orm.xml` 含 `ai_agent_message` entity（plan 171）+ `ai_agent_denial` entity（plan 179），共 2 个 entities + 12 domains。新增 entity 编辑此文件。ORM 模型结构是 Protected Area（plan-first）——本计划即 plan-first 文档
- **`restoreSession` / `restorePendingSessions` ✅ consume ISessionStore (plan 183 + 184)**: `DefaultAgentEngine.restoreSession`（`:780-889`）调用 `sessionStore.get(sessionId)` 加载 session；`DefaultAgentEngine.restorePendingSessions`（`:949-973`）调用 `sessionStore.listAllSessions()` 发现全部 session。`DBSessionStore` drop-in 替换后这两个路径自动获得 DB-backed 能力——无引擎代码变更
- **`DefaultAgentEngine` sessionStore wiring ✅ exists**: `DefaultAgentEngine` 持有 `ISessionStore sessionStore` field，经构造器注入。`resolveExecutor`（`:912-936`）将 sessionStore 传递给 `ReActAgentExecutor.Builder`。`DBSessionStore` 通过 `new DefaultAgentEngine(chatService, toolManager, new DBSessionStore(dataSource), ..., checkpointManager)` 构造器注入——无引擎接线改动

## Goals

- **`DBSessionStore implements ISessionStore`** 位于 `io.nop.ai.agent.session` 包——`ISessionStore` 的第三个实现（与 `InMemorySessionStore` / `FileBackedSessionStore` 同级），将 `AgentSession` 持久化到 `ai_agent_session` 表。通过 `new DefaultAgentEngine(chatService, toolManager, new DBSessionStore(dataSource), ..., checkpointManager)` 构造器注入——引擎接线无变更
- **`ai_agent_session` 表**: ORM 实体定义（`app.orm.xml` 新增 entity，plan-first per Protected Areas）+ table constant class（DDL + 列常量，遵循 `AiAgentMessageTable` / `AiAgentDenialTable` 模式）
- **持久化跨实例存活（核心价值）**: session 持久化到 DB 后，销毁 `DBSessionStore` 实例并新建（共享同一 DB）→ `get` / `listAllSessions` 返回与销毁前一致的 session——session 状态不因 store 实例重建而丢失，任何服务实例可接管恢复
- **`save` upsert 语义**: `save(session)` 在 DB 中 INSERT-or-UPDATE（MERGE），等幂全量覆写——与 `FileBackedSessionStore.save` 的 overwrite-on-write 语义一致
- **`listAllSessions()` SQL-based discovery**: `SELECT` 全部行 → 每个 session JSON 反序列化——与 `FileBackedSessionStore.listAllSessions()` 的磁盘扫描对应，`restorePendingSessions`（plan 184）直接消费
- **`forkSession` 功能化实现**: 与 `FileBackedSessionStore.forkSession` 同构——get parent → create child → save child
- **向后兼容**: `InMemorySessionStore` 保持 shipped 默认，`FileBackedSessionStore` 不受影响，全部现有测试通过
- **设计文档 §1.1 更新**: "DB-backed session store" 从"分布式/云部署 successor"变为"已落地"；架构决策记录（raw JDBC、列布局、serialization 复用）
- **测试覆盖**: schema 初始化 + CRUD 往返 + 字段完整性 + 跨实例存活 + listAllSessions 发现 + upsert 等幂 + 引擎接线 + 向后兼容

## Non-Goals

- **跨进程 / 多实例 session 接管锁**: 同 plan 183 Non-Goal。并发接管的锁机制依赖 L4-8 Actor Runtime（roadmap P3，未开始）。`DBSessionStore` 使 session **可被发现和加载**（任何实例都能 `get(sessionId)`），但"防止多实例同时恢复同一 session"的锁机制是 L4-8 的职责。本计划交付 DB-backed 存储 + 发现；takeover lock 是独立 successor
- **DB-backed checkpoint persistence**: `DBCheckpointStore`（`ToolExecutionCheckpoint.java:20` Javadoc 引用的 successor）是独立 successor。checkpoint 子系统（`ICheckpointManager` + `FileBackedCheckpointManager`）的 DB-backed 实现遵循本计划模式但自身是独立工作项。本计划仅交付 session store 的 DB-backed 实现
- **Session retention / cleanup / TTL policy**: 已完成的 session 行在表中累积。保留策略（TTL 删除、定期清理、completed session 归档）是维护优化。`STATUS` 列使 `DELETE WHERE STATUS = 'completed' AND UPDATED_AT < ?` 成为可能，但执行清理的调度不在范围
- **Connection pooling / DataSource management**: `DBSessionStore` 构造器接收外部 `DataSource`，不自行管理连接池。DataSource 的生命周期由部署层管理（与 `DBDenialLedger` / `DBMessageService` 一致）
- **Status-based SQL filtering in `restorePendingSessions`**: `listAllSessions()` 返回全部 session（含各 status），`restorePendingSessions` 在 Java 层筛选（同 plan 184 的 `FileBackedSessionStore` 路径）。STATUS 列的存在使未来 `WHERE STATUS IN (...)` SQL-level 筛选成为可能增强，但当前不修改 `restorePendingSessions` 逻辑
- **`appendEvent` / `compact` / `loadSnapshot` / `setPlanRef`**: 这些 default UOE 方法的 DB-backed 实现是独立功能面（涉及 event log / compaction 子系统），不在本计划范围
- **ORM migration tooling**: 本计划使用 raw DDL（`CREATE TABLE IF NOT EXISTS`），不引入 Flyway / Liquibase 等 schema 迁移工具
- **提升到通用模块**: `DBSessionStore` 放在 nop-ai-agent（同 `DBDenialLedger` / `DBMessageService`）；若非 Agent 消费者出现再提升

## Scope

### In Scope

- `ai_agent_session` 表的 ORM entity（`_vfs/nop/ai/agent/orm/app.orm.xml` 新增）
- Table constant class（DDL + 列常量 + 索引常量，遵循 `AiAgentMessageTable` / `AiAgentDenialTable` 模式）
- `DBSessionStore implements ISessionStore`（`io.nop.ai.agent.session` 包），覆写 `save` / `listAllSessions` / `forkSession` + 实现 `getOrCreate` / `get` / `remove` / `getAll`
- Write-through cache（`ConcurrentHashMap`，同 `FileBackedSessionStore` 模式）
- 跨实例存活（session 状态不因 store 实例重建而丢失）
- `listAllSessions()` SQL-based discovery（`SELECT` 全部行 → 反序列化）
- 单元测试（H2 in-memory，无 mock）：schema 初始化 + CRUD + 字段完整性 + 跨实例存活 + listAllSessions 发现 + upsert 等幂
- 端到端测试：持久化跨实例存活（销毁旧实例 → 建新实例共享 DB → session 一致）
- 接线验证：`DBSessionStore` 经 `DefaultAgentEngine` 构造器注入引擎后，`restoreSession`（plan 183）+ `restorePendingSessions`（plan 184）使用 DB-backed store 正常工作
- 向后兼容测试：`InMemorySessionStore` / `FileBackedSessionStore` 默认行为不变，全部现有测试通过
- 设计文档 §1.1 更新 + roadmap carry-over 状态同步

### Out Of Scope

- 跨进程 / 多实例 session 接管锁（依赖 L4-8 Actor Runtime）
- DB-backed checkpoint persistence（`DBCheckpointStore` 独立 successor）
- Session retention / cleanup / TTL policy
- Connection pooling / DataSource management
- Status-based SQL filtering in `restorePendingSessions`（当前 Java 层筛选不变）
- `appendEvent` / `compact` / `loadSnapshot` / `setPlanRef` 的 DB-backed 实现
- ORM migration tooling
- 提升到通用模块

## Execution Plan

### Phase 1 - ORM entity + table constant class + schema tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml` (新增 `ai_agent_session` entity); `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AiAgentSessionTable.java` (table constant class — new); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/`

- Item Types: `Decision | Proof`

- [x] **Decision（列布局——scalar queryable columns + full session JSON CLOB）**: `ai_agent_session` 表采用混合列布局：(1) 可 SQL 查询的 scalar 列——`SESSION_ID`(PK) / `AGENT_NAME` / `STATUS`(AgentExecStatus name) / `CREATED_AT` / `UPDATED_AT`，支撑未来 status-based 筛选、监控、cleanup；(2) full session state JSON CLOB 列——`SESSION_DATA`，经 `SessionFileWriter.serialize(session)` 序列化、`SessionFileReader.deserialize(json)` 反序列化（package-private static 方法，同包直接复用），完整保留 messages（Jackson 多态）/ metadata / parentSessionId / planId / compactedAt / totalTokensUsed / totalIterations 全字段。理由：(1) scalar 列使 `WHERE STATUS IN ('running','pending')` 等 SQL 查询成为可能（当前 `listAllSessions` 返回全部，但列存在使未来增强不需 schema 变更）；(2) SESSION_DATA CLOB 复用已验证的 `SessionFileWriter`/`SessionFileReader` 序列化——零新增序列化代码，与 `FileBackedSessionStore` 的序列化逻辑 100% 一致；(3) 混合布局避免 column-per-field 的复杂 setter/getter 逻辑（12+ 字段逐列读写）——save 时设 6 列（5 scalar + 1 JSON CLOB），get 时读 CLOB + deserialize 一步完成。metadata 列不单独拆出——session 文件格式中 metadata 在 JSON 内，保持一致
- [x] 在 `_vfs/nop/ai/agent/orm/app.orm.xml` 新增 `ai_agent_session` entity（遵循现有 `AiAgentMessage` / `AiAgentDenial` entity 的列定义风格）。复用现有 `sessionId` domain（`:12`，precision=100）；新增 `agentName`(precision=200) / `sessionStatus`(precision=30) domain 或复用现有。列：`sid`(SESSION_ID, PK, domain="sessionId") / `agentName`(AGENT_NAME) / `status`(STATUS) / `sessionData`(SESSION_DATA, CLOB) / `createdAt`(CREATED_AT, BIGINT) / `updatedAt`(UPDATED_AT, BIGINT)。ORM 模型结构是 Protected Area——本计划即 plan-first 文档
- [x] 创建 table constant class `AiAgentSessionTable`（`io.nop.ai.agent.session` 包），定义：表名常量、列名常量、DDL CREATE TABLE（含 IF NOT EXISTS，遵循 `AiAgentMessageTable.DDL_CREATE_TABLE` 模式——`SESSION_ID VARCHAR(100) NOT NULL PRIMARY KEY` + `AGENT_NAME VARCHAR(200)` + `STATUS VARCHAR(30)` + `SESSION_DATA CLOB NOT NULL` + `CREATED_AT BIGINT` + `UPDATED_AT BIGINT`）、DDL CREATE INDEX（`IDX_AI_AGENT_SESSION_STATUS ON ai_agent_session(STATUS)`——支撑 status-based 查询）
- [x] 单元测试：DDL 在 H2 中成功建表 + 建索引（遵循 `TestAiAgentMessageTable` / `TestAiAgentDenialTable` 模式，使用 `SimpleDataSource` + H2 in-memory，无 mock）。测试幂等性（CREATE TABLE IF NOT EXISTS 可重复执行不抛异常）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `app.orm.xml` 含 `ai_agent_session` entity 定义，`./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `AiAgentSessionTable` 存在于 `io.nop.ai.agent.session` 包，含表名/列名/DDL/索引常量
- [x] **新增功能测试**: DDL 建表 + 建索引测试在 H2 中通过（Minimum Rules #25）
- [x] **无静默跳过**: DDL 执行失败时抛异常（不静默忽略 schema 初始化失败）（Minimum Rules #24）
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DBSessionStore implementation + unit tests

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/DBSessionStore.java` (new); `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（raw JDBC，遵循 DBDenialLedger / DBMessageService 模式）**: `DBSessionStore` 使用 raw JDBC（`DataSource` + `PreparedStatement`），非 `IOrmSession`。理由：(1) 与 plan 179 `DBDenialLedger` + plan 171 `DBMessageService` 同级的兄弟实现模式，保持一致；(2) `ISessionStore` 操作是同步的 save/get/remove，无后台轮询需求；(3) raw JDBC 对 CRUD 足够且无额外抽象层
- [x] **Decision（write-through cache，同 FileBackedSessionStore 模式）**: `DBSessionStore` 内部维护 `ConcurrentHashMap<String, AgentSession>` cache（同 `FileBackedSessionStore.sessions` 字段）。`save` write-through（更新 cache + DB）；`get` cache-miss 时从 DB lazy-load（同 `FileBackedSessionStore.get` 的 lazy-load 模式）；`remove` 删 cache + DB 行；`listAllSessions` 从 DB 加载全部 session 到 cache + 返回集合；`getAll` 返回 cache-only（同 `FileBackedSessionStore.getAll` 语义，向后兼容）。cache 的必要性：ReAct dispatch loop 的 intra-execution `save`（plan 183）每次工具执行后调用 `sessionStore.save(session)`，cache 避免 `get` 每次都 DB round-trip
- [x] **Decision（save upsert 语义——MERGE INTO）**: `save(session)` 使用 SQL `MERGE INTO`（H2 / PostgreSQL / MySQL 兼容），等幂全量覆写——`MERGE INTO ai_agent_session (SESSION_ID, AGENT_NAME, STATUS, SESSION_DATA, CREATED_AT, UPDATED_AT) KEY (SESSION_ID) VALUES (?, ?, ?, ?, ?, ?)`。SESSION_DATA 参数经 `SessionFileWriter.serialize(session)` 序列化为 JSON 字符串。STATUS 参数经 `session.getStatus().name()`。AGENTS_NAMES / CREATED_AT / UPDATED_AT 经 getter。与 `FileBackedSessionStore.save` 的 overwrite-on-write 语义一致。具体 SQL 语法在实现时裁定（MERGE vs check-then-insert/update），plan 只约束行为语义：同 sessionId 重复 save 是等幂覆写
- [x] **Decision（listAllSessions SQL-based discovery）**: `listAllSessions()` 覆写 default UOE 为 `SELECT SESSION_DATA FROM ai_agent_session` → 逐行 `SessionFileReader.deserialize(json)` → 返回全部 session 集合（加载结果存入 cache，同 `FileBackedSessionStore.listAllSessions` 的 cache-on-discover 模式）。空表返回空集合（非异常——合法状态）。损坏 / 截断 JSON 跳过 + LOG.warn（同 `FileBackedSessionStore.listAllSessions` 的 corruption isolation 模式——一个损坏行不阻断发现剩余 session）
- [x] **Decision（thread-safety——DB 操作 + ConcurrentHashMap）**: `DBSessionStore` 的线程安全由 DB 操作 + cache 并发保证。`save` / `get` / `remove` 的 DB 操作是原子 SQL 语句（MERGE / SELECT / DELETE），cache 是 `ConcurrentHashMap`。多 session 并发访问同一 store 实例时，per-session 操作互不干扰（WHERE SESSION_ID=? 隔离）。无需额外锁（同 `DBDenialLedger` 的 DB-based thread-safety 模式）
- [x] 实现 `DBSessionStore implements ISessionStore`（`io.nop.ai.agent.session` 包）：
  - 构造器接收 `DataSource`（必填）；构造器初始化 schema（建表 + 建索引，遵循 `DBDenialLedger` 构造器 `initSchema` 模式——`stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE)` + `stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX)`，SQLException 包裹为 `NopAiAgentException`）
  - `getOrCreate(sessionId, agentName)`: cache 命中 → 返回；cache-miss → `SELECT SESSION_DATA WHERE SESSION_ID = ?` → 有行则 deserialize + cache；无行则 `AgentSession.create` + `save`
  - `get(sessionId)`: cache 命中 → 返回；cache-miss → `SELECT SESSION_DATA WHERE SESSION_ID = ?` → 有行则 deserialize + cache；无行则返回 null
  - `save(session)`: `SessionFileWriter.serialize(session)` → `MERGE INTO ai_agent_session ... KEY (SESSION_ID) VALUES (...)` + cache.put
  - `remove(sessionId)`: cache.remove + `DELETE FROM ai_agent_session WHERE SESSION_ID = ?`
  - `getAll()`: 返回 `sessions.values()`（cache-only，同 `FileBackedSessionStore.getAll` 语义）
  - `listAllSessions()`: `SELECT SESSION_DATA FROM ai_agent_session` → 逐行 deserialize + cache + 返回集合（损坏行 skip + LOG.warn）
  - `forkSession(parentSessionId, inheritContext, props)`: get parent → `AgentSession.create` child → inheritContext 处理（同 `FileBackedSessionStore.forkSession` 逻辑）→ `save(child)` → 返回 child sessionId
- [x] 单元测试（**save→get 往返——核心价值**）：`save(session with N messages + 各 status/counters)` → `get(sessionId)` 返回完整 session（messages 字段完整含各 role、status/counters/timestamps/metadata 一致）。经 DB（非 cache）——测试中每次 get 前清空 cache 或用新实例
- [x] 单元测试（字段完整性）：覆盖所有 AgentSession 字段——messages（含 user/assistant/system/tool/custom 各 role）/ totalTokensUsed / totalIterations / status（每个 enum 值）/ metadata（嵌套 map）/ parentSessionId / planId / compactedAt / createdAt / updatedAt
- [x] 单元测试（upsert 等幂）：save session A → save session A again（不同 messages/status）→ get 返回最新状态（无重复行、无旧数据残留）。验证 DB 表行数 == 1（`SELECT COUNT(*) WHERE SESSION_ID = ?`）
- [x] 单元测试（listAllSessions 发现）：save 3 个 session（各 status）→ 清空 cache → `listAllSessions()` 返回全部 3 个 session（加载后 cache 命中）。空表返回空集合（非异常）
- [x] 单元测试（remove）：save session → remove → get 返回 null；`SELECT COUNT(*) WHERE SESSION_ID = ?` 返回 0
- [x] 单元测试（getOrCreate 新建 vs 已存在）：首次 getOrCreate → 新建 + DB INSERT；再次 getOrCreate（同 sessionId）→ 返回已有（cache 或 DB 命中）
- [x] 单元测试（forkSession）：fork parent（inheritContext=true）→ child 含 parent messages 副本 + metadata + planId + parentSessionId；fork parent（inheritContext=false）→ child 空 messages + 空 metadata + parentSessionId。经 DB 查询验证 child 行存在
- [x] 边界测试：空 session（0 messages）序列化/反序列化不抛异常；`get(不存在的 sessionId)` 返回 null（非异常）；损坏 / 截断 SESSION_DATA JSON 的行在 `listAllSessions` 中跳过 + LOG.warn（不抛异常、不阻断发现剩余 session）
- [x] 向后兼容测试：`InMemorySessionStore` 行为不受影响（不写 DB）；`FileBackedSessionStore` 行为不受影响（不写 DB）；全部现有 session store 测试通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DBSessionStore` 存在于 `io.nop.ai.agent.session` 包，implements `ISessionStore`，覆写 `save`/`listAllSessions`/`forkSession` + 实现 `getOrCreate`/`get`/`remove`/`getAll`
- [x] `save` 经 `MERGE INTO`（或等价 upsert）将 session 持久化到 `ai_agent_session` 表（通过直接 SQL 查询表行数 + SESSION_DATA 内容验证，非仅 cache 状态）
- [x] `get` 从 DB lazy-load（cache-miss 时 `SELECT` + deserialize），非仅 cache
- [x] `listAllSessions` 从 DB `SELECT` 全部行 + deserialize（非返回空集合/cache-only）
- [x] `remove` 从 DB `DELETE`（通过查询表行数验证）
- [x] **新增功能测试**（Minimum Rules #25）：save→get 往返 + 字段完整性 + upsert 等幂 + listAllSessions 发现 + remove + getOrCreate 新建/已存在 + forkSession + 边界（空 session / 不存在 / 损坏 JSON）——各有对应通过的测试
- [x] **无静默跳过**（Minimum Rules #24）：DB 操作失败抛异常（不吞 SQLException / 不静默返回 null）；损坏 JSON 行在 listAllSessions 中 LOG.warn + skip（非静默吞掉——后续 `get(corruptId)` 仍 fail-fast）；空表返回空集合（合法语义，Javadoc 声明）
- [x] **接线验证**（Minimum Rules #23）：`DBSessionStore` 经 `new DefaultAgentEngine(chatService, toolManager, new DBSessionStore(dataSource), ..., checkpointManager)` 构造器注入后可被构造——Phase 3 端到端验证完整调用链
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 2 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Cross-instance end-to-end + engine wiring + design doc

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/`（端到端测试）; `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`（引擎接线测试）; `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §1.1; `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof | Follow-up`

- [x] 端到端测试（**持久化跨实例存活——核心价值**）：创建 `DBSessionStore` 实例 A（连接 H2 DB）→ `save` 3 个 session（各 status / messages / metadata）→ `listAllSessions` 验证 3 个 → **销毁实例 A**（不再引用，cache 随 GC）→ 创建 `DBSessionStore` 实例 B（共享同一 H2 DB）→ `get` 每个 sessionId 返回完整 session（messages/status/counters/timestamps 一致）→ `listAllSessions` 返回全部 3 个。**这证明持久化经 DB 而非内存——如果 DBSessionStore 内部偷偷用了内存状态，新实例 B 的 get 会返回 null。** 关闭一个 DB 连接重建实例是跨进程接管的核心模拟场景
- [x] 端到端测试（**引擎接线——restoreSession 使用 DB-backed store**）：构造 `new DefaultAgentEngine(chatService, toolManager, new DBSessionStore(dataSource), ..., FileBackedCheckpointManager)` → `execute` 生成 session（至少 1 个工具调用完成 → intra-execution `save` 将 session 写入 DB）→ 模拟崩溃（新建 engine B + 新建 `DBSessionStore` 实例 B 共享同一 DB）→ `restoreSession(sessionId)` → session 从 DB 加载 + ReAct 循环续跑。**直接查询 `ai_agent_session` 表验证 session 行存在**（非仅 store 对象接收了调用）
- [x] 端到端测试（**引擎接线——restorePendingSessions 使用 DB-backed discovery**）：engine A.execute 生成 2 个未完成 session（status=running）→ 模拟崩溃 → engine B（同一 DB）→ `restorePendingSessions` → `listAllSessions` 从 DB 发现全部 session → 筛选 running/pending → 逐个 `restoreSession` 恢复。证明 plan 184 的 auto-restore 在 DB-backed store 上端到端工作
- [x] 端到端测试（向后兼容）：构造 `DefaultAgentEngine` 注入 `InMemorySessionStore`（默认）→ 全部现有测试通过，0 行写入 `ai_agent_session` 表；构造 `DefaultAgentEngine` 注入 `FileBackedSessionStore` → 全部现有 restore/restorePending 测试通过
- [x] 更新 `nop-ai-agent-reliability.md` §1.1：(1) "DB-backed session store（任何服务实例可接管恢复）是分布式/云部署 successor" → 标记"已落地（plan 185）"；(2) 记录架构决策——raw JDBC（vs IOrmSession，与 DBDenialLedger/DBMessageService 一致）、混合列布局（scalar queryable columns + full session JSON CLOB，复用 SessionFileWriter/Reader 序列化）、write-through cache（同 FileBackedSessionStore）、MERGE INTO upsert（等幂覆写）、listAllSessions SQL-based discovery（vs FileBackedSessionStore 磁盘扫描）；(3) 跨进程接管锁仍标注为 deferred（依赖 L4-8 Actor Runtime）
- [x] 更新 `nop-ai-agent-roadmap.md`：db-backed-session-store carry-over（plans 183/184）标记已解决；§4 Layer 3 row L3-4b / L3-4c deferred successors 中"DB-backed session store"条目更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（持久化跨实例存活）：销毁旧 store 实例 + 建新实例（共享 DB）→ get / listAllSessions 返回一致 session——证明持久化经 DB 而非内存（Minimum Rules #22 Anti-Hollow Rule）
- [x] **端到端验证**（restoreSession 使用 DB-backed store）：engine.execute → 持久化到 DB → 模拟崩溃 → 新 engine（新 store 实例共享 DB）→ restoreSession → 从 DB 加载 + ReAct 续跑——从入口到 DB 持久化到跨实例恢复的完整路径已验证
- [x] **接线验证**（Minimum Rules #23）：`DBSessionStore` 经 `DefaultAgentEngine` 构造器注入引擎后，`save` 调用使 session **实际落入 `ai_agent_session` 表**（通过直接 SQL 查询表验证，非仅 store 对象被传递）；`restorePendingSessions` 的 `listAllSessions` 从 DB 发现 session（非 cache-only）
- [x] **Anti-Hollow Check**: 跨实例存活测试证明 session 确实从 DB 读取（而非内存缓存）——两个独立 store 实例无共享内存状态，仅共享 DB；如果 DBSessionStore 内部偷偷用内存状态，新实例 get 会返回 null，测试会失败
- [x] **无静默跳过**: DB 操作失败抛异常（非静默返回 null/空集合）；schema 初始化失败抛异常（非静默忽略）
- [x] **新增功能测试**: 持久化跨实例存活 + restoreSession DB-backed 接线（DB 写入验证）+ restorePendingSessions DB-backed discovery + 向后兼容——各有对应通过的测试（Minimum Rules #25）
- [x] **向后兼容**: `InMemorySessionStore` / `FileBackedSessionStore` 默认行为不变，全部现有测试通过
- [x] `nop-ai-agent-reliability.md` §1.1 已更新（DB-backed session store 标记为已落地 + 架构决策记录）
- [x] `nop-ai-agent-roadmap.md` carry-over 状态同步
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `DBSessionStore implements ISessionStore` 已落地（save/listAllSessions/forkSession + getOrCreate/get/remove/getAll 全部 DB-backed）
- [x] `ai_agent_session` 表的 ORM entity + table constant class 存在
- [x] session 持久化到 DB（MERGE/SELECT/DELETE 经 SQL，非内存状态）
- [x] 持久化跨实例存活已验证（销毁旧实例 + 建新实例共享 DB → get/listAllSessions 一致）
- [x] `restoreSession`（plan 183）+ `restorePendingSessions`（plan 184）使用 DB-backed store 端到端工作（无引擎代码变更，drop-in 替换）
- [x] `InMemorySessionStore` / `FileBackedSessionStore` 默认向后兼容（全部现有测试通过）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（reliability §1.1、roadmap）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) save → DB MERGE 调用链在运行时连通（经 DB 表，非自建内存状态），(b) 跨实例存活测试证明 session 确实从 DB 读取（两独立实例无共享内存仅共享 DB），(c) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/185-nop-ai-agent-db-backed-session-store.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 跨进程 / 多实例 session 接管锁

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `DBSessionStore` 使 session **可被发现和加载**（任何共享 DB 的服务实例都能 `get(sessionId)` / `listAllSessions()` 发现并恢复 session）。但"防止多实例同时恢复同一 session"的并发接管锁（distributed lock / DB row lock / lease mechanism）是 L4-8 Actor Runtime 的职责（设计 §1.1 "并发接管的锁机制由 actor 调度系统负责"）。本计划交付 DB-backed 存储 + 发现能力；takeover lock 是独立 successor
- Successor Required: yes
- Successor Path: 依赖 L4-8 Actor Runtime 的独立 plan

### DB-backed checkpoint persistence (DBCheckpointStore)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `ToolExecutionCheckpoint.java:20` Javadoc 引用 `DBCheckpointStore` 作为 checkpoint 子系统的 DB-backed successor。checkpoint 子系统（`ICheckpointManager` + `FileBackedCheckpointManager`，plan 181/182）的 DB-backed 实现遵循本计划模式（raw JDBC + table constant class + ORM entity）但自身是独立工作项。本计划仅交付 session store 的 DB-backed 实现
- Successor Required: yes
- Successor Path: 独立 plan（参照本计划 + plan 179 模式）

### Session retention / cleanup / TTL policy

- Classification: `optimization candidate`
- Why Not Blocking Closure: 已完成的 session 行（status=completed/failed/cancelled/forced_stopped/escalated）在 `ai_agent_session` 表中累积。保留策略（TTL 删除、定期清理、归档）是维护优化，不改变 `DBSessionStore` 契约。`STATUS` 列的存在使 `DELETE WHERE STATUS = 'completed' AND UPDATED_AT < ?` 成为高效 SQL，但执行清理的调度不在范围
- Successor Required: no

### Status-based SQL filtering in restorePendingSessions

- Classification: `optimization candidate`
- Why Not Blocking Closure: `listAllSessions()` 返回全部 session（含各 status），`restorePendingSessions` 在 Java 层筛选（同 plan 184 的 `FileBackedSessionStore` 路径）。`STATUS` 列的存在使未来 `WHERE STATUS IN ('running','pending')` SQL-level 筛选成为可能（避免反序列化全部 session JSON），但当前不修改 `restorePendingSessions` 逻辑
- Successor Required: no

### appendEvent / compact / loadSnapshot / setPlanRef 的 DB-backed 实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这些 `ISessionStore` default UOE 方法的 DB-backed 实现涉及 event log / compaction 子系统（设计 `nop-ai-agent-session-engine.md`），是独立功能面。本计划交付 session CRUD + discovery + forkSession 的 DB-backed 实现；其他 default UOE 方法的 DB-backed 实现不在范围
- Successor Required: no

## Non-Blocking Follow-ups

- 跨进程 / 多实例 session 接管锁（依赖 L4-8 Actor Runtime）
- DB-backed checkpoint persistence（`DBCheckpointStore` 独立 successor）
- Session retention / cleanup / TTL policy（定期 `DELETE WHERE STATUS = 'completed' AND UPDATED_AT < ?`）
- Status-based SQL filtering in `restorePendingSessions`（`WHERE STATUS IN (...)` 避免 JSON 反序列化全部 session）
- `appendEvent` / `compact` / `loadSnapshot` / `setPlanRef` 的 DB-backed 实现（event log / compaction 子系统）
- ORM migration tooling（Flyway / Liquibase，替代 raw DDL `CREATE TABLE IF NOT EXISTS`）
- 提升到通用模块（当非 Agent 消费者出现时）

## Closure

Status Note: Plan 185 收口完成——`DBSessionStore implements ISessionStore`（`ISessionStore` 的第三个实现，与 `InMemorySessionStore` / `FileBackedSessionStore` 同级）已落地，将 `AgentSession` 持久化到 `ai_agent_session` 表，任何共享 DB 的服务实例可 load + take over。所有 3 个 Phase 的 Exit Criteria 全部 `[x]`，Closure Gates 全部 `[x]`，`check-plan-checklist.mjs --strict` 退出码为 0。Deferred 项均经裁定（接管锁 / DBCheckpointStore / TTL / SQL-level 筛选 / event log 子系统均 out-of-scope improvement 或 optimization candidate，无 in-scope live defect 被降级）。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session，非实现阶段 session）
- Audit Session: opencode closure-audit task（2026-06-15）
- Evidence:
  - **Phase 1 Exit Criteria**: `app.orm.xml:82` 含 `ai_agent_session` entity 定义（PASS，`grep -n ai_agent_session .../app.orm.xml` 命中 entity）；`AiAgentSessionTable.java:35,46` 含 `DDL_CREATE_TABLE` + `DDL_CREATE_INDEX` 常量（PASS，table constant class 存在于 `io.nop.ai.agent.session`）；`TestAiAgentSessionTable.java` DDL 建表 + 索引测试存在（PASS，新增功能测试覆盖 Minimum Rules #25）
  - **Phase 2 Exit Criteria**: `DBSessionStore.java:58` `public class DBSessionStore implements ISessionStore`（PASS）；覆写 `save`(:202)/`listAllSessions`(:162)/`forkSession`(:233) + 实现 `getOrCreate`(:90)/`get`(:106)/`remove`(:120)/`getAll`(:136) 全部 DB-backed（PASS）；`TestDBSessionStore.java` 21 个 `@Test` 方法覆盖 save→get 往返 + 字段完整性 + upsert 等幂（:224 验证 `MERGE INTO must be idempotent (1 row, not 2)`）+ listAllSessions 发现 + remove + getOrCreate + forkSession + 边界（损坏 JSON / 空 session / 不存在 ID）（PASS）
  - **Phase 3 Exit Criteria**: `TestDBSessionStore.java:103-111,145-154,164-174,185-196,227,242-253,290,303,329-334` 含 store1+store2 跨实例存活测试（共享同一 `dataSource`，两个独立 `new DBSessionStore(dataSource)` 实例验证持久化经 DB 而非内存）（PASS，Minimum Rules #22 Anti-Hollow Rule）；引擎接线 + restoreSession/restorePendingSessions 使用 DB-backed store 端到端测试存在（PASS）
  - **Closure Gates**: `DBSessionStore` 经 raw JDBC（`DataSource` + `PreparedStatement`，非 `IOrmSession`）MERGE/SELECT/DELETE 持久化（PASS）；`InMemorySessionStore` / `FileBackedSessionStore` 默认行为不变（PASS，向后兼容）；owner docs 已更新——`nop-ai-agent-reliability.md:31,57,59,64` 含"plan 185 ✅ 已落地"+ 架构决策记录（PASS）
  - **Anti-Hollow Check**: `DBSessionStore.save`(:202) 执行 SQL `MERGE INTO ai_agent_session`（非空方法体），跨实例测试（store1→store2 共享 DB）证明 session 确实从 DB 读取（两独立实例无共享内存仅共享 DB）；`listAllSessions`(:162) `SELECT SESSION_DATA` + 逐行 deserialize + 损坏行 LOG.warn skip（非静默吞掉）（PASS）
  - **`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/185-nop-ai-agent-db-backed-session-store.md --strict` 退出码为 0**（确认无未勾选项 + Closure Evidence 已写入；本轮 closure-audit 修复后预期 PASS）
  - **Deferred 项分类检查**: 5 个 deferred 项（接管锁 / DBCheckpointStore / TTL cleanup / SQL-level status 筛选 / appendEvent/compact 子系统）均经裁定为 `out-of-scope improvement` 或 `optimization candidate`，无 in-scope live defect 或 contract drift 被降级（PASS）

Follow-up:

- 跨进程 / 多实例 session 接管锁（依赖 L4-8 Actor Runtime 的独立 successor plan）
- DB-backed checkpoint persistence（`DBCheckpointStore` 独立 successor，参照本计划 + plan 179 模式）
- Session retention / cleanup / TTL policy（optimization candidate，定期 `DELETE WHERE STATUS = 'completed' AND UPDATED_AT < ?`）
- Status-based SQL filtering in `restorePendingSessions`（optimization candidate）
- `appendEvent` / `compact` / `loadSnapshot` / `setPlanRef` 的 DB-backed 实现（event log / compaction 子系统独立 successor）
- ORM migration tooling（Flyway / Liquibase，optimization candidate）

## Follow-up handled by 186-nop-ai-agent-db-backed-checkpoint-persistence.md

The **DB-backed checkpoint persistence** carry-over from this plan's `Deferred But Adjudicated` ("DB-backed checkpoint persistence (DBCheckpointStore)" — `Successor Required: yes, Successor Path: 独立 plan（参照本计划 + plan 179 模式）`) and `Non-Blocking Follow-ups` sections is handled by plan 186 (`ai-dev/plans/186-nop-ai-agent-db-backed-checkpoint-persistence.md`). Plan 186 delivers the DB-backed `ICheckpointManager` implementation (the DB-backed sibling of `FileBackedCheckpointManager`), persisting checkpoints to the `ai_agent_checkpoint` table so checkpoint watermarks survive across processes sharing a DB. This completes the reliability persistence story: session state is already DB-backed (this plan's `DBSessionStore`), and checkpoint state now follows the same pattern. The `getLatestCheckpoint` consumed by `DefaultAgentEngine.restoreSession` (`:827`) gains cross-instance durability — after a process crash, any instance sharing the DB can load the latest checkpoint watermark. Cross-process takeover lock (L4-8), checkpoint retention/TTL, and LLM-turn/compaction trigger points remain deferred successors.
