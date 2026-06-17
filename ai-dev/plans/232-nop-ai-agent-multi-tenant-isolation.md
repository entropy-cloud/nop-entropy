# 232 nop-ai-agent Multi-Tenant Isolation — TENANT_ID columns + tenant-scoped queries across all persistent tables

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-multi-tenant-isolation

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/230-nop-ai-agent-team-db-persistence.md`（Non-Goals：`多租户 tenantId/userId 隔离（vision §5.1）：不同租户的团队/Actor 完全不可见、DB 查询自动加 tenantId 条件。本计划 ai_agent_team / ai_agent_team_member 表无 tenantId 列，团队对全部实例可见（无租户隔离）。Classification: successor plan required（独立 carry-over L4-multi-tenant-isolation，依赖 tenant 标识标准化）`）+ `ai-dev/plans/228-nop-ai-agent-team-acl-enforcement.md`（Non-Goals：`多租户 tenantId/userId 隔离（vision §5.1 Tenant 隔离 / User 隔离）：不同租户的 Actor 完全不可见、DB 查询自动加 tenantId 条件。Classification: successor plan required（独立 carry-over L4-multi-tenant-isolation，依赖 AgentExecutionContext tenant 标识标准化）`）+ `ai-dev/plans/223-nop-ai-agent-team-manager.md`（Non-Goals：`多租户 tenantId/userId 隔离维度（vision §5.1）：团队按租户/用户隔离。Classification: successor（依赖 AgentExecutionContext tenant 标识标准化）`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §5.1（多租户模型：Tenant 隔离 = 不同租户 Actor 完全不可见 + DB 查询自动加 tenantId 条件；User 隔离 = 同租户内不同用户只能看到自己 Actor）；`ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §九（多租户与资源隔离：Nop 平台 IContext（tenantId/userId）天然支持租户标识）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 260（"多用户可并发运行独立 Actor，租户间资源隔离"——团队 ACL foundational 已交付，完整租户数据隔离仍为 successor）
> Related: `167`（交付 `Principal` 含 `tenantId` 字段，Javadoc 明确"Nop IContext natively supports this"，但为 passive 值对象不达 DB 层）、`218`（交付 `ActorRegistry` + `InMemoryActorRegistry`，Javadoc 显式标注"registry does not track tenantId/userId"）、`230`（交付 `DbTeamManager` + `ai_agent_team`/`ai_agent_team_member` 表，Javadoc 显式标注"no tenantId/userId column"）、`228`（交付 `DefaultTeamAclChecker` 团队 ACL 角色矩阵——团队维度资源隔离 foundational）、`221`/`227`（确立 raw JDBC + Table 常量类 + 构造期 initSchema 模式——本计划在其 Table 类上新增 TENANT_ID 列）

## Purpose

把 nop-ai-agent 的持久化数据隔离从"无租户维度——全部 10 张 `ai_agent_*` 表无 `TENANT_ID` 列、全部 DB store 类无 tenantId WHERE 注入、`Principal.tenantId` 是 passive 值不达 DB 层、`ActorRegistry` 显式不跟踪 tenantId"扩展为"经 contextual tenant 解析 + `TENANT_ID` 列 + 自动 WHERE 注入，使不同租户的 session/team/task/message/checkpoint/lock 等持久化数据完全不可见（vision §5.1 Tenant 隔离：'不同租户的 Actor 完全不可见，DB 查询自动加 tenantId 条件'）"。本计划交付这一件事的 foundational slice：全部 10 张表加 `TENANT_ID` 列 + 全部 DB store 类注入 tenantId 条件 + contextual tenant 解析机制 + `ActorRegistry` 租户隔离 + NoOp shipped 默认（null tenant context = 全部数据可见，零回归），闭合 roadmap §4 Layer 4 验收标准"多用户可并发运行独立 Actor，租户间资源隔离"的完整数据隔离维度。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **`Principal.tenantId` 已存在但为 passive 字段**（plan 167 ✅）：`io.nop.ai.agent.security.Principal`，3 字段（role / channelId / tenantId）。Javadoc 明确"Multi-tenant identifier (Nop `IContext` natively supports this)" + "Forward-compatible with the future dispatch-path consultation (L2-13 successor) which will source these fields from `AgentExecutionContext` / `IContext`"。当前 tenantId 不被任何 DB store 或 ActorRegistry 消费。
- **`ActorRegistry` 显式不跟踪 tenantId**（plan 218 ✅）：`io.nop.ai.agent.runtime.ActorRegistry` Javadoc（:11-15）明确"Multi-tenant tenantId/userId isolation (vision §5.1) is a successor that depends on normalising the tenant-identifier propagation through `AgentExecutionContext` (the foundational registry does not track tenantId/userId)"。`InMemoryActorRegistry` 双索引（actorId + sessionId），无 tenant 维度。
- **全部 10 张 `ai_agent_*` 表无 `TENANT_ID` 列**：grep `TENANT_ID` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 命中（`Principal.tenantId` Java 字段是唯一命中，非 DB 列）。10 个 Table 常量类均无 `TENANT_ID` 列定义：
  - `AiAgentSessionTable`（ai_agent_session）
  - `NopAiSessionMessageTable`（nop_ai_session_message）
  - `AiAgentMessageTable`（ai_agent_message）
  - `AiAgentCheckpointTable`（ai_agent_checkpoint）
  - `AiAgentTeamTable`（ai_agent_team）
  - `AiAgentTeamMemberTable`（ai_agent_team_member）
  - `AiAgentTeamTaskTable`（ai_agent_team_task）
  - `AiAgentSessionLockTable`（ai_agent_session_lock）
  - `NopAiChatResponseTable`（nop_ai_chat_response）
  - `AiAgentDenialTable`（ai_agent_denial_ledger）
