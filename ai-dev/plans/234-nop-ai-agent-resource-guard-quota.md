# 234 nop-ai-agent ResourceGuard + 配额强制 — foundational count-based concurrent quota enforcement

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-resource-guard-quota

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/232-nop-ai-agent-multi-tenant-isolation.md`（Non-Goals：`ResourceGuard + 配额强制（vision §5.2 / §5.1 配额表）：每租户最大并发 Actor 数 / LLM 调用频率 / 存储配额。本计划只交付数据可见性隔离，不交付资源配额强制。Classification: successor plan required（vision §10 Phase 5）`）+ `ai-dev/plans/230-nop-ai-agent-team-db-persistence.md`（Non-Goals：ResourceGuard 配额）+ `ai-dev/plans/231-nop-ai-agent-team-auto-binding.md`（Non-Goals：maxParallelMembers 强制）+ `ai-dev/plans/227-nop-ai-agent-team-task-update.md`（Non-Goals：任务产物存储 successor 之外的 ResourceGuard 引用）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §5.2（资源配额表）+ §4.2 line 204（`ResourceGuard` = successor Phase 5 平台组件）+ §10 Phase 5（"ResourceGuard + 协调信道 + 资源配额"，依赖 Phase 2 已 ✅）；`ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §九（每租户最大并发 Agent 数 / LLM 调用频率上限 / 存储配额——配额在 Agent 启动前检查，超限则排队或拒绝）
> Related: `223`（交付 `TeamSpec.maxParallelMembers` capacity hint 字段，Javadoc 显式标注"does not enforce; quota enforcement is a successor, vision §5.2"）、`230`（交付 `DbTeamManager.TeamRow.maxParallelMembers` 持久化读取，line 570/733——存读但不强制）、`232`（交付 contextual `ITenantResolver` + `ThreadLocalTenantResolver` + `InMemoryActorRegistry` tenant 标签 map——本计划 per-tenant 配额 scopeKey 复用此机制）、`228`（确立 `ITeamAclChecker` 契约 + NoOp shipped 默认 + functional 实现 + 引擎全链路接线的 opt-in 模式——本计划遵循同一模式）

## Purpose

