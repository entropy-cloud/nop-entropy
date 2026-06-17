# 230 nop-ai-agent DB-backed 团队持久化 — DbTeamManager + ai_agent_team / ai_agent_team_member（raw JDBC 跨进程共享）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-team-db-persistence

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/227-nop-ai-agent-team-task-update.md`（Non-Blocking Follow-ups 第四条：`DB-backed 团队（team）持久化（plan 223 follow-up）：@BizModel("AiTeam") + ai_agent_team / ai_agent_team_member 表。Classification: successor plan required`）+ `ai-dev/plans/228-nop-ai-agent-team-acl-enforcement.md`（Non-Goals：`DB-backed 团队（team）持久化 ... 均为 plan 223/225 显式 successor，本计划不触及`）+ `ai-dev/plans/223-nop-ai-agent-team-manager.md`（Non-Goals：`DB-backed 团队持久化（vision §4.2 @BizModel("AiTeam") + ORM 实体）：团队状态写 DB 表、跨进程共享、事务保护。是独立 successor plan required。本计划仅交付 in-memory 实现`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §4.2（TeamManager 平台层组件，平台映射为 `@BizModel("AiTeam")` + ORM 实体持久化）+ §8（Team Mode 完整设计）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 257（"多用户可并发运行独立 Actor，租户间资源隔离"——团队状态跨进程共享是该验收的 foundational 基础设施层）
> Related: `223`（交付 `ITeamManager` 契约 + `InMemoryTeamManager` + `NoOpTeamManager` + `Team`/`TeamMember`/`TeamSpec`/`TeamMemberSpec`/`MemberRole`/`TeamStatus` 数据对象 + `DefaultAgentEngine.setTeamManager` 全链路接线，本计划在其契约上新增 DB-backed 实现）、`221`（交付 `DbSessionTakeoverLock` + `AiAgentSessionLockTable`——raw JDBC + Table 常量类 + 构造期 `initSchema` 自动建表模式，本计划 DB 持久化层直接遵循此模式）、`227`（交付 `DbTeamTaskStore` + `AiAgentTeamTaskTable`——本计划 DB 持久化层直接镜像其 raw JDBC + 条件 UPDATE CAS + 跨实例 H2 测试模式）、`228`（交付 `DefaultTeamAclChecker` 持有 `ITeamManager` 引用，本计划 DbTeamManager 作为 drop-in 实现对其透明）

## Purpose

把 nop-ai-agent 的团队注册表从"仅 `InMemoryTeamManager`——团队与成员状态驻留 JVM 内存，进程重启即丢失、跨进程实例不可见"扩展为"可选启用 `DbTeamManager`——团队与成员状态经 raw JDBC 写入 `ai_agent_team` + `ai_agent_team_member` 共享表，跨进程实例经同一 DB 可见、进程重启后可重建"。本计划只负责这一件事：交付 `ITeamManager` 契约的 DB-backed 功能实现（`DbTeamManager`）+ 两张共享表（DDL + 常量类 + 构造期自动建表），闭合 plan 223/227/228 显式切出的"团队 DB 持久化"successor gap，使团队子系统具备生产可用所需的跨进程共享与重启存活能力。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **`ITeamManager` 契约已存在且稳定**（plan 223 ✅）：9 个方法——`createTeam(TeamSpec)` / `getTeam(teamId)` / `getTeamBySession(sessionId)` / `disbandTeam(teamId)` / `getActiveTeams()` / `addMember(teamId, TeamMemberSpec)` / `removeMember(teamId, memberName)` / `bindMemberSession(teamId, memberName, sessionId, actorId)` / `getMember(teamId, memberName)`。`io.nop.ai.agent.team.ITeamManager`。**本计划不修改该契约**——`DbTeamManager` 是 drop-in 实现。
- **`InMemoryTeamManager` 是唯一功能性实现**（plan 223 ✅）：`ConcurrentHashMap` 双索引（`teamId → Team` + `sessionId → teamId` 反查），完整生命周期 + 状态机（CREATED→ACTIVE→DISBANDED），首次绑定激活经 `teams.compute` exactly-once。`io.nop.ai.agent.team.InMemoryTeamManager`。**返回的是 live 可变 Team 对象**——其 member map 由 manager 直接 in-place mutate（`addMember` 经 `members.putIfAbsent`，`bindMemberSession` 经 `members.compute`）。
- **`NoOpTeamManager` shipped 默认**（plan 223 ✅）：写操作抛 `UnsupportedOperationException`（Minimum Rules #24），读返回 empty。零回归。本计划不改 shipped 默认。
- **`Team` 数据对象是可变运行时状态持有者**（plan 223 ✅）：不可变身份（`teamId` UUID / `spec` TeamSpec / `createdAt` millis）+ 可变状态（`members` Map<String,TeamMember> / `status` TeamStatus / `disbandedAt` millis）。`getMembers()` 返回 manager 拥有的 live map（契约要求调用者只读）。`io.nop.ai.agent.team.Team`。
- **`TeamMember` 数据对象是可变绑定持有者**（plan 223 ✅）：不可变身份（`memberName` / `role` MemberRole / `joinedAt` millis）+ 可变绑定（`sessionId` / `actorId`，null 直到 `bind()`）。`io.nop.ai.agent.team.TeamMember`。
- **`TeamSpec` 不可变配置**（plan 223 ✅）：5 字段——`teamName` / `description`(nullable) / `leadAgentName` / `memberSpecs`(List<TeamMemberSpec>) / `maxParallelMembers`(int, <=0 = unlimited)。全参构造防御性拷贝。`io.nop.ai.agent.team.TeamSpec`。
- **`TeamMemberSpec` 不可变配置**（plan 223 ✅）：3 字段——`memberName` / `agentModel` / `role`。`io.nop.ai.agent.team.TeamMemberSpec`。
- **`TeamStatus` 枚举 3 态**（plan 223 ✅）：`CREATED` / `ACTIVE` / `DISBANDED`（终态）。`io.nop.ai.agent.team.TeamStatus`。
- **`MemberRole` 枚举 2 态**（plan 223 ✅）：`LEAD` / `MEMBER`。`io.nop.ai.agent.team.MemberRole`。
- **`DefaultAgentEngine.setTeamManager(ITeamManager)` 全链路接线已存在**（plan 223 ✅）：`teamManager` 字段（`DefaultAgentEngine.java:290`，默认 `NoOpTeamManager.noOp()`）+ `setTeamManager`（`:1316-1318`，null-safe 回退 NoOp）+ `resolveExecutor` 经 `ReActAgentExecutor.Builder` 传递。`ReActAgentExecutor` 全链路（字段 `:305` + 构造 `:350` + Builder `:464`/`:882`）+ `AgentToolExecuteContext`（字段 `:52` + getter `:413`）。**本计划无需任何引擎/context 接线变更**——`DbTeamManager implements ITeamManager` 经 `setTeamManager(new DbTeamManager(dataSource))` drop-in 注入（与 `setTeamTaskStore(new DbTeamTaskStore(dataSource))` 同一 opt-in 模式）。
- **raw JDBC + Table 常量类 + 构造期 `initSchema` 模式已确立**（plan 221/227 ✅）：`AiAgentSessionLockTable` + `DbSessionTakeoverLock`（plan 221）、`AiAgentTeamTaskTable` + `DbTeamTaskStore`（plan 227）。模块内所有 DB 持久化均遵循 raw JDBC：`DBSessionStore` / `DBMessageService` / `DBDenialLedger` / `DBCheckpointManager` / `DBUsageRecorder` / `DbSessionTakeoverLock` / `DbTeamTaskStore` / `DefaultOrphanRecoveryHandler`。无 ORM/DAO/codegen 管线。
- **条件 UPDATE CAS 模式已确立**（plan 221/227 ✅）：`DbSessionTakeoverLock.tryAcquire` 与 `DbTeamTaskStore.claimTask/completeTask/abandonTask` 的 conditional `UPDATE ... WHERE ...` + affected-row-count（`executeUpdate()==1`）判定 CAS 成败。
- **DB 测试模式已确立**（plan 221/227 ✅）：`TestDbTeamTaskStore` / `TestDbSessionTakeoverLock` 经 `io.nop.dao.jdbc.datasource.SimpleDataSource` + H2 in-memory（`jdbc:h2:mem:test-...;DB_CLOSE_DELAY=-1`），`CoreInitialization.initializeTo(...)` 初始化。跨 store 实例共享同一 H2 验证跨进程语义。
- **零 DbTeamManager / 团队 DB 持久化代码**：grep `DbTeamManager|AiAgentTeamTable|AiAgentTeamMemberTable|ai_agent_team[^_]` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 命中（`ai_agent_team_task` 任务表 + `AiAgentTeamTaskTable` + `DbTeamTaskStore` 是 plan 227 交付的任务持久化，与团队/成员持久化是不同表/不同类）。`app.orm.xml` 中无 `AiTeam` / `AiTeamMember` ORM 实体。
- **vision §4.2 TeamManager 平台映射**：标注 `@BizModel("AiTeam")` + ORM 实体持久化，当前实现状态为"🟡 部分落地"（InMemoryTeamManager + 团队工具 + ACL foundational 已交付，DB 持久化为 successor）。
- **roadmap §4 Layer 4 验收标准 line 257**："多用户可并发运行独立 Actor，租户间资源隔离"标注团队 ACL foundational 已交付，完整多租户 tenantId/userId 隔离仍为 successor。团队状态跨进程共享是该验收的 foundational 基础设施。

## Goals

- **`DbTeamManager` 功能实现**（新文件，implements `ITeamManager`）：经 raw JDBC 将全部 9 个 `ITeamManager` 方法持久化到 `ai_agent_team` + `ai_agent_team_member` 两张共享表。构造期接收 `DataSource` + 调用 `initSchema()` 自动建两张表（`CREATE TABLE IF NOT EXISTS`，镜像 `DbTeamTaskStore.initSchema` / `DbSessionTakeoverLock.initSchema`）。集成商经 `new DbTeamManager(dataSource)` 构造并经 `setTeamManager` 注入——**无需任何引擎/context 代码变更**。
- **`AiAgentTeamTable`**（新文件）：`ai_agent_team` 表 DDL + 列名常量。每行一个团队，记录团队不可变配置（teamName / description / leadAgentName / maxParallelMembers）+ 可变状态（status / disbandedAt）+ createdAt。PK = TEAM_ID。
- **`AiAgentTeamMemberTable`**（新文件）：`ai_agent_team_member` 表 DDL + 列名常量。每行一个团队成员，记录成员不可变身份（memberName / agentModel / role / joinedAt）+ 可变绑定（sessionId / actorId）。唯一约束 = (TEAM_ID, MEMBER_NAME)。TEAM_ID 为外键语义（引用 `ai_agent_team.TEAM_ID`）。
- **快照重建读语义**（Design Decision 3）：`getTeam` / `getTeamBySession` / `getActiveTeams` / `getMember` 经 SELECT 读出团队行 + 关联成员行，重建为新鲜 `Team` + member map 快照返回。写操作（createTeam / addMember / removeMember / bindMemberSession / disbandTeam）执行 DML 直接修改 DB。返回的 `Team` 是查询时刻的一致快照（非 live 可变对象），调用者对返回 map 的修改不持久化——符合 `ITeamManager` 契约"返回的 members 须只读"要求。
- **首次绑定 CREATED→ACTIVE 激活 = 条件 UPDATE exactly-once**（Design Decision 5）：`bindMemberSession` 在成功 UPDATE 成员绑定后，执行 `UPDATE ai_agent_team SET STATUS='ACTIVE' WHERE TEAM_ID=? AND STATUS='CREATED'`——至多一个并发绑定触发激活（与 `InMemoryTeamManager` 的 `teams.compute` exactly-once 语义等价）。
- **`getTeamBySession` 反查 = SELECT member 表**（Design Decision 4）：经 `SELECT TEAM_ID FROM ai_agent_team_member WHERE SESSION_ID=?` 反查，而非维护内存反查索引。foundational slice 接受每次反查一次 DB 读；性能优化（索引/缓存）为 successor。
- **跨进程共享语义**：多个 JVM 实例指向同一 DB 时，任一实例创建/解散的团队、添加/移除/绑定的成员，对其他实例可见。`getActiveTeams` 返回 DB 中所有非 DISBANDED 团队。
- **NoOp shipped 默认零回归**：`NoOpTeamManager` 仍是 shipped 默认（`DefaultAgentEngine.teamManager` 默认值）。`DbTeamManager` 是 opt-in，由集成商显式构造注入。既有全量测试零回归。
- **端到端验证**（Anti-Hollow #22）：两个独立 `DbTeamManager` 实例（不同 `DataSource` 句柄指向同一 H2 DB）+ 两个 `DefaultAgentEngine` 各自 `setTeamManager`——实例 A 创建团队 + 绑定成员 session → 实例 B 经 `getTeamBySession` 看到该团队与绑定（跨进程可见）；实例 A disband → 实例 B `getTeam` 看到 DISBANDED。再加一个 `DefaultTeamAclChecker`（持有实例 A 的 `DbTeamManager`）验证 ACL 检查经 DB-backed manager 透明工作（drop-in 语义）。
- vision §4.2 TeamManager 状态从"DB 持久化为 successor"更新为"DB-backed 持久化已落地（raw JDBC，@BizModel/ORM 仍为 successor 方向）"。

## Non-Goals

- **`@BizModel("AiTeam")` + ORM 实体持久化**（vision §4.2 平台映射）：vision 层方向描述为 Nop 平台 ORM 实体 + BizModel 自动暴露 GraphQL API。本计划交付 raw JDBC 实现（与模块所有 DB 持久化约定一致，见 Design Decision 1）。完整 ORM 实体 + BizModel + GraphQL API 自动暴露是后续平台集成 successor。Classification: successor plan required。
- **多租户 tenantId/userId 隔离**（vision §5.1）：不同租户的团队/Actor 完全不可见、DB 查询自动加 tenantId 条件。本计划 `ai_agent_team` / `ai_agent_team_member` 表无 tenantId 列，团队对全部实例可见（无租户隔离）。Classification: successor plan required（独立 carry-over `L4-multi-tenant-isolation`，依赖 tenant 标识标准化）。
- **DB-backed ACL 规则持久化**（vision §5.1 `TeamAclEntry` 表）：跨进程共享 ACL 规则表。本计划 ACL 角色矩阵仍由 `DefaultTeamAclChecker` 硬编码（plan 228 交付），角色信息从 `ai_agent_team_member.ROLE` 列读取（ACL 检查器经 `DbTeamManager.getTeam()` 透明获得成员角色）。`TeamAclEntry` 表持久化是 successor。Classification: successor plan required。
- **TeamSpec `permissions` override**（vision §5.1）：per-team/per-member 权限收紧。本计划 `ai_agent_team` 表无 permissions 列。Classification: successor plan required（plan 228 Non-Goal，依赖 TeamSpec 扩展）。
- **自动团队绑定**（从 agent config 的 TeamSpec 自动创建团队 + 绑定成员 session）：引擎三入口点自动调用 TeamManager。本计划团队创建仍由集成商程序化调用。Classification: successor plan required（plan 223/225 Non-Goal，依赖 TeamSpec XDSL 配置化）。
- **TeamSpec XDSL 配置化**（`team-spec.xdef`）：团队定义经 XDSL 配置加载、Delta 定制。Classification: successor plan required（plan 223 Non-Goal）。
- **跨进程团队消息路由**：经 `DBMessageService` 跨进程投递 team-send-message。本计划交付团队状态 DB 共享，不改变 `team-send-message` 现有 in-memory messenger 路径。Classification: successor plan required（依赖 DBMessageService 部署）。
- **ResourceGuard + 团队成员配额强制**（`maxParallelMembers`）：本计划 `ai_agent_team.MAX_PARALLEL_MEMBERS` 列存储该 hint 但不强制（与 `TeamSpec.maxParallelMembers` 语义一致——foundational slice 记录但不强制）。Classification: successor plan required（plan 223/225/228 Non-Goal）。
- **Fencing Token**（vision §5.1 monotonic counter 并发写入防护）：属 ResourceGuard / Phase 5 范畴。Classification: successor plan required。
- **二级索引/查询缓存优化**：`ai_agent_team_member.SESSION_ID` 列在 foundational slice 不建 DB 二级索引（correctness 不依赖索引，H2 测试全表扫描足够）。生产部署的性能优化（显式 CREATE INDEX / 读缓存 / materialized view）是 successor。Classification: optimization candidate。
- **read-after-write 强一致性保证超出单实例 DB 事务范围的部分**：foundational slice 依赖 DB 事务的 read-committed 隔离（H2 默认）——同一实例的写后立即读可见，跨实例经 DB 事务提交后可见。分布式会话/跨 DB 一致性是 successor。
- **`nop-task` DAG 集成 / blockedBy 依赖解析 / 自动团队绑定 / 团队消息 DB 路由**：均为 plan 225/227 显式 successor，本计划不触及。

## Scope

### In Scope

- `io.nop.ai.agent.team` 包（新文件）：
  - `AiAgentTeamTable.java` — `ai_agent_team` 表 DDL + 列名常量（TEAM_ID PK + TEAM_NAME + DESCRIPTION + LEAD_AGENT_NAME + MAX_PARALLEL_MEMBERS + STATUS + CREATED_AT + DISBANDED_AT）
  - `AiAgentTeamMemberTable.java` — `ai_agent_team_member` 表 DDL + 列名常量（TEAM_ID + MEMBER_NAME + AGENT_MODEL + ROLE + SESSION_ID + ACTOR_ID + JOINED_AT，唯一约束 (TEAM_ID, MEMBER_NAME)）
  - `DbTeamManager.java` — implements `ITeamManager` 全部 9 方法（raw JDBC：createTeam INSERT team + INSERT members / getTeam SELECT team + members 重建快照 / getTeamBySession SELECT member 反查 / disbandTeam 条件 UPDATE status / getActiveTeams SELECT 非终态 / addMember INSERT member / removeMember DELETE member / bindMemberSession UPDATE member + 条件 UPDATE team 激活 / getMember SELECT member）
- 测试文件：
  - `TestDbTeamManager.java` — H2 真实 DB（构造期建两张表 / 完整生命周期：create→get→addMember→bindMemberSession→ACTIVE→disband→DISBANDED / disbanded 拒绝写 / removeMember 清理 / getTeamBySession 反查 / getActiveTeams 过滤终态 / 快照隔离：两次 getTeam 返回独立对象 / 字段保真 round-trip 含 null description）
  - `TestDbTeamManagerCrossInstance.java` — 跨实例共享（两个 DbTeamManager 指向同一 H2：实例 A create+bind → 实例 B getTeamBySession 可见 / 实例 A disband → 实例 B getTeam 见 DISBANDED / 并发 bindMemberSession 仅一个触发 CREATED→ACTIVE）
  - `TestDbTeamManagerWiring.java` — drop-in 接线（`DefaultAgentEngine.setTeamManager(new DbTeamManager(ds))` + `DefaultTeamAclChecker` 持有 DbTeamManager，验证 team-status 工具经 DB-backed manager 透明工作；Anti-Hollow #23 接线验证：engine→ReAct→tool→context→DbTeamManager→DB 调用链连通）

### Out Of Scope

- `@BizModel("AiTeam")` + ORM 实体 + GraphQL API（Non-Goal: 平台 ORM 映射）
- tenantId/userId 列 + 多租户隔离（Non-Goal: 多租户）
- `TeamAclEntry` 表（Non-Goal: DB-backed ACL 规则持久化）
- TeamSpec permissions 列（Non-Goal: permissions override）
- 自动团队绑定 / TeamSpec XDSL（Non-Goal: 配置化团队）
- 跨进程团队消息路由（Non-Goal: DBMessageService 集成）
- DB 二级索引 / 读缓存（Non-Goal: 性能优化）
- `maxParallelMembers` 配额强制（Non-Goal: ResourceGuard）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **持久化方案 = raw JDBC + Table 常量类（非 ORM 实体，非 @BizModel）**。vision §4.2 与 carry-over 措辞为"`@BizModel("AiTeam")` + ORM 实体持久化"，但模块内既有 DB 持久化全部为 raw JDBC（`DBSessionStore` / `DbSessionTakeoverLock` / `DBMessageService` / `DBDenialLedger` / `DBCheckpointManager` / `DBUsageRecorder` / `DbTeamTaskStore` / `DefaultOrphanRecoveryHandler`），无 ORM/DAO/codegen 管线。"@BizModel + ORM"是 vision 层方向描述，实施层遵循已验证的 raw JDBC + Table 常量类 + 构造期 `initSchema()` 自动建表模式（直接镜像 `AiAgentTeamTaskTable` + `DbTeamTaskStore`，plan 227 Design Decision 1 同一裁定）。表名 `ai_agent_team` + `ai_agent_team_member`。理由：(1) 与模块 DB 持久化约定 100% 一致；(2) raw JDBC 无 ORM 依赖，`nop-ai-agent` 无需引入 DAO/codegen 管线；(3) 构造期自动建表使集成商无需手工 DDL；(4) `@BizModel` + GraphQL 自动暴露是独立的平台集成层决策，不阻塞团队状态 DB 共享能力的交付。

2. **两张表：`ai_agent_team`（团队）+ `ai_agent_team_member`（成员，1-to-many）**。团队行记录 `TeamSpec` 配置字段（teamName / description / leadAgentName / maxParallelMembers）+ 状态字段（status / createdAt / disbandedAt）。成员行记录 `TeamMemberSpec` 身份（memberName / agentModel / role）+ 绑定（sessionId / actorId）+ joinedAt。两表经 TEAM_ID 关联（成员表 TEAM_ID 引用团队表 TEAM_ID，foundational slice 不加物理 FK 约束——H2 测试 + raw JDBC 模式下逻辑关联已足够，物理 FK 增加 DDL 复杂度且与模块其他 raw JDBC 表如 `ai_agent_team_task.TEAM_ID` 无 FK 一致）。成员表唯一约束 (TEAM_ID, MEMBER_NAME) 对应 `addMember` 的 duplicate 检测。

3. **读语义 = 快照重建（非 live 可变对象）**。`InMemoryTeamManager` 返回 live `Team` 对象，manager 直接 in-place mutate 其 member map。`DbTeamManager` 不同：读方法经 SELECT 团队行 + 关联成员行重建为新鲜 `Team`（含新鲜 `ConcurrentHashMap` member map），写方法执行 DML 直接改 DB。返回的 `Team` 是查询时刻的一致快照——调用者对返回 map 的修改不持久化、不影响其他读者。理由：(1) 符合 `ITeamManager` 契约（"callers must treat returned members as read-only"、所有 mutation 经 manager 方法）；(2) 镜像 `DbTeamTaskStore` 的快照重建模式（每次 `getTask` 重建不可变 `TeamTask`）；(3) DB-backed 下维护 live 可变对象 + 写回 DB 需引入脏标记/OptimisticLock 复杂度，超出 foundational slice。此行为差异在 `DbTeamManager` Javadoc 显式记录，非静默降级。

4. **`getTeamBySession` 反查 = SELECT member 表 SESSION_ID**。`InMemoryTeamManager` 维护 `sessionToTeamId` 内存反查索引；`DbTeamManager` 经 `SELECT TEAM_ID FROM ai_agent_team_member WHERE SESSION_ID=?` 反查。foundational slice 接受每次反查一次 DB 全表扫描（H2 测试 correctness 不依赖索引）。生产性能优化（CREATE INDEX ON SESSION_ID / 读缓存）是 successor optimization candidate。

5. **首次绑定 CREATED→ACTIVE 激活 = 条件 UPDATE exactly-once**。`bindMemberSession` 成功 UPDATE 成员绑定（`UPDATE ai_agent_team_member SET SESSION_ID=?, ACTOR_ID=? WHERE TEAM_ID=? AND MEMBER_NAME=?`，affected-row-count==1 判定成员存在）后，执行 `UPDATE ai_agent_team SET STATUS='ACTIVE' WHERE TEAM_ID=? AND STATUS='CREATED'`——并发场景下至多一个绑定把 CREATED 改成 ACTIVE（第二个 UPDATE 影响 0 行，幂等），与 `InMemoryTeamManager.teams.compute` exactly-once 语义等价。bindMemberSession 返回 true 当且仅当成员绑定成功（激活 UPDATE 的 affected-row-count 不影响返回值——已有 InMemoryTeamManager 语义：绑定成功即 true，激活是副作用）。

6. **disbandTeam = 条件 UPDATE + 幂等**。`UPDATE ai_agent_team SET STATUS='DISBANDED', DISBANDED_AT=? WHERE TEAM_ID=? AND STATUS <> 'DISBANDED'`。已 DISBANDED 的团队再次 disband 幂等返回当前状态（与 `InMemoryTeamManager` 幂等语义一致）。**DISBANDED 校验范围与 `InMemoryTeamManager` 运行时行为精确一致**：仅 `addMember` 在 DML 前校验团队存在 + 非 DISBANDED（SELECT 团队行检查 STATUS=DISBANDED → 抛 `NopAiAgentException`，与 `InMemoryTeamManager.addMember` `:153` fail-fast 一致）；`bindMemberSession` 不校验 DISBANDED（与 `InMemoryTeamManager.bindMemberSession` `:196-206` 运行时行为一致——该方法仅检查 `team == null`，DISBANDED 团队绑定成员成功返回 true、跳过 CREATED→ACTIVE 激活）。注意 `TeamStatus` Javadoc 称 DISBANDED 终态"addMember / bindMemberSession / removeMember fail fast"，但 `InMemoryTeamManager.bindMemberSession` 运行时实际未强制——本计划 `DbTeamManager` 遵循 de-facto 运行时 baseline（drop-in 行为一致），不扩大也不收紧该分歧（InMemoryTeamManager 的 doc-vs-code 分歧修复是独立 successor，不在本计划 scope）。

7. **无引擎/context 代码变更**。`DbTeamManager implements ITeamManager` 经 `DefaultAgentEngine.setTeamManager(new DbTeamManager(dataSource))` drop-in 注入。`ITeamManager` 契约 + 全链路接线（DefaultAgentEngine → ReActAgentExecutor → AgentToolExecuteContext）在 plan 223 已完整交付。本计划不修改任何引擎/context/test 既有代码（除新增测试文件）。`DefaultTeamAclChecker`（plan 228）持有 `ITeamManager` 引用经 `getTeam()` 读团队——`DbTeamManager` drop-in 透明工作。

8. **TeamSpec 重建**。读团队时从 `ai_agent_team` 行重建 `TeamSpec`（teamName / description / leadAgentName / maxParallelMembers）+ 从 `ai_agent_team_member` 行重建 `List<TeamMemberSpec>`（memberName / agentModel / role）。`Team` 对象的 `spec` 字段经此重建。createTeam 时反向：从入参 `TeamSpec` 拆出团队列 + 从 `memberSpecs` 拆出成员行 INSERT。

### Phase 1 - AiAgentTeamTable + AiAgentTeamMemberTable + DbTeamManager 实现 + focused H2 测试

Status: completed
Targets: `io.nop.ai.agent.team`（AiAgentTeamTable / AiAgentTeamMemberTable / DbTeamManager 新文件）、`io.nop.ai.agent.team` 测试包（TestDbTeamManager / TestDbTeamManagerCrossInstance）

- Item Types: `Proof`

- [x] 定义 `AiAgentTeamTable`（DDL_CREATE_TABLE 含 `CREATE TABLE IF NOT EXISTS ai_agent_team` + 全部列常量 + PK TEAM_ID；列：TEAM_ID VARCHAR(100) / TEAM_NAME VARCHAR(500) / DESCRIPTION VARCHAR(4000) nullable / LEAD_AGENT_NAME VARCHAR(200) / MAX_PARALLEL_MEMBERS INT / STATUS VARCHAR(20) / CREATED_AT BIGINT / DISBANDED_AT BIGINT）
- [x] 定义 `AiAgentTeamMemberTable`（DDL_CREATE_TABLE 含 `CREATE TABLE IF NOT EXISTS ai_agent_team_member` + 全部列常量 + 唯一约束 (TEAM_ID, MEMBER_NAME)；列：TEAM_ID VARCHAR(100) / MEMBER_NAME VARCHAR(200) / AGENT_MODEL VARCHAR(200) / ROLE VARCHAR(20) / SESSION_ID VARCHAR(200) nullable / ACTOR_ID VARCHAR(200) nullable / JOINED_AT BIGINT）
- [x] 实现 `DbTeamManager implements ITeamManager`：构造期接收 `DataSource` + `initSchema()` 建两张表
- [x] 实现 `createTeam`：生成 UUID teamId + INSERT ai_agent_team（含 TeamSpec 字段）+ 批量 INSERT ai_agent_team_member（从 memberSpecs，SESSION_ID/ACTOR_ID = null）+ 返回重建 Team 快照
- [x] 实现 `getTeam`：SELECT ai_agent_team WHERE TEAM_ID + SELECT ai_agent_team_member WHERE TEAM_ID → 重建 TeamSpec + member map → 返回 Optional<Team> 快照（团队不存在返回 empty）
- [x] 实现 `getTeamBySession`：SELECT TEAM_ID FROM ai_agent_team_member WHERE SESSION_ID → 委托 getTeam（Design Decision 4）
- [x] 实现 `disbandTeam`：条件 UPDATE status=DISBANDED + disbandedAt（幂等，Design Decision 6）；团队不存在抛 NopAiAgentException（与 InMemoryTeamManager 一致）
- [x] 实现 `getActiveTeams`：SELECT ai_agent_team WHERE STATUS <> 'DISBANDED' → 逐个重建 Team 快照（含各自成员）
- [x] 实现 `addMember`：SELECT 团队校验存在 + 非 DISBANDED → INSERT ai_agent_team_member（duplicate 唯一约束违反 → 抛 NopAiAgentException，与 InMemoryTeamManager putIfAbsent duplicate 检测一致）
- [x] 实现 `removeMember`：DELETE ai_agent_team_member WHERE TEAM_ID AND MEMBER_NAME（affected-row-count==1 判定存在）；团队/成员不存在返回 false
- [x] 实现 `bindMemberSession`：UPDATE ai_agent_team_member SET SESSION_ID, ACTOR_ID WHERE TEAM_ID AND MEMBER_NAME（affected==1 判定成员存在）→ 条件 UPDATE ai_agent_team SET STATUS='ACTIVE' WHERE TEAM_ID AND STATUS='CREATED'（exactly-once 激活，Design Decision 5）→ 返回绑定是否成功
- [x] 实现 `getMember`：SELECT ai_agent_team_member WHERE TEAM_ID AND MEMBER_NAME → 重建 TeamMember（含绑定）；不存在返回 empty
- [x] `DbTeamManager` Javadoc 显式记录 Design Decisions（快照重建语义、raw JDBC 模式、跨进程共享、无租户隔离、refer plan 221/227 raw JDBC 模式 + plan 223 契约）
- [x] 编写 `TestDbTeamManager`：构造期建表 / create→get round-trip 全字段保真（含 null description）/ addMember→getMember / bindMemberSession→ACTIVE / disbanded 拒绝 addMember（抛 NopAiAgentException，与 InMemoryTeamManager.addMember 一致）/ disbandTeam 幂等 / removeMember 后 getMember empty / getTeamBySession 反查 / getActiveTeams 过滤 DISBANDED / 快照隔离（两次 getTeam 返回不同对象实例，修改一个不影响另一个）/ 团队不存在 getTeam 返回 empty
- [x] 编写 `TestDbTeamManagerCrossInstance`：两个 DbTeamManager 指向同一 H2（DB_CLOSE_DELAY=-1）——实例 A create+addMember+bindMemberSession → 实例 B getTeamBySession 看到团队+成员+绑定 / 实例 A disband → 实例 B getTeam 见 DISBANDED / 并发 bindMemberSession 仅一个触发 CREATED→ACTIVE

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AiAgentTeamTable.java` / `AiAgentTeamMemberTable.java` / `DbTeamManager.java` 存在于 `io.nop.ai.agent.team` 包，`DbTeamManager implements ITeamManager` 编译通过
- [x] `DbTeamManager` 全部 9 个 `ITeamManager` 方法为真实非空壳 raw JDBC 实现（无空方法体 / 无静默跳过 / 无 TODO——Minimum Rules #24）
- [x] `TestDbTeamManager` 全部断言通过（H2 真实 DB，非 mock），覆盖完整生命周期 + 状态机 + 快照隔离 + null description round-trip
- [x] `TestDbTeamManagerCrossInstance` 全部断言通过，验证跨实例共享 + 并发激活 exactly-once
- [x] **无静默跳过**：新增方法在未实现路径抛异常或返回 empty（按契约），不返回 placeholder
- [x] No owner-doc update required（design 文档更新在 Phase 2）