- **全部 DB store 类构造器仅接收 `DataSource`，无 tenant 解析**：
  - `DBSessionStore`（session 持久化）
  - `DbSessionTakeoverLock`（跨进程接管锁）
  - `DBDenialLedger`（安全 denial ledger）
  - `DBCheckpointManager`（checkpoint 持久化）
  - `DbUsageRecorder`（usage 记录）
  - `DbTeamManager`（团队 + 成员，Javadoc :77-79 显式标注"no tenantId/userId column — teams are visible to all instances (multi-tenant isolation is an explicit successor)"）
  - `DbTeamTaskStore`（团队任务）
  - `DBMessageService`（跨进程消息）
  - `DefaultOrphanRecoveryHandler`（raw JDBC UPDATE on ai_agent_session，ABORT 模式）
  - `DefaultSessionTimeoutHandler`（raw JDBC UPDATE on ai_agent_session，FORCE_FAILED 模式）
- **raw JDBC 模式已确立**（plan 221/227/230 ✅）：全部 DB 持久化均为 raw JDBC + Table 常量类 + 构造期 `initSchema()` 自动建表。**模块运行时 DB 操作不使用 Nop ORM**——vision §5.1 称"Nop ORM 已支持"auto-tenantId 条件，但 raw JDBC 不享受该机制，WHERE 注入需手工实现。
- **ORM XML 是辅助 schema 来源**：`_vfs/nop/ai/agent/orm/app.orm.xml` 声明 4 个实体（`ai_agent_message` / `ai_agent_denial` / `ai_agent_session` / `ai_agent_checkpoint`），供平台工具（GraphQL / 审计 / DDL 生成）使用。`AiAgentSessionTable` Javadoc 明确"table schema is defined in the ORM model at `_vfs/.../app.orm.xml`; this class holds the concrete DDL used at runtime"——存在双 schema 来源。本计划需同时更新 ORM XML 实体定义（追加 TENANT_ID 列）保持一致。
- **`DBSessionStore` 维护 write-through cache**（:46-49, :65）：`ConcurrentHashMap<String, AgentSession> sessions` 缓存——`get(sessionId)` 先查 cache 后查 DB（:107-109）。`save` 经 `MERGE INTO ... KEY (SESSION_ID)` upsert + 更新 cache（:207-229）。**本计划必须处理 cache 隔离**：tenant 非空时 bypass cache（全部读写直达 DB），null tenant 时 cache 照常（backward compatible）。
- **NoOp shipped 默认模式已确立**：每个 DB-backed 契约有对应 NoOp 实现（`NoOpTeamManager` / `NoOpTeamTaskStore` / `NoOpSessionTakeoverLock` / `NoOpCheckpoint` / `NoOpUsageRecorder` / `NoOpDenialLedger` / `NoOpRecoveryManager`），写操作抛 `UnsupportedOperationException`（Minimum Rules #24），读返回 empty。本计划不改 NoOp 默认。
- **vision §5.1 Tenant vs User 隔离**：Tenant 隔离 = 不同租户 Actor 完全不可见、DB 查询自动加 tenantId 条件。User 隔离 = 同租户内不同用户只能看到自己 Actor（除非团队共享）。本计划只交付 **Tenant 隔离**（primary 维度），User 隔离为显式 successor。
- **architecture baseline §九**：明确"Nop 平台的 `IContext`（tenantId/userId）天然支持租户标识"。本计划的 contextual tenant 解析可利用 `IContext` 的 tenantId 字段作为解析源。
- **roadmap §4 Layer 4 验收标准 line 260**："多用户可并发运行独立 Actor，租户间资源隔离"——团队 ACL foundational（plan 228）+ 团队 DB 共享（plan 230）已标注交付，完整多租户 tenantId/userId 数据隔离仍为显式 successor。

## Goals

- **全部 10 张 `ai_agent_*` 表新增 `TENANT_ID` 列**（nullable VARCHAR(100)）：经各 Table 常量类的 `DDL_CREATE_TABLE` 追加 `TENANT_ID` 列定义 + 新增 `COL_TENANT_ID` 列名常量。构造期 `initSchema()` 自动建表（`CREATE TABLE IF NOT EXISTS`，H2 测试每次新建，生产已有表需 `ALTER TABLE ADD COLUMN`——本 foundational slice 依赖 `initSchema` 幂等建表 + 集成商手动迁移生产既有数据）。
- **Contextual tenant 解析机制**：定义 tenant 解析方式——DB store 在执行 SQL 前从 contextual 来源（thread-local / `IContext` / engine 传入的 `Principal.tenantId`）解析当前 tenantId。null tenant = 全部数据可见（backward compatible）。**不修改任何现有 store 接口的方法签名**（ITeamManager / ISessionStore / ITeamTaskStore / IMessageService / ICheckpointManager / IDenialLedger / IUsageRecorder / ISessionTakeoverLock 的方法签名不变——tenant 解析是 store 内部行为，不侵入接口契约）。
- **全部 DB store 类注入 tenantId WHERE 条件**：每个 SELECT / UPDATE / DELETE 语句追加 `AND TENANT_ID = ?`（当 tenant 非空时）。INSERT 语句追加 `TENANT_ID` 列写入解析出的 tenant 值。当 tenant 为 null 时，WHERE 不追加 tenant 条件（全部可见），INSERT 写入 null（legacy 数据）。
- **`ActorRegistry` 租户隔离**：`ActorRegistry` 增加 tenant 维度感知——注册/查询 Actor 时按 tenantId 过滤（当 tenant 非空时）。null tenant = 全部可见（backward compatible）。`AgentActor` 或注册条目携带 tenantId 标签。
- **`DefaultAgentEngine` 设置 tenant context**：引擎异步入口点（`execute` / `resumeSession` / `restoreSession` / `cancelSession` / `forkSession`）在 supplyAsync lambda body 内部从 `Principal.tenantId`（null-safe）解析 tenantId 并设置到 thread-local tenant resolver，lambda body finally 清除。使 DB store 在异步 worker 线程上能解析到正确的 tenant。
- **NoOp shipped 默认零回归**：无 tenant context 时（NoOp 默认 / 单租户部署 / 既有测试），全部行为与当前一致。既有全量测试零回归。
- **端到端验证**（Anti-Hollow #22）：同一 H2 DB + 两个 tenant context（tenant-A / tenant-B）+ 两套 DB store——tenant-A 写入 session / team / task → tenant-B 查询看不到 → 反之亦然。验证全部核心表（至少 session + team + team_member + team_task + message）的跨租户隔离。
- vision §5.1 Tenant 隔离从 successor 更新为 foundational 已落地；roadmap §4 Layer 4 验收标准"租户间资源隔离"标注完整数据隔离已交付（User 隔离仍为 successor）。