把 nop-ai-agent 的资源治理从"零配额强制——无 `ResourceGuard`/`IQuotaEnforcer` 契约、`TeamSpec.maxParallelMembers` 是被动 hint 字段（存读不强制）、`DbTeamManager.TeamRow.maxParallelMembers` 持久化但不消费、无任何并发计数配额检查接入 team/actor 编排路径"扩展为"经中央 `IResourceGuard` 配额决策网关 + NoOp shipped 默认（恒 allow = 零回归）+ functional 实现，对三类 count-based 并发配额维度强制：team 最大成员数 / `maxParallelMembers`（hint→enforced 升级）/ per-tenant 并发 Actor 数"。本计划交付 vision §10 Phase 5（ResourceGuard + 资源配额）的 foundational count-based 切片，是后续 LLM 调用频率 rate-limit / storage 配额 / Fencing Token 等维度的最高杠杆网关。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **零 `ResourceGuard` / `IQuotaEnforcer` / 配额强制实现**：grep `ResourceGuard|IQuotaEnforcer|IResourceGuard|quota.*enforce|checkConcurrent` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 命中（`_AgentConstraintsModel._maxIterations` 与 `AgentExecutionContext.maxIterations` 是 per-agent ReAct 迭代上限，非配额网关）。vision §4.2 line 204 标注 `ResourceGuard = successor（Phase 5）`。
- **`TeamSpec.maxParallelMembers` 是被动 hint 字段**（plan 223 ✅）：`io.nop.ai.agent.team.TeamSpec` line 46 `private final int maxParallelMembers` + getter line 102。Javadoc（line 32-35 / 97-100）显式"capacity hint. A value `<= 0` means unlimited. The foundational slice records this hint but does not enforce it; quota enforcement is an explicit successor (vision §5.2, ResourceGuard scope)"。`<= 0` = unlimited 语义已确立。
- **`DbTeamManager` 持久化读取 `maxParallelMembers` 但不消费强制**（plan 230 ✅）：`io.nop.ai.agent.team.DbTeamManager` 内部 `TeamRow` line 570 `final int maxParallelMembers` + 构造器 line 576 + 行重建 line 733——从 DB 读出该值放入 TeamRow，但 `bindMemberSession`（line 486）无条件 UPDATE，无任何并发上限检查。
- **`InMemoryTeamManager.bindMemberSession` 无配额检查**（plan 223 ✅）：`io.nop.ai.agent.team.InMemoryTeamManager` line 196 `bindMemberSession` 经 `ConcurrentHashMap` 串行化（line 49），无条件写入 session 索引，不限制并发绑定成员数。`createTeam` 从 spec 初始化成员（无成员上限检查），`addMember` 直接追加（无上限检查）。
- **`TeamMember.isBound()` 已存在**（plan 223 ✅）：`io.nop.ai.agent.team.TeamMember` line 126 `public boolean isBound()`——`autoBindMember`（engine line 2923）已消费此方法判定幂等跳过。"并行成员数" = 当前 `isBound()==true` 的成员数。
- **`TeamModelConverter` / `_TeamModel._maxParallelMembers` DSL 透传已落地**（plan 231 ✅）：`_TeamModel` line 42 `private java.lang.Integer _maxParallelMembers` + getter——声明式 `<team maxParallelMembers=...>` 经 `TeamModelConverter.toTeamSpec` 透传到 `TeamSpec`（agent.xdef 已含该属性，line 392 标注"hint, 不强制"）。升级为强制不需 DSL 变更，仅消费侧强制。
- **`ITenantResolver` contextual 租户解析已落地**（plan 232 ✅）：`io.nop.ai.agent.security.ITenantResolver` line 48 `resolveTenantId()` + `ThreadLocalTenantResolver` / `NullTenantResolver` shipped 默认。`DefaultAgentEngine` 在异步入口点 supplyAsync lambda 内 set/clear tenant context（line 1657）——本计划 per-tenant Actor 配额 scopeKey 复用此解析（createActor 在 supplyAsync worker 线程上调用，此时 tenant context 已设置）。
- **`InMemoryActorRegistry` tenant 标签 map 已落地**（plan 232 ✅）：`io.nop.ai.agent.runtime.InMemoryActorRegistry` line 84/131/160 经 `tenantResolver.resolveTenantId()` 注册时打 tenant 标签 + 查询时按 tenant 过滤——本计划 per-tenant 并发 Actor 计数可经 registry tenant 标签派生。
- **`IActorRuntime` opt-in `isEnabled()` gate 已确立**（plan 218 ✅）：`io.nop.ai.agent.runtime.IActorRuntime` line 66 `isEnabled()` + line 86 `createActor` + line 109 `getActiveActors()`。`NoOpActorRuntime` shipped 默认 `isEnabled()==false`，引擎跳过 Actor 路径。`InMemoryActorRuntime.getActiveActors()`（line 238）返回活跃 Actor 快照。createActor 在 engine supplyAsync 内调用（line 1849/2124/2301）。
- **NoOp shipped 默认 + opt-in setter 模式已确立**：每个扩展点契约有 NoOp 实现（恒 allow / 全可见 / `isEnabled()==false`）+ engine `setXxx` null-safe 回退（`setTeamManager` line 1324 / `setActorRuntime` line 1296）。本计划遵循同一模式。
- **`AgentExecutionContext.maxIterations`（default 10）已落地**：`io.nop.ai.agent.engine.AgentExecutionContext` line 30/62 + ReAct 循环消费——这是 per-agent ReAct 迭代上限，非 vision §5.2 配额表（配额表"单 Agent 最大迭代"= per-agentModel 配置维度，迭代上限语义已部分由 maxIterations 满足）。本计划 Non-Goal per-agent token/时间累积上限。
- **fail-fast 哲学**：`autoBindLead`/`autoBindMember`（engine line 2872/2927）在 `bindMemberSession` 返回 false 时抛 `NopAiAgentException`——本计划配额 denial 在这些 engine-internal 编排路径上同样 fail-fast（不静默）。
- **roadmap §4 Layer 4 已 100% ✅，§10 Phase 5 未启动**：`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 全部 work items ✅；`nop-ai-agent-actor-runtime-vision.md` §10 Phase 5（line 457）= ResourceGuard + 协调信道 + 资源配额，状态空（依赖 Phase 2 已 ✅，可启动）。

## Goals

- **中央 `IResourceGuard` 配额决策网关契约**：定义 `IResourceGuard`（配额决策入口，经 `QuotaDimension` 枚举 + `QuotaDecision` 不可变结果对象 allow/deny + dimension/scopeKey/limit/projectedCount/reason）。决策语义 = 比较 projectedCount 与 resolved limit（`<= 0` = unlimited，与 maxParallelMembers 既有语义一致）。
- **`NoOpResourceGuard` shipped 默认（零回归）**：恒返回 allow（不强制任何配额）——与 `NoOpTeamAclChecker`/`NoOpTenantResolver`/`NoOpActorRuntime` 模式一致。未 wire functional guard 时全部行为不变。
- **`DefaultResourceGuard` functional 实现**：持有 `QuotaConfig`（经 `@cfg:ai.agent.quota.*` 配置注入），对 config-driven 维度查表返回 decision；对 per-scope override 维度使用 caller 传入的 limit。
- **维度 1：`maxParallelMembers` hint→enforced 升级**（vision §5.2 团队最大并行成员数）：`InMemoryTeamManager.bindMemberSession` + `DbTeamManager.bindMemberSession` 在绑定前计数当前 `isBound()==true` 成员数，projectedCount = boundCount + 1，limit = team 的 `TeamSpec.maxParallelMembers`（per-team override）。`> 0` 且 projectedCount > limit → denial。
- **维度 2：team 最大成员数**（vision §5.2 默认 8，config `@cfg:ai.agent.quota.team.max-members`）：`InMemoryTeamManager.createTeam`（projectedCount = spec 初始成员数）+ `addMember`（projectedCount = currentMemberCount + 1），scopeKey = teamId/teamName，limit = QuotaConfig。
- **维度 3：per-tenant 并发 Actor 数**（vision §5.2 默认 10，config `@cfg:ai.agent.quota.tenant.max-concurrent-actors`）：`InMemoryActorRuntime.createActor` 在创建前经 `ITenantResolver.resolveTenantId()` 解析 tenant scopeKey，projectedCount = 该 tenant 当前活跃 Actor 数 + 1（经 registry tenant 标签派生），limit = QuotaConfig。仅在 functional guard wired + ActorRuntime `isEnabled()` 时生效。
- **Denial = fail-fast**：配额 denial 在 engine-internal 编排路径（createTeam/addMember/bindMemberSession/createActor，经 auto-bind 与 createActor 调用）抛 `NopAiAgentException`（英文消息 + dimension/scopeKey/limit/projectedCount）——与既有 auto-bind fail-fast 一致，Minimum Rules #24 无静默跳过。
- **NoOp shipped 默认零回归**：未 wire functional `IResourceGuard` 时（NoOp 默认 / 单租户 / 既有测试），全部 team/actor 行为与当前一致。既有全量测试零回归。
- **端到端验证**（Anti-Hollow #22）：同一引擎 + functional `DefaultResourceGuard`（低 limit 配置）—— team 声明 `maxParallelMembers=2` → 第 3 个成员 auto-bind 被拒（抛异常，绑定计数仍为 2）；team config `max-members=3` → 第 4 个 addMember 被拒；tenant config `max-concurrent-actors=2` + functional ActorRuntime → 第 3 个 createActor 被拒。验证三类维度 denial 实际阻止越限操作（Anti-Hollow：不止检查 NoOp 类型存在，验证 functional 路径 denial 真发生）。
- vision §5.2 三类 count-based 并发配额维度标注 foundational 已落地；vision §10 Phase 5 标注 foundational count-based 切片已交付（rate-limit / storage / Fencing 仍为 successor）。

## Non-Goals

- **LLM 调用频率 rate-limit**（vision §5.2 默认 100/min，`IRateLimiter`）：time-window / token-bucket 语义，独立配额池。本计划只交付 count-based 并发配额，不交付时间窗 rate-limit。Classification: successor plan required（依赖 IRateLimiter 时间窗语义裁定 + ReAct 循环 LLM 调用点接线）。
- **Compaction LLM 调用独立配额池**（vision §5.2 默认 20/min）：依赖 rate-limit 机制。Classification: successor（同上）。
- **storage 存储配额**（vision §5.1 配额表）：需 storage 用量度量基础设施。Classification: successor plan required（依赖 storage metrics 基础设施裁定）。
- **单 Agent 最大 Token / 最大时间**（vision §5.2 默认 200K / 30min，per-agentModel 累积）：需 per-agent token 累积追踪 + 时间预算。"单 Agent 最大迭代"语义已部分由 `AgentExecutionContext.maxIterations` 满足。Classification: successor plan required（依赖 per-agent token 累积 + ICancelToken 时间预算裁定）。
- **Fencing Token**（vision §5.1 monotonic counter 并发写入防护）：独立 carry-over（roadmap `L4-fencing-token`，P2），属 Phase 5 并行项。Classification: successor plan required。
- **DB-backed ACL 规则持久化（`TeamAclEntry` 表）**（vision §5.1）：跨进程共享 ACL 规则。Classification: successor plan required（独立 carry-over `L4-db-acl-persistence`）。
- **TeamSpec `permissions` override / 权限派生**（vision §5.1）：per-team/per-member 权限收紧 + 父子 Actor ACL 继承。Classification: successor plan required（plan 228 follow-up）。
- **ResourceGuard 协调信道（scope_claim / conflict_alert）**（vision §4.2 / multi-agent.md §4）：Actor 间协调协议，非配额维度。Classification: successor plan required（依赖协调协议裁定）。
- **User 隔离**（vision §5.1 同租户内用户级过滤）：独立 carry-over `L4-user-isolation`。Classification: successor plan required。
- **配额超限排队/等待语义**（architecture baseline §九"超限则排队或拒绝"中的"排队"）：本计划只交付"拒绝"（fail-fast），不交付"排队/等待"。Classification: optimization candidate（依赖背压/队列机制裁定）。
- **DB 持久化配额计数器 / 跨进程共享配额状态**：本计划配额计数派生自 in-memory 状态（registry/manager 内存索引 + config），非 DB 持久化。跨进程共享配额（如多 JVM 实例共享 per-tenant Actor 上限）是 successor。Classification: successor（依赖 DB-backed 配额存储裁定）。

## Scope

### In Scope

- `io.nop.ai.agent.quota` 包（新包）：
  - `IResourceGuard` 契约（配额决策入口）
  - `QuotaDimension` 枚举（in-scope：`TEAM_PARALLEL_BOUND_MEMBERS` / `TEAM_MEMBERS` / `CONCURRENT_ACTORS_PER_TENANT`）
  - `QuotaDecision` 不可变数据对象（allow/deny 工厂 + dimension/scopeKey/limit/projectedCount/reason）
  - `QuotaConfig` 配置对象（config-driven limits）
  - `NoOpResourceGuard` shipped 默认（恒 allow）
  - `DefaultResourceGuard` functional 实现（持有 QuotaConfig）
- `io.nop.ai.agent.team` 包：
  - `InMemoryTeamManager`（bindMemberSession maxParallelMembers 强制 + createTeam/addMember TEAM_MEMBERS 强制——构造期可选接收 `IResourceGuard`，默认 NoOp）
  - `DbTeamManager`（bindMemberSession maxParallelMembers 强制 + createTeam/addMember TEAM_MEMBERS 强制——构造期可选接收 `IResourceGuard`，默认 NoOp；消费既有 TeamRow.maxParallelMembers）
- `io.nop.ai.agent.runtime` 包：
  - `InMemoryActorRuntime`（createActor CONCURRENT_ACTORS_PER_TENANT 强制——构造期可选接收 `IResourceGuard` + `ITenantResolver`，默认 NoOp/Null）
- 测试文件（三类维度 denial + NoOp 零回归 focused/E2E 测试）
- 文档更新（vision §5.2 + §10 Phase 5、roadmap §4/§10）

### Out Of Scope

- LLM rate-limit / Compaction 配额池（Non-Goal: 时间窗）
- storage 配额（Non-Goal: 需 metrics 基础设施）
- per-agent token/时间累积上限（Non-Goal: 需累积追踪）
- Fencing Token（Non-Goal: 独立 carry-over）
- DB-backed ACL 表 / permissions override / 权限派生（Non-Goal: ACL successor）
- 协调信道 scope_claim/conflict_alert（Non-Goal: 协调协议 successor）
- User 隔离（Non-Goal: 独立 carry-over）
- 排队/等待语义（Non-Goal: 背压 successor）
- DB 持久化配额计数器（Non-Goal: 跨进程 successor）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **单一中央 `IResourceGuard` 配额决策网关**。全部 in-scope 配额检查路由经 `IResourceGuard`——enforcement point（TeamManager member/bind 操作 / ActorRuntime createActor）调用 guard 取 decision，guard 返回 `QuotaDecision`，enforcement point 在 denial 时 fail-fast 抛 `NopAiAgentException`。理由：(1) 与 vision §4.2 "`ResourceGuard` = 平台层单组件（资源配额）" 框架一致——guard 是 THE 配额决策点；(2) 后续 rate-limit / storage 维度扩展同一契约，不需再造决策点；(3) 与 `ITeamAclChecker`（单一 ACL 决策网关）模式一致。

2. **NoOp shipped 默认（恒 allow）= 零回归**。`NoOpResourceGuard` singleton shipped 默认，全部 `checkConcurrent` 返回 allow。未 wire functional guard 时（NoOp 默认 / 单租户 / 既有测试）team/actor 行为不变。functional impls（`InMemoryTeamManager` / `DbTeamManager` / `InMemoryActorRuntime`）构造期可选接收 `IResourceGuard`（默认 NoOp），与 `ITenantResolver`/`ITeamAclChecker` 构造期注入模式一致。**不新增 engine 顶层 `setResourceGuard` 字段**——本计划维度全部在 functional impls 内部决策，engine 既有调用路径（createTeam/addMember/bindMemberSession/createActor）不变。engine 顶层 guard 字段预留给后续 ReAct 循环 LLM rate-limit successor。

3. **limit 解析 = override 优先 + QuotaConfig 兜底**。guard 决策时：caller 传入的 per-scope override limit `> 0` 时使用 override；`<= 0` 时使用 QuotaConfig 中该维度的全局默认。`TEAM_PARALLEL_BOUND_MEMBERS` 的 limit = team 的 `TeamSpec.maxParallelMembers`（per-team override，由 TeamManager 从既有 spec/TeamRow 取，传入 guard）；`TEAM_MEMBERS` / `CONCURRENT_ACTORS_PER_TENANT` 的 limit = QuotaConfig 全局默认（override 传 `<= 0`）。`<= 0` = unlimited 语义与 maxParallelMembers 既有语义一致。理由：per-team spec 字段（maxParallelMembers）与 config 全局默认（team-max-members / tenant-max-concurrent-actors）共用同一决策路径，避免分叉契约。

4. **`maxParallelMembers` 升级 = bindMemberSession 计数 isBound 成员强制**。"并行成员数"语义 = 当前并发绑定（运行中）的成员数 = `isBound()==true` 的成员数。enforcement point = `bindMemberSession`（projectedCount = boundCount + 1，limit = spec.maxParallelMembers）。**createTeam 初始成员不计入 parallel**（成员初始 unbound）；**addMember 不计入 parallel**（新增成员初始 unbound）。parallel-bound 与 member-count（TEAM_MEMBERS）是两个独立维度：member-count 计全部成员（bound + unbound），parallel-bound 只计 bound。两个功能 TeamManager（`InMemoryTeamManager` + `DbTeamManager`）均强制——`DbTeamManager` 已持久化读取 `TeamRow.maxParallelMembers`（line 570/733），当前存读不消费，本计划消费它做强制。理由：(1) "并行" = 并发运行，对应 bound 状态；(2) 与既有 `autoBindMember` isBound 幂等判定语义一致。

5. **per-tenant Actor scope = ITenantResolver + InMemoryActorRegistry tenant 标签派生**。`InMemoryActorRuntime.createActor` 经 `ITenantResolver.resolveTenantId()` 解析当前 tenant（createActor 在 engine supplyAsync worker 线程上调用，plan 232 已确保此时 tenant context 已设置）作 scopeKey；projectedCount = 该 tenant 当前活跃 Actor 数 + 1，活跃 Actor 计数经 `InMemoryActorRegistry` tenant 标签 map（plan 232）派生（扫 `getActiveActors()` 按 tenant 标签过滤）。tenant 为 null（无 tenant context / 单租户）时 scopeKey = 全局单桶（或由 integrator 决定），limit 仍来自 QuotaConfig。仅在 functional guard wired + `isEnabled()==true` 时生效；NoOp guard / NoOp runtime = 不强制。理由：(1) 复用 plan 232 已交付的 tenant 解析 + 标签基础设施，零新增 tenant 机制；(2) 仅 ActorRuntime 启用时才有 Actor 计数可言。

6. **Denial = fail-fast（非 LLM-facing 诚实反馈）**。配额 denial 在 engine-internal 编排路径（createTeam/addMember/bindMemberSession 经 auto-bind 调用 / createActor 经 supplyAsync 调用）抛 `NopAiAgentException`（英文消息含 dimension/scopeKey/limit/projectedCount）。这些是 engine-internal 路径，非 LLM-facing 工具结果——fail-fast 与既有 `autoBindMember` bind 失败抛 `NopAiAgentException` 一致。LLM-facing 工具（如未来 team 工具经 guard 拒绝时返回诚实策略反馈）不在本计划 scope。理由：(1) 与既有 auto-bind fail-fast 一致；(2) Minimum Rules #24 无静默跳过——denial 必须显式失败。

7. **无 DB schema 变更**。配额计数派生自 in-memory 状态（registry/manager 内存索引）+ config，非 DB 持久化。无新表、无新列、无 ORM XML 变更。（DB-backed `TeamAclEntry` / Fencing Token / 持久化配额计数器均为 successor。）理由：(1) 本计划维度计数可从既有 in-memory 索引派生；(2) 保持切片聚焦，不引入 DB 迁移负担。

8. **`QuotaConfig` 经 Nop 配置系统注入**（`@cfg:ai.agent.quota.*`，vision §5.2 "配额通过 Nop 配置系统管理"）。`QuotaConfig` 为不可变配置对象（构造期注入 DefaultResourceGuard），含 `teamMaxMembers`（默认 8）/ `tenantMaxConcurrentActors`（默认 10）。`<= 0` = unlimited。Delta 定制经既有 Nop 配置机制支持。理由：(1) 与 vision §5.2 配置管理一致；(2) 可测试性——构造期注入纯对象，测试用低 limit 验证 denial。

### Phase 1 - IResourceGuard 契约 + QuotaDimension/QuotaDecision/QuotaConfig + NoOp/Default 实现

Status: completed
Targets: `io.nop.ai.agent.quota` 包（新文件：`IResourceGuard` / `QuotaDimension` / `QuotaDecision` / `QuotaConfig` / `NoOpResourceGuard` / `DefaultResourceGuard`）

- Item Types: `Decision`、`Proof`

- [x] 定义 `IResourceGuard` 契约：配额决策入口（`checkConcurrent(QuotaDimension, String scopeKey, int projectedCount, int overrideLimit) → QuotaDecision`）。Javadoc 明确：overrideLimit `<= 0` = 用 QuotaConfig 全局默认，`> 0` = 用 override；projectedCount = 操作后 projected 计数；refer vision §5.2
- [x] 定义 `QuotaDimension` 枚举（in-scope 三值：`TEAM_PARALLEL_BOUND_MEMBERS` / `TEAM_MEMBERS` / `CONCURRENT_ACTORS_PER_TENANT`，Javadoc 标注其余 vision §5.2 维度为 successor）
- [x] 定义 `QuotaDecision` 不可变数据对象：`allowed: boolean` + `dimension` + `scopeKey` + `limit: int` + `projectedCount: int` + `reason: String`（nullable）+ 静态工厂 `allow()` / `deny(dimension, scopeKey, limit, projectedCount)`
- [x] 定义 `QuotaConfig` 不可变配置对象：`teamMaxMembers`（默认 8）/ `tenantMaxConcurrentActors`（默认 10），`<= 0` = unlimited
- [x] 实现 `NoOpResourceGuard`（singleton shipped 默认，全部 `checkConcurrent` 返回 `allow()`，Javadoc 明确零回归语义）
- [x] 实现 `DefaultResourceGuard`（持有 `QuotaConfig`，override `<= 0` 时查 QuotaConfig 默认，limit `<= 0` = unlimited allow，否则 projectedCount > limit → deny）
- [x] NoOp 为 shipped 默认（无 @InjectValue 强制要求，functional DefaultResourceGuard 由 integrator 经构造期注入 QuotaConfig 装配）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `io.nop.ai.agent.quota` 包下 6 个文件存在且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `NoOpResourceGuard.checkConcurrent(...)` 对全部三维度恒返回 allow（focused test：三维度各一断言 allowed==true）
- [x] `DefaultResourceGuard` denial 行为：limit `<= 0` → allow（unlimited）；projectedCount <= limit → allow；projectedCount > limit（limit > 0）→ deny（decision.allowed==false + reason 非空 + limit/projectedCount 字段正确，focused test 覆盖三维度 + override 优先于 config）
- [x] **无静默跳过**（Minimum Rules #24）：NoOp 显式返回 allow（零回归语义，非 placeholder bug）；Default 无 limit 时显式 allow（unlimited 语义），非吞掉
- [x] No owner-doc update required（vision §5.2 / roadmap 更新在 Phase 3）

### Phase 2 - enforcement 接线：TeamManager（maxParallelMembers + TEAM_MEMBERS）+ ActorRuntime（CONCURRENT_ACTORS_PER_TENANT）

Status: completed
Targets: `io.nop.ai.agent.team.InMemoryTeamManager` / `io.nop.ai.agent.team.DbTeamManager`（构造期可选 `IResourceGuard`，默认 NoOp）、`io.nop.ai.agent.runtime.InMemoryActorRuntime`（构造期可选 `IResourceGuard` + `ITenantResolver`，默认 NoOp/Null）

- Item Types: `Proof`

- [x] `InMemoryTeamManager` 构造期可选接收 `IResourceGuard`（默认 NoOp）。`bindMemberSession`（line 196）绑定前计数 `isBound()==true` 成员 → `checkConcurrent(TEAM_PARALLEL_BOUND_MEMBERS, teamId, boundCount+1, spec.maxParallelMembers)` → denial 抛 `NopAiAgentException`
- [x] `InMemoryTeamManager.createTeam`（projectedCount = spec 初始成员数）+ `addMember`（projectedCount = currentMemberCount+1）→ `checkConcurrent(TEAM_MEMBERS, teamId, projectedCount, 0)`（config-driven，override `<= 0`）→ denial 抛 `NopAiAgentException`
- [x] `DbTeamManager` 构造期可选接收 `IResourceGuard`（默认 NoOp）。`bindMemberSession`（line 486）绑定前计数 bound 成员（经既有 SELECT 重建逻辑或 COUNT 查询）+ 消费既有 `TeamRow.maxParallelMembers`（line 570/733）→ `checkConcurrent(TEAM_PARALLEL_BOUND_MEMBERS, ...)` → denial 抛 `NopAiAgentException`
- [x] `DbTeamManager.createTeam`（初始成员数）+ `addMember`（currentMemberCount+1）→ `checkConcurrent(TEAM_MEMBERS, ...)` → denial 抛 `NopAiAgentException`
- [x] `InMemoryActorRuntime` 构造期可选接收 `IResourceGuard`（默认 NoOp）+ `ITenantResolver`（默认 Null，复用既有解析）。`createActor` 创建前 resolve tenant scopeKey + 经 registry 派生该 tenant 活跃 Actor 计数 → `checkConcurrent(CONCURRENT_ACTORS_PER_TENANT, tenant, activeCount+1, 0)` → denial 抛 `NopAiAgentException`。仅在 `isEnabled()==true` + functional guard 时生效

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **NoOp 零回归 focused test**：`InMemoryTeamManager` / `DbTeamManager` / `InMemoryActorRuntime` 不注入 functional guard（NoOp 默认）→ bind/create/addMember 行为与当前完全一致（既有测试零回归）
- [x] **maxParallelMembers 强制 focused test**：`InMemoryTeamManager`（+ `DbTeamManager` 同 H2）注入 DefaultResourceGuard，team `maxParallelMembers=2` → 第 1/2 个 bindMemberSession 成功、第 3 个抛 `NopAiAgentException`（dimension=TEAM_PARALLEL_BOUND_MEMBERS，bound 计数仍为 2，Anti-Hollow 断言状态未越限）
- [x] **TEAM_MEMBERS 强制 focused test**：DefaultResourceGuard + QuotaConfig `teamMaxMembers=3` → createTeam（2 成员）成功、addMember（第 4 个，projectedCount=4 > 3）抛 `NopAiAgentException`
- [x] **CONCURRENT_ACTORS_PER_TENANT 强制 focused test**：DefaultResourceGuard + QuotaConfig `tenantMaxConcurrentActors=2` + `InMemoryActorRuntime.isEnabled()==true` + tenant-A context → createActor 第 1/2 成功、第 3 抛 `NopAiAgentException`；tenant-B 独立桶（互不影响，验证 per-tenant scope）
- [x] **接线验证**（Minimum Rules #23）：focused test 断言 enforcement point 在运行时确实调用 `IResourceGuard.checkConcurrent(...)`（如注入 spy/计数 guard 验证被调用 + denial 时抛异常，非仅类型存在）
- [x] **无静默跳过**（Minimum Rules #24）：denial 确实抛 `NopAiAgentException`（不静默 allow 越限）；NoOp 默认显式 allow（零回归语义）
- [x] `DbTeamManager.bindMemberSession` denial 经 H2 条件验证（不实际 UPDATE 越限行——denial 在 UPDATE 前抛出）
- [x] No owner-doc update required（vision §5.2 / roadmap 更新在 Phase 3）

### Phase 3 - 端到端验证 + 文档更新

Status: completed
Targets: 测试文件（E2E 配额强制 test）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`（§5.2 + §10 Phase 5）、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`（§4 / §10）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 编写端到端配额强制测试：同一 `DefaultAgentEngine` + functional `DefaultResourceGuard`（低 limit QuotaConfig）+ functional `InMemoryTeamManager` / `InMemoryActorRuntime`—— lead agent 声明 `<team maxParallelMembers="2">` 含 3 成员 → engine.execute lead 自动建团+绑定 lead → 第 2 个成员 auto-bind 成功 → 第 3 个成员 auto-bind 因 maxParallelMembers=2 抛 `NopAiAgentException`（engine.execute future 失败，绑定计数仍为 2）。验证三类维度至少一类经 engine.execute 入口点完整触发 denial
- [x] **端到端验证**（Minimum Rules #22）：从 engine.execute 入口点 → auto-bind → teamManager.bindMemberSession → IResourceGuard.checkConcurrent → denial 抛异常，完整路径验证（不只组件级单测）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §5.2：三类 count-based 并发配额维度（团队最大成员数 / 并发 Actor 数 / maxParallelMembers）标注 foundational 已落地（`IResourceGuard` 网关 + NoOp shipped 默认 + functional DefaultResourceGuard + TeamManager/ActorRuntime enforcement）；标注 LLM rate-limit / Compaction 配额池 / storage 配额 / per-agent token/时间 / Fencing Token 仍为 successor
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §10 Phase 5：foundational count-based 切片已交付（`IResourceGuard` 网关 + 三维度强制）；标注协调信道 + 时间窗 rate-limit + storage 仍为 successor
- [x] 更新 `nop-ai-agent-roadmap.md` §4（如涉及验收标准引用）/ §10 Phase 5 foundational 标注
- [x] `ai-dev/logs/` 对应日期条目更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 端到端配额强制测试断言通过（maxParallelMembers denial 经 engine.execute 完整触发 + bound 计数未越限）
- [x] **端到端验证**：engine.execute → auto-bind → bindMemberSession → guard.checkConcurrent → denial，完整链路验证
- [x] **接线验证**：E2E test 断言 functional `IResourceGuard.checkConcurrent` 在 engine 路径上确实被调用（非 NoOp 旁路）
- [x] `nop-ai-agent-actor-runtime-vision.md` §5.2 + §10 Phase 5 已更新（三维度 foundational 已落地 + successor 标注）
- [x] `nop-ai-agent-roadmap.md` §4/§10 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IResourceGuard` 契约 + `QuotaDimension`（三维度）+ `QuotaDecision` + `QuotaConfig` + `NoOpResourceGuard`（shipped 默认）+ `DefaultResourceGuard` 存在且编译通过
- [x] 三类维度 enforcement 接线完成（InMemoryTeamManager + DbTeamManager maxParallelMembers/TEAM_MEMBERS + InMemoryActorRuntime CONCURRENT_ACTORS_PER_TENANT）
- [x] `maxParallelMembers` 从 hint 升级为 enforced（`TeamSpec` Javadoc 更新 + InMemory/Db 双 impl 强制）
- [x] NoOp shipped 默认零回归（`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿，2460 tests）
- [x] 三类维度 denial 经 focused test + 端到端测试验证（denial 真发生、状态未越限、per-tenant scope 独立）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（LLM rate-limit / Compaction 配额池 / storage 配额 / per-agent token/时间 / Fencing Token / DB-backed ACL / permissions override / 协调信道 / User 隔离 / 排队语义 / DB 持久化配额计数器 均为显式 Non-Goals successor）
- [x] 受影响 owner docs 已同步（vision §5.2 + §10 Phase 5 + roadmap §4/§10）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）enforcement point → IResourceGuard.checkConcurrent → denial 调用链在运行时连通（不只 NoOp 类型存在），（b）denial 实际抛 NopAiAgentException 阻止越限操作（状态计数未越限），（c）NoOp 默认显式 allow（零回归语义，非吞掉），（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/234-nop-ai-agent-resource-guard-quota.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

## Deferred But Adjudicated

（暂无；LLM rate-limit / Compaction 配额池 / storage 配额 / per-agent token/时间 / Fencing Token / DB-backed ACL / permissions override / 协调信道 / User 隔离 / 排队语义 / DB 持久化配额计数器 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **LLM 调用频率 rate-limit**（vision §5.2 默认 100/min，`IRateLimiter`）：time-window / token-bucket。Classification: successor plan required（依赖 IRateLimiter 时间窗语义 + ReAct 循环 LLM 调用点接线 + engine 顶层 setResourceGuard 字段）。
- **Compaction LLM 调用独立配额池**（vision §5.2 默认 20/min）：Classification: successor（依赖 rate-limit 机制）。
- **storage 存储配额**（vision §5.1）：Classification: successor plan required（依赖 storage metrics 基础设施）。
- **单 Agent 最大 Token / 最大时间**（vision §5.2 默认 200K / 30min）：Classification: successor plan required（依赖 per-agent token 累积 + ICancelToken 时间预算）。单 Agent 最大迭代已部分由 `AgentExecutionContext.maxIterations` 满足。
- **Fencing Token**（vision §5.1）：Classification: successor plan required（独立 carry-over `L4-fencing-token`）。
- **DB-backed ACL 规则持久化（`TeamAclEntry` 表）**：Classification: successor plan required（独立 carry-over `L4-db-acl-persistence`）。
- **TeamSpec `permissions` override / 权限派生**：Classification: successor plan required（plan 228 follow-up）。
- **ResourceGuard 协调信道（scope_claim / conflict_alert）**：Classification: successor plan required（依赖协调协议裁定）。
- **User 隔离**：Classification: successor plan required（独立 carry-over `L4-user-isolation`）。
- **配额超限排队/等待语义**：Classification: optimization candidate（依赖背压/队列机制裁定）。
- **DB 持久化配额计数器 / 跨进程共享配额状态**：Classification: successor（依赖 DB-backed 配额存储裁定）。

## Closure

Status Note: foundational count-based 并发配额切片已完整交付——中央 `IResourceGuard` 配额决策网关 + `NoOpResourceGuard` shipped 默认（恒 allow = 零回归）+ `DefaultResourceGuard` 功能实现，对三类 count-based 并发配额维度强制（TEAM_PARALLEL_BOUND_MEMBERS / TEAM_MEMBERS / CONCURRENT_ACTORS_PER_TENANT）。denial fail-fast 抛 `NopAiAgentException`，端到端经 engine.execute → auto-bind → bindMemberSession → guard → denial 完整链路验证。全部 in-scope 项已落地，无 live defect 被静默降级。剩余维度（LLM rate-limit / storage / per-agent token/时间 / Fencing Token / 协调信道等）均为显式 Non-Goals successor。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（explore，fresh session，task `ses_12aabddbfffe55CvPuM6pTW06u`）
- Audit Session: `ses_12aabddbfffe55CvPuM6pTW06u`
- Evidence:
  - **Point 1 (contract surface)**: PASS — `quota/IResourceGuard.java` L72-73 (`checkConcurrent → QuotaDecision`)、`QuotaDimension.java` L50-73（恰好 3 值）、`QuotaDecision.java`（final + 私有 ctor + allow/deny 工厂 + allow⇒null reason/deny⇒requireNonNull 不变量）、`QuotaConfig.java` L32/35（默认 8/10）、`NoOpResourceGuard.java`（singleton + L46 显式 allow）、`DefaultResourceGuard.java` L67-85（override 优先/config 兜底/unlimited allow/projectedCount>limit deny）
  - **Point 2 (InMemoryTeamManager)**: PASS — 构造器 L95-97（默认 NoOp）+ createTeam TEAM_MEMBERS L125-126（注册前）+ addMember TEAM_MEMBERS L223-224（putIfAbsent 前）+ bindMemberSession TEAM_PARALLEL_BOUND_MEMBERS L283-292（isBound 计数 + idempotent rebind 不增 + spec.maxParallelMembers override）+ enforceQuota L105-114 denial 抛 NopAiAgentException
  - **Point 3 (DbTeamManager)**: PASS — 3-arg 构造器 L150-156 + createTeam L199-200（INSERT 前）+ addMember L463-465（INSERT 前）+ bindMemberSession L562-578（selectMemberRows 计数 + UPDATE L580 之前 denial，越限行不持久化）+ enforceQuota L162-170
  - **Point 4 (InMemoryActorRuntime)**: PASS — 5-arg 构造器 L205-215 + createActor L251-263（tenantResolver resolve + GLOBAL_SCOPE 哨兵 + getActiveActors().size() registry-tenant-filtered 计数 + register L276 之前 denial）+ idempotent 短路 L232-241 不调 guard
  - **Point 5 (No Silent No-Op #24)**: PASS — NoOp 显式 QuotaDecision.allow L46（非 null/非吞掉）+ denials 抛 NopAiAgentException 含 reason + 无空方法体/continue 跳过/吞异常
  - **Point 6 (Anti-Hollow #22/#23)**: PASS — `TestResourceGuardQuotaEndToEnd` 经 engine.execute L102/115/124 → auto-bind → bindMemberSession → guard denial（L127 assertFutureFails + L131-134 cause 含 NopAiAgentException + TEAM_PARALLEL_BOUND_MEMBERS + L138-141 bound 计数仍为 2 + L150-157 spy guard wasCalled + 含 deny decision）；focused tests（TestQuotaContract 17 / TestTeamManagerQuotaEnforcement 11 / TestActorRuntimeQuotaEnforcement 7）+ CountingResourceGuard spy 接线验证
  - **Point 7 (docs)**: PASS — vision §4.2 ResourceGuard 行 L204（successor→🟡 部分落地）+ §5.2 配额表 L275-277（三维度 ✅ foundational 已落地）+ §10 Phase 5 L460（空→🟡 部分落地）；roadmap §4 Layer 4 L257 新增 L4-resource-guard-quota ✅ 行
  - **Point 8 (maxParallelMembers hint→enforced)**: PASS — TeamSpec.java Javadoc L32-39/60-64/105-111 不再含 "does not enforce"，改为 "enforced when functional guard wired (plan 234); NoOp default unenforced"
  - **Anti-Hollow 检查**: enforcement point → IResourceGuard.checkConcurrent → denial 调用链在运行时连通（E2E + spy guard 验证）；denial 实际抛 NopAiAgentException 阻止越限操作（bound 计数/actor 计数未越限 Anti-Hollow 断言）；NoOp 默认显式 allow（零回归语义）；无空方法体/静默跳过
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/234-nop-ai-agent-resource-guard-quota.md --strict` 退出码为 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（2460 tests，零回归）
  - Deferred 项分类检查：全部 Non-Goals（LLM rate-limit / Compaction 配额池 / storage / per-agent token/时间 / Fencing Token / DB-backed ACL / permissions override / 协调信道 / User 隔离 / 排队语义 / DB 持久化配额计数器）均为显式 successor，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（全部 in-scope 项已落地）。剩余 non-blocking follow-up 见 Non-Blocking Follow-ups 段（均为显式 successor plan required，不在本计划 scope）。

## Follow-up handled by 235-nop-ai-agent-fencing-token.md

Non-Blocking Follow-up "Fencing Token（vision §5.1）：Classification: successor plan required（独立 carry-over `L4-fencing-token`）" 现由 `ai-dev/plans/235-nop-ai-agent-fencing-token.md` 接管。本节仅为可追溯链接，不修改本计划既有内容（Rule #20）。