### Phase 2 - drop-in 接线端到端验证 + 文档更新

Status: completed
Targets: `io.nop.ai.agent.team` 测试包（TestDbTeamManagerWiring）、`ai-dev/design/nop-ai-agent/`（vision）、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 编写 `TestDbTeamManagerWiring`：构造 `DefaultAgentEngine` + `setTeamManager(new DbTeamManager(ds))` + 构造 `DefaultTeamAclChecker(dbTeamManager)` → 程序化 createTeam + bindMemberSession → 调用 `DefaultTeamAclChecker.checkAccess` 验证经 DbTeamManager.getTeam() 透明解析角色（drop-in 语义）；验证 team-status IToolExecutor 经 `AgentToolExecuteContext.getTeamManager()`（DbTeamManager）返回团队信息
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言 `DbTeamManager` 确实被 `DefaultAgentEngine.teamManager` 持有并经 `AgentToolExecuteContext.getTeamManager()` 在工具执行时调用（非仅 DbTeamManager 自测）
- [x] **端到端验证**（Minimum Rules #22）：两个 engine 实例共享同一 H2——engine A createTeam + bindMemberSession(sessionA) → engine B 的 team-status 工具（经 DbTeamManager）看到该团队 + 成员 + taskCount
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §4.2 TeamManager 平台层组件：DB-backed 持久化状态从 successor → 已落地（raw JDBC DbTeamManager + ai_agent_team / ai_agent_team_member 表，@BizModel/ORM 仍为 successor 平台集成方向）；标注快照重建语义与 InMemoryTeamManager live 对象语义的差异
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4：line 257 "租户间资源隔离"标注团队状态跨进程 DB 共享基础设施已交付（完整多租户 tenantId/userId 隔离仍为 successor）
- [x] `ai-dev/logs/` 对应日期条目更新（记录 DbTeamManager 交付 + 跨进程共享能力 + 快照重建语义裁定）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestDbTeamManagerWiring` 全部断言通过，验证 `DbTeamManager` 经 `setTeamManager` drop-in 接入 engine + `DefaultTeamAclChecker` + team-status 工具透明工作
- [x] **端到端验证**：两 engine 实例共享同一 H2，team-status 工具经 DbTeamManager 看到对方实例创建的团队（入口点 setTeamManager → 出口点 team-status JSON 反映跨实例团队）
- [x] **接线验证**：测试断言 DbTeamManager 在运行时被 engine context 调用（非仅类型存在）
- [x] `nop-ai-agent-actor-runtime-vision.md` §4.2 已更新（TeamManager DB-backed 持久化状态 + 快照重建语义）
- [x] `nop-ai-agent-roadmap.md` §4 Layer 4 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `DbTeamManager` + `AiAgentTeamTable` + `AiAgentTeamMemberTable` 存在且为真实非空壳 raw JDBC 实现
- [x] `DbTeamManager implements ITeamManager` 全部 9 方法经 H2 真实 DB focused 测试验证
- [x] 跨实例共享语义验证（两 DbTeamManager 指向同一 H2，团队/成员/绑定/disband 跨实例可见）
- [x] drop-in 接线验证（`setTeamManager(new DbTeamManager(ds))` 无引擎代码变更，既有 team 工具 + ACL 检查器透明工作）
- [x] NoOp shipped 默认零回归（`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（@BizModel/ORM / 多租户 / DB ACL 表 / permissions override / 自动团队绑定 / TeamSpec XDSL / 跨进程消息路由 / ResourceGuard / Fencing Token / 二级索引优化 / read-after-write 超出单 DB 范围均显式在 Non-Goals 切出）
- [x] 受影响 owner docs 已同步（vision §4.2 + roadmap §4 Layer 4 + 快照重建语义记录）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通（engine→ReAct→tool→context→DbTeamManager→DB），（b）端到端跨实例路径完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/230-nop-ai-agent-team-db-persistence.md --strict` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

## Deferred But Adjudicated

（暂无；@BizModel/ORM / 多租户隔离 / DB-backed ACL 表 / permissions override / 自动团队绑定 / TeamSpec XDSL / 跨进程消息路由 / ResourceGuard 配额 / Fencing Token / 二级索引优化 / read-after-write 超出单 DB 范围 / nop-task DAG 集成 / blockedBy 依赖解析均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **`@BizModel("AiTeam")` + ORM 实体 + GraphQL API 自动暴露**（vision §4.2 平台映射）：完整 Nop 平台 ORM 集成。Classification: successor plan required（依赖平台 ORM/BizModel 集成裁定）。
- **多租户 tenantId/userId 隔离**（vision §5.1）：`ai_agent_team` + `ai_agent_team_member` 加 TENANT_ID 列 + DB 查询自动加 tenantId 条件。Classification: successor plan required（独立 carry-over `L4-multi-tenant-isolation`）。
- **DB-backed ACL 规则持久化**（vision §5.1 `TeamAclEntry` 表）：跨进程共享 ACL 规则。Classification: successor plan required。
- **TeamSpec `permissions` override**（vision §5.1）：per-team/per-member 权限收紧，`ai_agent_team` 加 PERMISSIONS 列。Classification: successor plan required（plan 228 follow-up）。
- **自动团队绑定 + TeamSpec XDSL**（vision §8.1）：从 agent config 自动建团/绑定 + `team-spec.xdef` 配置加载。Classification: successor plan required。
- **跨进程团队消息路由**：经 `DBMessageService` 跨进程投递 team-send-message。Classification: successor plan required。
- **ResourceGuard + `maxParallelMembers` 配额强制**：`ai_agent_team.MAX_PARALLEL_MEMBERS` 从 hint 升级为强制。Classification: successor plan required。
- **二级索引/读缓存优化**：`CREATE INDEX ON ai_agent_team_member(SESSION_ID)` + 读缓存。Classification: optimization candidate。
- **Fencing Token**（vision §5.1）：monotonic counter 并发写入防护。Classification: successor plan required（属 Phase 5）。

## Closure

Status Note: 交付 `DbTeamManager`（`ITeamManager` 的 raw-JDBC DB-backed 实现）+ `ai_agent_team` / `ai_agent_team_member` 两张共享表（DDL 常量类 + 构造期 `initSchema` 自动建表）。经 `setTeamManager(new DbTeamManager(dataSource))` drop-in 注入，无引擎/context 代码变更。闭合 plan 223/227/228 显式切出的"团队 DB 持久化"successor gap——团队与成员状态跨进程共享、进程重启后可重建。读语义为快照重建（区别于 InMemoryTeamManager 的 live 可变对象）。NoOp shipped 默认零回归。24 focused/E2E 测试全绿（含跨实例共享 + 并发激活 exactly-once + drop-in ACL/team-status 接线）。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit（task `ses_12c79eae7ffe0YBJCrhNB3m5DV`，fresh session，非实现阶段 task）
- Evidence:
  - **A. 实现真实性（非空壳）**: AiAgentTeamTable.java:53-64（`CREATE TABLE IF NOT EXISTS ai_agent_team` + PK TEAM_ID + 8 列）PASS；AiAgentTeamMemberTable.java:56-66（`CREATE TABLE IF NOT EXISTS ai_agent_team_member` + UK_TEAM_MEMBER(TEAM_ID,MEMBER_NAME) + 7 列）PASS；DbTeamManager.java:84 `implements ITeamManager` + 构造器 :101-104 调 `initSchema()` 执行两 DDL；全部 9 方法为真实 raw JDBC（createTeam:121-163 / getTeam:202-212 / getTeamBySession:214-241 / disbandTeam:279-317 / getActiveTeams:243-261 / addMember:323-373 / removeMember:375-393 / bindMemberSession:399-460 / getMember:263-273），无空方法体/TODO/吞没异常 PASS
  - **B. 快照重建语义（裁定 3）**: getTeam:207-211 返回 `rebuildTeam(row)`；rebuildTeam:601-620 每次构造新 `Team` + 新 `ConcurrentHashMap` member map，调用者修改不达 DB PASS
  - **C. 状态机 CAS**: bindMemberSession:425 `executeUpdate()==1` 判定成员绑定 + :441-450 独立条件 `UPDATE ... SET STATUS='ACTIVE' WHERE STATUS='CREATED'` exactly-once 激活 + :459 返回值仅依赖绑定成功（裁定 5）PASS；disbandTeam:288 先 selectTeamRow 不存在抛 NopAiAgentException:289-292 + :299 条件 `WHERE STATUS<>'DISBANDED'` 幂等（裁定 6）PASS；addMember:332-341 DML 前校验存在+非 DISBANDED + :362-369 唯一约束/INSERT 失败 → NopAiAgentException PASS
  - **D. 无静默跳过（#24）**: 无空 `{}` 方法体、无 `catch(Exception e){}`、无 TODO/FIXME 当已完成、无 `continue` 跳过。3 处 `return false`（:378/:405/:433）为 null-guard/CAS-fail（与 InMemoryTeamManager 同模式）；2 处 `return null`（:518/:571）为内部 selectXxxRow row-not-found 哨兵，由 public 调用者转 Optional.empty() PASS
  - **E. 测试真实性（#22 Anti-Hollow + #23 接线）**: 3 个测试文件存在且断言真实 PASS；TestDbTeamManagerWiring.engineHoldsInjectedDbTeamManagerInstance:118 `assertSame(dbMgr, engine.getTeamManager())`（接线 #23）+ crossInstanceTeamStatusViaSharedDb:229-285 两 engine 共享 H2，engine B team-status 观察 engine A 团队（teamId/SharedTeam/ACTIVE/members=2）+ disband 可见（E2E #22 跨实例）PASS；TestDbTeamManager.snapshotIsolationTwoGetTeamCalls:362-381 `assertNotSame(snap1,snap2)` + 改 snap1.getMembers() 不影响 snap3/DB（裁定 3 验证）PASS
  - **F. 文档同步**: vision §4.2 TeamManager 行（nop-ai-agent-actor-runtime-vision.md:202）标注 DbTeamManager/plan 230/快照重建语义 PASS；roadmap §4 Layer 4 新增 L4-team-db-persistence 行（nop-ai-agent-roadmap.md:254 ✅）+ 验收标准（:259）标注跨进程 DB 共享基础设施已交付 PASS；ai-dev/logs/2026/06-17.md:1 plan-230 条目 PASS
  - **G. 一致性**: Phase 1（Status: completed:112 + items:117-131 全 [x] + Exit Criteria:137-142 全 [x]）；Phase 2（Status: completed:146 + items:151-156 全 [x] + Exit Criteria:162-167 全 [x]）PASS
  - **checklist 工具**: `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/230-nop-ai-agent-team-db-persistence.md --strict` 退出码 0（Plans checked: 1, Passed: 1）
  - **Anti-Hollow 自动扫描**: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（Critical/High/Medium/Low 全 0 findings）
  - **构建验证**: `./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS（新增 24 tests 全绿 + 既有全模块零回归）
  - **文档链接检查**: `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（No errors found）
  - **Deferred 项分类检查**: 所有 Non-Goals（@BizModel/ORM / 多租户 / DB ACL 表 / permissions override / 自动团队绑定 / TeamSpec XDSL / 跨进程消息路由 / ResourceGuard / Fencing Token / 二级索引优化 / read-after-write 超出单 DB）均为显式 successor/optimization candidate，无 in-scope live defect 被降级为 non-blocking

Follow-up:

- no remaining plan-owned work（所有 in-scope 项已 landed；其余为显式 Non-Goals successor，见 Non-Blocking Follow-ups）

## Follow-up handled by 232-nop-ai-agent-multi-tenant-isolation.md

> 多租户 tenantId/userId 隔离 successor（Non-Goals 第二条 + Non-Blocking Follow-ups 第二条，标 `successor plan required（独立 carry-over L4-multi-tenant-isolation）`）已由 successor plan `ai-dev/plans/232-nop-ai-agent-multi-tenant-isolation.md` 接管：交付全部 10 张 `ai_agent_*` 表 `TENANT_ID` 列 + contextual tenant 解析机制（thread-local backed `ITenantResolver`，不侵入 store 接口签名）+ 全部 DB store 类 tenantId WHERE 注入 + `ActorRegistry` tenant 过滤 + engine 入口点 tenant context 设置/清除。NoOp shipped 默认零回归（null tenant = 全部可见）。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