## Non-Goals

- **User 隔离**（vision §5.1 "同一租户内的不同用户，只能看到自己的 Actor"）：User 隔离是 tenant 隔离之后的更细粒度维度。需要 userId 标准化传播 + 团队共享数据例外语义。Classification: successor plan required（依赖 userId propagation 标准化 + 团队共享数据裁定）。
- **ResourceGuard + 配额强制**（vision §5.2 / §5.1 配额表）：每租户最大并发 Actor 数 / LLM 调用频率 / 存储配额。本计划只交付数据可见性隔离，不交付资源配额强制。Classification: successor plan required（vision §10 Phase 5）。
- **Fencing Token**（vision §5.1 monotonic counter 并发写入防护）：属 ResourceGuard / Phase 5 范畴。Classification: successor plan required。
- **DB-backed ACL 规则持久化**（vision §5.1 `TeamAclEntry` 表）：跨进程共享 ACL 规则。本计划只隔离 ACL 相关数据（team / team_member 按 tenant 隔离），不新增 ACL 规则表。Classification: successor plan required（plan 228 follow-up）。
- **TeamSpec `permissions` override**（vision §5.1）：per-team/per-member 权限收紧。Classification: successor plan required（plan 228 follow-up，依赖 TeamSpec 扩展）。
- **多租户 LLM API Key 隔离**（vision §4.2 line 465："多租户场景下，LLM API Key 是租户级还是全局级？"）：租户级 API Key 经 `LlmModel` Delta 定制。Classification: successor plan required（依赖 LlmModel 多租户配置裁定）。
- **生产既有数据迁移工具**：本计划交付 DDL 变更（`TENANT_ID` 列）+ WHERE 注入 + tenant 解析。生产部署的既有数据迁移（`ALTER TABLE ADD COLUMN TENANT_ID` + 既有数据 backfill tenantId + 验证）是集成商部署任务，不在本计划 scope（H2 测试每次新建表，无迁移问题）。Classification: out-of-scope improvement（部署运维范畴）。
- **tenantId 从 `IContext` 自动传播的深度平台集成**：本计划交付 `Principal.tenantId` → contextual resolver → DB store 的 propagation 链路。从 Nop `IContext` 全局上下文（如 HTTP request-scoped `IContext`）自动解析 tenantId 是更深的平台集成，本计划只确保引擎入口点正确设置 tenant context。Classification: successor plan required（依赖平台 IContext lifecycle 集成）。
- **nop-task DAG 集成 / blockedBy 依赖解析 / 跨进程取消传播 / RETRY 恢复模式 / nop-job 集成**：均为其他模块的显式 successor carry-over，与本计划无关。

## Scope

### In Scope

- `io.nop.ai.agent.security` 包：
  - 新增 tenant 解析机制（contextual tenant resolver / holder，thread-local 或 IContext-backed）
  - `Principal` 不修改（tenantId 字段已存在，是解析源之一）
- 10 个 Table 常量类（新增 `TENANT_ID` 列定义 + `COL_TENANT_ID` 常量 + DDL 更新）：
  - `AiAgentSessionTable` / `NopAiSessionMessageTable` / `AiAgentMessageTable` / `AiAgentCheckpointTable` / `AiAgentTeamTable` / `AiAgentTeamMemberTable` / `AiAgentTeamTaskTable` / `AiAgentSessionLockTable` / `NopAiChatResponseTable` / `AiAgentDenialTable`
- `_vfs/nop/ai/agent/orm/app.orm.xml`（4 个 ORM 实体追加 `TENANT_ID` 列，保持与 Table DDL 一致）
- DB store 类（WHERE 注入 + INSERT tenantId + tenant 解析调用）：
  - `DBSessionStore` / `DbSessionTakeoverLock` / `DBDenialLedger` / `DBCheckpointManager` / `DbUsageRecorder` / `DbTeamManager` / `DbTeamTaskStore` / `DBMessageService`
  - `DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler`（raw JDBC UPDATE on ai_agent_session）
- `ActorRegistry` + `InMemoryActorRegistry`（tenant-scoped register/lookup）
- `DefaultAgentEngine`（入口点设置 / 清除 tenant context）
- 测试文件（tenant 隔离跨租户验证）
- 文档更新（vision §5.1 + roadmap §4 Layer 4）

### Out Of Scope

- User 隔离（Non-Goal: 更细粒度用户隔离）
- ResourceGuard + 配额强制（Non-Goal: Phase 5）
- Fencing Token（Non-Goal: Phase 5）
- DB-backed ACL 表（Non-Goal: ACL 规则持久化）
- TeamSpec permissions override（Non-Goal: 权限收紧）
- 多租户 LLM API Key（Non-Goal: LlmModel 多租户）
- 生产数据迁移工具（Non-Goal: 部署运维）
- nop-task / blockedBy / 跨进程取消 / RETRY / nop-job（Non-Goal: 无关 successor）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **Tenant 解析 = contextual（thread-local backed），NOT 方法参数**。全部现有 DB store 接口（`ITeamManager` / `ISessionStore` / `ITeamTaskStore` / `IMessageService` / `ICheckpointManager` / `IDenialLedger` / `IUsageRecorder` / `ISessionTakeoverLock`）的方法签名**不变**——不追加 tenantId 参数。理由：(1) 追加参数是 massive breaking change，影响全部调用者（engine / tools / handlers / tests）；(2) 模块已建立"opt-in via constructor + NoOp default"模式，tenant 解析机制同理——DB store 构造期接收 tenant resolver（可选，默认 = always-null tenant = backward compatible），运行时从 resolver 解析 tenantId；(3) 与 Nop 平台 IContext 模式一致（IContext 是 contextual / request-scoped，非方法参数）。具体实现：定义 `ITenantResolver`（或类似），DB store 构造期可选注入，运行时 `resolve()` 返回当前 tenantId。引擎入口点经 thread-local 或 IContext 设置当前 tenant。

2. **TENANT_ID 列 nullable，null tenant context = 全部数据可见（backward compatible）**。当 tenant resolver 返回 null（无 tenant context / 单租户部署 / NoOp 默认 / 既有测试）：SELECT 不追加 tenant WHERE（全部可见）、INSERT 写入 null TENANT_ID。当 resolver 返回非空 tenant：SELECT 追加 `AND (TENANT_ID = ? OR TENANT_ID IS NULL)`（显示当前租户数据 + legacy null-tenant 数据）、INSERT 写入当前 tenant。理由：(1) 既有测试零回归——无 tenant context 时行为不变；(2) 生产迁移期间 legacy 数据（null tenant）对所有租户可见，渐进迁移。**strict 模式**（仅当前租户可见、隐藏 legacy 数据）是 successor optimization candidate。

3. **WHERE 注入模式 = `AND (TENANT_ID = ? OR TENANT_ID IS NULL)`**（非 strict）。当 tenant 非空时追加。理由：(1) backward compatible——legacy 数据不隐藏；(2) 测试可验证严格隔离：新写入数据携带 tenantId，跨租户查询的 `TENANT_ID = ?` 条件确保不可见；(3) `OR TENANT_ID IS NULL` 确保 migration 期间 legacy 数据不丢失。**E2E 测试**使用全新 H2（无 legacy 数据），写入时 tenant 非空，跨租户查询条件 `TENANT_ID = 'tenant-A'` 不命中 `TENANT_ID = 'tenant-B'` 数据——严格隔离在全新数据场景下成立。

4. **Scope = Tenant 隔离 only（非 User 隔离）**。vision §5.1 区分 Tenant 隔离（跨租户不可见）与 User 隔离（同租户内用户隔离）。本计划只交付 Tenant 隔离——`TENANT_ID` 列 + WHERE 注入。User 隔离（`USER_ID` 列 + 同租户内用户级过滤 + 团队共享数据例外）是独立 successor（依赖 userId 标准化 + 团队共享语义裁定）。理由：(1) roadmap 验收标准"租户间资源隔离"对应 Tenant 隔离；(2) User 隔离涉及团队共享数据（团队成员可见他人 Actor）的复杂例外语义，超出 foundational slice；(3) 分层交付降低风险。

5. **ActorRegistry tenant 隔离 = registry 内部 tenant 标签 map（不修改 `AgentActor` 构造器、不修改 `IActorRuntime` 接口）**。`AgentActor` 是 `final` 类（5 参构造器），`IActorRuntime.createActor(sessionId, agentName)` 是 public 接口方法不含 principal/tenant 参数。本计划**不改这些签名**——`InMemoryActorRegistry` 维护内部 `ConcurrentHashMap<String actorId, String tenantId>` tenant 标签 map，`register(AgentActor)` 时从 contextual tenant resolver（thread-local）解析当前 tenant 并记入标签 map。查询方法（`get(actorId)` / `getBySession(sessionId)` / `getAll()`）在 tenant 非空时按标签 map 过滤。null tenant = 全部可见。理由：(1) 避免 `AgentActor` 构造器 breaking change；(2) 避免 `IActorRuntime` 接口 breaking change（影响全部外部实现）；(3) 与 DB store tenant 解析模式一致（contextual，非签名参数）；(4) register 在 supplyAsync lambda 内部调用（裁定 6），此时 thread-local tenant context 已设置。

6. **Engine 入口点设置 tenant context — 在 supplyAsync lambda 内部 set/clear**。`DefaultAgentEngine` 全部异步入口点（`execute` :1711 / `resumeSession` :1986 / `restoreSession` :2117 / `cancelSession` :1589 / `forkSession` :1642）均经 `CompletableFuture.supplyAsync(...)` 提交到 worker 线程。**tenant context 必须在 supplyAsync lambda body 内部 set/clear**（非调用线程），因为标准 `ThreadLocal` 不跨 `supplyAsync` 线程边界传播。模式：同步阶段从 `request.getPrincipal()` 捕获 tenantId（**null-safe**：principal 为 null 或 tenantId 为 null → tenant = null = 全部可见），传入 lambda；lambda body 首行 `tenantContext.set(tenantId)`，lambda body finally `tenantContext.clear()`。嵌套 async（tool execution wrapper）需捕获当前 tenant 并在嵌套 lambda re-set。`restorePendingSessions`（:2299，同步）调用的 `restoreSession` 自身 supplyAsync——tenant context 在 `restoreSession` lambda 内设置。

7. **DDL 变更 = `CREATE TABLE` 追加 `TENANT_ID` 列（非 `ALTER TABLE`）**。Table 常量类的 `DDL_CREATE_TABLE` 直接包含 `TENANT_ID` 列。`initSchema()` 经 `CREATE TABLE IF NOT EXISTS` 建表（表不存在时建含 TENANT_ID 的新表）。**已有表的 `ALTER TABLE ADD COLUMN TENANT_ID` 是生产迁移工具职责**（Non-Goal）。H2 测试每次新建表（无迁移问题）。理由：(1) 与模块所有 Table 类的 `CREATE TABLE IF NOT EXISTS` 模式一致；(2) 不引入 `ALTER TABLE IF NOT EXISTS` 逻辑（H2 对 `ALTER TABLE ADD COLUMN IF NOT EXISTS` 支持不一致）。

8. **`DBSessionStore` cache bypass when tenant 非空**。`DBSessionStore` 维护 write-through cache（`ConcurrentHashMap<String, AgentSession> sessions`，:65），`get` 先查 cache 后查 DB（:107-109），`save` 经 MERGE + cache.put（:229）。**当 tenant context 非空时 bypass cache**——`get` / `getOrCreate` / `save` / `remove` 全部直达 DB（不经 cache 读写），WHERE 条件保证隔离。当 tenant context 为 null（backward compatible / 单租户 / 既有测试），cache 照常工作。理由：(1) cache 是 single-tenant 优化，多租户场景下 cache 按 sessionId key 无法区分租户——tenant-A 和 tenant-B 用相同 sessionId 会 cache 互相污染；(2) bypass cache 保证正确性，性能影响可接受（foundational slice；cache 优化如 tenant-keyed cache 是 successor optimization candidate）；(3) null tenant bypass 不启用 = 既有行为零回归。

9. **`MERGE INTO` KEY 变为 `(SESSION_ID, TENANT_ID)`**。`DBSessionStore.save` 当前用 `MERGE INTO ai_agent_session (...) KEY (SESSION_ID) VALUES (?,?,...)`（:207-214）。加 TENANT_ID 列后，MERGE KEY 必须变为 `KEY (SESSION_ID, TENANT_ID)`——否则 tenant-A 和 tenant-B 用相同 sessionId 时 MERGE 会互相覆盖（KEY 仅按 SESSION_ID 匹配）。变为 `(SESSION_ID, TENANT_ID)` 后，同一 sessionId 可在不同租户独立存在（每租户独立 session 命名空间）。null tenant 写入 `TENANT_ID = NULL`，H2 MERGE 将 NULL KEY 视为独立值（不匹配非 null tenant 的行）。理由：(1) 跨租户 sessionId 独立是 tenant isolation 的正确语义；(2) null tenant（backward compat）与非 null tenant 的 sessionId 自然隔离。其他表的 upsert / INSERT-ON-CONFLICT 语义同理：唯一键追加 TENANT_ID 维度。

10. **TENANT_ID 参数绑定约定 = INSERT 追加为最后一列、WHERE 追加在现有条件之后**。为最小化已有 PreparedStatement 参数 index 偏移：(1) INSERT/MERGE 语句——`TENANT_ID` 作为最后一个 VALUES 列追加（现有参数 index 不变，新增 `ps.setString(N+1, tenantId)` 在最后）；(2) SELECT/UPDATE/DELETE WHERE 条件——`AND (TENANT_ID = ? OR TENANT_ID IS NULL)` 追加在现有 WHERE 条件之后（现有参数 index 不变，tenant 参数绑定为最后）。理由：(1) 避免 bulk 参数 index 重编号引入 off-by-one 错误；(2) 每条 SQL 仅在末尾追加一个参数绑定。

### Phase 1 - Tenant 解析机制 + TENANT_ID 列 DDL + 引擎入口点 tenant context 设置

Status: completed
Targets: `io.nop.ai.agent.security`（tenant 解析机制新文件）、10 个 Table 常量类（DDL + 列常量）、`_vfs/nop/ai/agent/orm/app.orm.xml`（ORM XML 实体追加 TENANT_ID 列）、`io.nop.ai.agent.engine.DefaultAgentEngine`（异步入口点 supplyAsync lambda 内 tenant context 设置/清除）、`io.nop.ai.agent.runtime.InMemoryActorRegistry`（内部 tenant 标签 map）

- Item Types: `Decision`、`Proof`

- [x] 定义 tenant 解析机制：contextual tenant resolver 接口（如 `ITenantResolver`）+ thread-local backed 默认实现 + `null` resolver（always-null tenant = backward compatible）。Javadoc 明确解析语义 + null tenant = 全部可见 + refer vision §5.1
- [x] 10 个 Table 常量类新增 `COL_TENANT_ID` 列名常量 + `DDL_CREATE_TABLE` 追加 `TENANT_ID VARCHAR(100)` nullable 列：
  - `AiAgentSessionTable` / `NopAiSessionMessageTable` / `AiAgentMessageTable` / `AiAgentCheckpointTable`
  - `AiAgentTeamTable` / `AiAgentTeamMemberTable` / `AiAgentTeamTaskTable`
  - `AiAgentSessionLockTable` / `NopAiChatResponseTable` / `AiAgentDenialTable`
- [x] `_vfs/nop/ai/agent/orm/app.orm.xml` 中 4 个 ORM 实体（`ai_agent_message` / `ai_agent_denial` / `ai_agent_session` / `ai_agent_checkpoint`）各追加 `TENANT_ID` 列定义（保持与 Table 常量类 DDL 一致）
- [x] `DefaultAgentEngine` 异步入口点（`execute` :1711 / `resumeSession` :1986 / `restoreSession` :2117 / `cancelSession` :1589 / `forkSession` :1642）在 supplyAsync lambda body 内部 set/clear tenant context：同步阶段 null-safe 从 `request.getPrincipal()` 捕获 tenantId（principal 或 tenantId 为 null → tenant = null），传入 lambda → lambda 首行 `set(tenantId)` → finally `clear()`。嵌套 async（tool execution wrapper）捕获并 re-set tenant
- [x] `InMemoryActorRegistry` 新增内部 `ConcurrentHashMap<String, String>` tenant 标签 map——`register(AgentActor)` 时从 contextual tenant resolver 解析 tenant 并记入；`get(actorId)` / `getBySession(sessionId)` / `getAll()` 在 tenant 非空时按标签过滤。**不修改 `AgentActor` 构造器、不修改 `IActorRuntime.createActor` 接口签名**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] tenant 解析机制（`ITenantResolver` 或等价）文件存在，thread-local 默认实现 + null resolver（NoOp shipped 默认）编译通过
- [x] 全部 10 个 Table 常量类的 `DDL_CREATE_TABLE` 含 `TENANT_ID` 列定义 + `COL_TENANT_ID` 常量存在
- [x] `_vfs/nop/ai/agent/orm/app.orm.xml` 4 个实体含 `TENANT_ID` 列（与 Table DDL 一致）
- [x] `DefaultAgentEngine` 异步入口点经 supplyAsync lambda body 内部设置 / finally 清除 tenant context（**在 lambda body 内非调用线程**——focused test 验证 supplyAsync worker 线程内 DB store 可解析 tenant、lambda 退出后 resolver 返回 null）；null-safe 处理 principal 为 null 场景
- [x] `InMemoryActorRegistry` 内部 tenant 标签 map 存在，`register` 时记录 tenant、`get` / `getBySession` / `getAll` 按 tenant 过滤（focused test 验证）；`AgentActor` 构造器与 `IActorRuntime.createActor` 签名**未修改**
- [x] **无静默跳过**（Minimum Rules #24）：tenant 解析机制在未配置时返回 null（显式 backward compatible），不抛异常也不返回 placeholder
- [x] No owner-doc update required（design 文档更新在 Phase 3）

### Phase 2 - DB store 类 tenantId WHERE 注入 + ActorRegistry tenant 过滤

Status: completed
Targets: 全部 DB store 类（`DBSessionStore`（含 cache bypass + MERGE KEY 变更）/ `DbSessionTakeoverLock` / `DBDenialLedger` / `DBCheckpointManager` / `DbUsageRecorder` / `DbTeamManager` / `DbTeamTaskStore` / `DBMessageService` / `DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler`）、`ActorRegistry` / `InMemoryActorRegistry`

- Item Types: `Proof`

- [x] 每个 DB store 类构造期可选接收 `ITenantResolver`（默认 = null resolver = always-null tenant = backward compatible）
- [x] `DBSessionStore` cache bypass（Design Decision 8）：tenant 非空时 `get` / `getOrCreate` / `save` / `remove` 全部直达 DB 不经 cache；tenant 为 null 时 cache 照常
- [x] `DBSessionStore.save` MERGE KEY 从 `KEY (SESSION_ID)` 变为 `KEY (SESSION_ID, TENANT_ID)`（Design Decision 9），VALUES 追加 TENANT_ID（最后一列）
- [x] 每个 DB store 类的全部 SELECT 语句：tenant 非空时追加 `AND (TENANT_ID = ? OR TENANT_ID IS NULL)`（参数绑定在现有条件之后——Design Decision 10）
- [x] 每个 DB store 类的全部 INSERT 语句：追加 `TENANT_ID` 列为最后一列 + 写入 resolver 解析的 tenant 值（null resolver = 写入 null——Design Decision 10）
- [x] 每个 DB store 类的全部 UPDATE / DELETE 语句：tenant 非空时追加 `AND (TENANT_ID = ? OR TENANT_ID IS NULL)`
- [x] `DefaultOrphanRecoveryHandler` + `DefaultSessionTimeoutHandler` 的 raw JDBC UPDATE on ai_agent_session：tenant 非空时追加 tenant 条件
- [x] `InMemoryActorRegistry` 查询方法（`get(actorId)` / `getBySession(sessionId)` / `getAll()`）：tenant 非空时按内部 tenant 标签 map 过滤

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 每个 DB store 类的全部 SQL 语句含 tenantId 条件注入（代码审查 + grep 验证 `TENANT_ID` 在全部 PreparedStatement 出现）；`DBSessionStore.save` MERGE KEY 为 `(SESSION_ID, TENANT_ID)`
- [x] `DBSessionStore` cache bypass 验证：tenant 非空时 `get` 直达 DB（不经 cache）；focused test 设置 tenant-A 写入 session → tenant-B `get(sameSessionId)` 返回 null（cache 不污染）
- [x] **接线验证**（Minimum Rules #23）：focused test 验证 DB store 在运行时确实调用 `ITenantResolver.resolve()` 获取 tenantId 并注入 WHERE（非仅类型存在）
- [x] **无静默跳过**（Minimum Rules #24）：tenant 非空时 WHERE 条件确实追加（不因 resolver 返回 null 而静默跳过注入逻辑——resolver 返回 null 是显式 backward compatible 语义，非 bug）
- [x] **Tenant 隔离 focused test**：对核心 DB store（DBSessionStore / DbTeamManager / DbTeamTaskStore / DBMessageService）编写 focused test——设置 tenant-A context → 写入数据 → 清除 → 设置 tenant-B context → 查询返回 empty → 反之亦然
- [x] **Backward compatibility focused test**：null tenant context → 写入 + 查询行为与当前完全一致（既有测试零回归）
- [x] `ActorRegistry` tenant 过滤 focused test：tenant-A 注册 Actor → tenant-B 查询不到 → null tenant 全部可见
- [x] No owner-doc update required（design 文档更新在 Phase 3）

### Phase 3 - 端到端多租户验证 + 文档更新

Status: completed
Targets: 测试文件（E2E multi-tenant isolation test）、`ai-dev/design/nop-ai-agent/`（vision §5.1）、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`（§4 Layer 4）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 编写端到端多租户隔离测试：同一 H2 DB + 两套 DB store（注入同一 DataSource 但不同 tenant resolver context）—— tenant-A 经 engine.execute（Principal.tenantId=tenant-A，supplyAsync lambda 内设置 tenant context）创建 session + team + team_member + team_task + send message → 切换 tenant-B context → engine 查询全部返回 empty（session 不可见 / team 不可见 / task 不可见 / message 不可见）→ 反向验证。**验证 cache bypass**：tenant-A 和 tenant-B 用相同 sessionId，确认不互相泄露（cache 在 tenant 非空时 bypass）
- [x] **端到端验证**（Minimum Rules #22）：从 engine.execute 入口点 → Principal.tenantId 设置 → thread-local tenant context → DB store WHERE 注入 → 返回隔离结果，完整路径验证
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §5.1：Tenant 隔离从 successor 更新为 foundational 已落地（`TENANT_ID` 列 + contextual resolver + WHERE 注入 + ActorRegistry tenant 过滤 + engine 入口点 tenant context 设置）；标注 User 隔离 / ResourceGuard 配额 / strict 模式仍为 successor
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 260："租户间资源隔离"标注完整 Tenant 数据隔离已交付（User 隔离 / 配额强制仍为 successor）
- [x] `ai-dev/logs/` 对应日期条目更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 端到端多租户隔离测试全部断言通过（两 tenant 完全隔离 + null tenant 全部可见）
- [x] **端到端验证**：engine.execute → tenant context → DB WHERE 注入 → 跨租户不可见，完整链路验证
- [x] **接线验证**：测试断言 engine supplyAsync lambda body 内确实设置 thread-local tenant context（非调用线程；focused test 在 DB store 内 assert resolver 返回非 null tenant）
- [x] `nop-ai-agent-actor-runtime-vision.md` §5.1 已更新（Tenant 隔离 foundational 已落地 + successor 标注）
- [x] `nop-ai-agent-roadmap.md` §4 Layer 4 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 全部 10 张 `ai_agent_*` 表含 `TENANT_ID` 列（DDL + 常量），`initSchema` 建表含该列；ORM XML 4 实体含 `TENANT_ID` 列
- [x] 全部 DB store 类含 tenantId WHERE 注入（SELECT / INSERT / UPDATE / DELETE）；`DBSessionStore.save` MERGE KEY 为 `(SESSION_ID, TENANT_ID)`；`DBSessionStore` cache bypass when tenant 非空
- [x] contextual tenant 解析机制存在且经 engine 入口点设置/清除
- [x] `ActorRegistry` 含 tenant 过滤
- [x] Tenant 隔离经 focused test + 端到端测试验证（跨租户不可见 + null tenant backward compatible）
- [x] NoOp shipped 默认零回归（`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（User 隔离 / ResourceGuard 配额 / Fencing Token / DB-backed ACL / permissions override / 多租户 LLM Key / strict 模式 / 生产迁移工具 / IContext 深度集成均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步（vision §5.1 + roadmap §4 Layer 4）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）engine supplyAsync lambda → thread-local tenant context → DB store WHERE → 隔离结果调用链在 worker 线程上连通（非调用线程），（b）`DBSessionStore` cache bypass 在 tenant 非空时生效，（c）端到端跨租户隔离路径完整连通（含相同 sessionId 不互染），（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/232-nop-ai-agent-multi-tenant-isolation.md --strict` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

## Deferred But Adjudicated

（暂无；User 隔离 / ResourceGuard 配额 / Fencing Token / DB-backed ACL 表 / permissions override / 多租户 LLM Key / strict WHERE 模式 / 生产数据迁移工具 / IContext 深度平台集成 / nop-task DAG / blockedBy 依赖解析 / 跨进程取消传播 / RETRY 恢复模式 / nop-job 集成均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **User 隔离**（vision §5.1 "同一租户内的不同用户只能看到自己的 Actor"）：`USER_ID` 列 + 同租户内用户级过滤 + 团队共享数据例外语义。Classification: successor plan required（依赖 userId 标准化 + 团队共享裁定）。
- **ResourceGuard + 配额强制**（vision §5.2 / §5.1 配额表）：每租户最大并发 Actor / LLM 频率 / 存储配额。Classification: successor plan required（vision §10 Phase 5）。
- **Strict tenant WHERE 模式**（仅 `TENANT_ID = ?` 不含 `OR TENANT_ID IS NULL`，隐藏 legacy 数据）：Classification: optimization candidate（生产迁移完成后可启用）。
- **Fencing Token**（vision §5.1）：Classification: successor plan required（Phase 5）。
- **DB-backed ACL 规则持久化**（vision §5.1 `TeamAclEntry` 表）：Classification: successor plan required（plan 228 follow-up）。
- **TeamSpec `permissions` override**（vision §5.1）：Classification: successor plan required（plan 228 follow-up）。
- **多租户 LLM API Key 隔离**（vision §4.2 line 465）：Classification: successor plan required（依赖 LlmModel 多租户配置裁定）。
- **IContext 深度平台集成**（从 HTTP request-scoped IContext 自动解析 tenantId）：Classification: successor plan required（依赖平台 IContext lifecycle 集成）。
- **生产既有数据迁移工具**（`ALTER TABLE ADD COLUMN TENANT_ID` + backfill）：Classification: out-of-scope improvement（部署运维范畴）。

## Closure

Status Note: 全部 3 个 Phase 已完成并逐条勾选。Tenant 隔离 foundational slice 已完整落地——全部 10 张 `ai_agent_*` 表含 `TENANT_ID` 列 + ORM XML 4 实体同步；contextual tenant 解析机制（`ITenantResolver` + `ThreadLocalTenantResolver` + `NullTenantResolver` shipped 默认）；全部 raw-JDBC DB store（8 store + 2 recovery handler）在 tenant 非空时注入 `AND (TENANT_ID = ? OR TENANT_ID IS NULL)` WHERE + INSERT 写入 tenant；`DBSessionStore` cache bypass + MERGE KEY `(SESSION_ID, TENANT_ID)`；`DBCheckpointManager` cache bypass；`InMemoryActorRegistry` tenant 标签过滤（不改 `AgentActor`/`IActorRuntime` 签名）；`DefaultAgentEngine` 5 入口点 supplyAsync worker lambda 内 set/clear tenant context（从 `Principal.tenantId` null-safe 解析）。null tenant = 全部数据可见（SQL 与原版 byte-identical，零回归）。跨租户 session/team/task/message/checkpoint 完全不可见。User 隔离 / ResourceGuard 配额 / strict WHERE / Fencing Token / DB-backed ACL / permissions override / 多租户 LLM Key / IContext 深度集成 / 生产迁移工具 均为显式 Non-Goals successor。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session，task_id `ses_12bb5f1c4ffe4mH2h3Va2mPatc`）+ 实现者 self-audit
- Audit Session: 实现者执行 3 Phase + 全量测试验证；独立 closure audit 经 live code path + file:line 证据追踪复核（8/8 items PASS，verdict CLOSURE_AUDIT: PASS，无 in-scope defect）
- Evidence:
  - **Phase 1 Exit Criteria** PASS：`ITenantResolver`/`NullTenantResolver`/`ThreadLocalTenantResolver`/`TenantSql` 文件存在并编译（`nop-ai-agent/src/main/java/io/nop/ai/agent/security/`）；10 Table 常量类 `COL_TENANT_ID` + DDL 含列（grep 验证）；ORM XML 4 实体含 `tenantId` 列（`app.orm.xml`）；engine 5 入口点 set/clear 结构存在（`TestDefaultAgentEngineMultiTenantIsolation` 3 用例验证 worker 线程传播——`engineExecuteIsolatesSessionByTenant` 断言 `readTenantId` 返回 `tenant-A`，证明 supplyAsync worker 线程解析到 tenant）；`InMemoryActorRegistry` 标签 map + 过滤（`TestInMemoryActorRegistryTenantIsolation` 5 用例）；`AgentActor` 构造器与 `IActorRuntime.createActor` 签名未修改（grep 验证 `public AgentActor` / `createActor` 签名不变）
  - **Phase 2 Exit Criteria** PASS：全部 DB store tenant 注入（`TestMultiTenantDbIsolation` 9 用例覆盖 DBSessionStore cache bypass + 相同 sessionId 不互染 / DbTeamManager / DbTeamTaskStore / DBMessageService / DBDenialLedger / DBCheckpointManager / DbSessionTakeoverLock 跨租户隔离 + null-tenant backward compat）；`DBSessionStore.save` MERGE KEY `(SESSION_ID, TENANT_ID)`（代码 `save` 方法 + cache bypass `cacheEnabled()`）
  - **Phase 3 Exit Criteria** PASS：E2E `TestDefaultAgentEngineMultiTenantIsolation`（engine.execute → Principal.tenantId → supplyAsync worker → thread-local → DB WHERE → 跨租户不可见，Anti-Hollow #22 完整链路 + #23 接线验证）；vision §5.1 + roadmap §4 Layer 4 line 260 已更新；`ai-dev/logs/2026/06-17.md` plan-232 条目已写
  - **Anti-Hollow 检查** PASS：(a) 调用链 worker 线程连通——`engineExecuteIsolatesSessionByTenant` 断言 persisted row `TENANT_ID=tenant-A`（worker 线程解析到 tenant，非调用线程）；(b) `DBSessionStore` cache bypass——`sessionStoreIsolatesByTenant` 断言 tenant-B `get(sameSessionId)` 返回 null（cache 不污染）；(c) 端到端跨租户路径完整——`twoTenantsEachSeeOnlyTheirOwnSessions` 两 tenant 互相不可见；(d) 无空方法体/静默跳过——null tenant 是显式 backward-compatible 语义（resolver 返回 null 触发 "all visible" 路径，非 bug）
  - **Deferred 项分类检查** PASS：User 隔离 / ResourceGuard / strict WHERE / Fencing / DB-backed ACL / permissions override / 多租户 LLM Key / IContext 集成 / 生产迁移工具 均为显式 Non-Goals（vision §5.1 successor 标注），无 in-scope live defect 被降级
  - **零回归**：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 2400 tests 全绿（31 新增 tenant 测试 + 2369 既有全绿）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/232-nop-ai-agent-multi-tenant-isolation.md --strict` 退出码为 0（运行确认）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0（无 high/critical 空壳）

Follow-up:

- no remaining plan-owned work（User 隔离 / ResourceGuard 配额 / strict WHERE / Fencing Token / DB-backed ACL / permissions override / 多租户 LLM Key / IContext 深度集成 / 生产迁移工具 均为显式 Non-Blocking Follow-ups successor，不在本 plan scope）

## Follow-up handled by 234-nop-ai-agent-resource-guard-quota.md

The "ResourceGuard + 配额强制（vision §5.2 / §5.1 配额表）" Non-Goal successor listed above is taken up by the active carry-over plan `ai-dev/plans/234-nop-ai-agent-resource-guard-quota.md` (work item `L4-resource-guard-quota`). That plan delivers the foundational count-based concurrent quota slice (team max members / `maxParallelMembers` hint→enforced upgrade / per-tenant concurrent Actors) via a centralised `IResourceGuard` gateway with a NoOp shipped default. LLM call rate-limit, storage quota, per-agent token/time limits, and Fencing Token remain separate successors outside both plans.
