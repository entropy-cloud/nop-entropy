# 228 nop-ai-agent Team ACL 强制 — 角色权限矩阵 + ITeamAclChecker 拦截层

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-team-acl-enforcement

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/227-nop-ai-agent-team-task-update.md`（Non-Blocking Follow-ups 第一条：`Team ACL 强制（vision §5.1）：角色权限矩阵 + 权限派生 + team-task-update / team-task-create / team-send-message 的权限检查拦截。Classification: successor plan required`）+ `ai-dev/plans/225-nop-ai-agent-team-communication-tools.md`（Non-Goals：`Team ACL 强制（vision §5.1）...独立 successor plan required`）+ `ai-dev/plans/223-nop-ai-agent-team-manager.md`（Non-Goals：`Team ACL 强制（vision §5.1 TeamAclEntry / AclResource / AclAction）...独立 successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §5.1（Team ACL 模型：TeamAclEntry / AclResource / AclAction 枚举 + 默认 ACL 规则表 LEAD=ADMIN / MEMBER=READ+WRITE+EXECUTE + 权限派生）+ §4.2 Phase 表（line 446 引用 `ITeamAclProvider` 接口）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 256（"多用户可并发运行独立 Actor，租户间资源隔离"——Team ACL 强制是团队维度资源隔离的基础设施层）
> Related: `223`（交付 `ITeamManager` + `InMemoryTeamManager` + `MemberRole` 枚举 LEAD/MEMBER + `Team`/`TeamMember` 数据对象，本计划在其上新增 ACL 检查层）、`225`（交付 4 个团队通信 IToolExecutor + `AgentToolExecuteContext` teamManager/teamTaskStore 接线，本计划在其 context 新增 teamAclChecker 字段 + 4 个工具集成 ACL 检查）、`227`（交付 team-task-update 状态机 + DbTeamTaskStore 共享任务表，本计划为其 abandon 动作新增角色区分）

## Purpose

把 nop-ai-agent 的团队工具授权从"无 ACL——任何绑定了团队的成员可对所在团队执行全部 4 个团队工具（team-send-message / team-status / team-task-create / team-task-update）的全部操作，无角色区分、无权限拦截层"扩展为"经可插拔 `ITeamAclChecker` 在每次团队工具操作前裁定调用者角色（LEAD / MEMBER）并按 vision §5.1 默认权限矩阵授权——LEAD 拥有 ADMIN（全部操作）、MEMBER 拥有 READ+WRITE+EXECUTE（全部协作操作，但放弃未被认领的任务 = ADMIN-only 管理操作被拒绝）"。本计划只负责这一件事：为 4 个团队工具建立角色权限矩阵 + 拦截层基础设施，闭合 plan 223/225/227 显式切出的 Team ACL successor gap。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **`MemberRole` 枚举已存在**（plan 223 ✅）：`LEAD` / `MEMBER`，Javadoc 明确"identity tag only, does not enforce any permission. Team ACL enforcement is a successor (vision §5.1)"。`io.nop.ai.agent.team.MemberRole`。本计划将该 identity tag 升级为权限矩阵驱动维度。
- **`TeamMember` 携带 role + sessionId**（plan 223 ✅）：`memberName` / `role(MemberRole)` / `joinedAt`（immutable）+ `sessionId` / `actorId`（mutable binding）。`getRole()` / `getSessionId()` 已存在。`io.nop.ai.agent.team.TeamMember`。ACL 角色解析经 sessionId 反查 member → role。
- **`Team.getMembers()` 返回 memberName → TeamMember 映射**（plan 223 ✅）：`io.nop.ai.agent.team.Team:89`。ACL 检查器遍历此映射按 sessionId 匹配定位调用者角色。
- **`ITeamManager` 契约已存在**（plan 223 ✅）：`getTeam(teamId)` / `getTeamBySession(sessionId)` / `getMember(teamId, memberName)` 等查询方法。`io.nop.ai.agent.team.ITeamManager:70,79,145`。ACL 检查器经 `getTeam` 获取团队 + 遍历 members 解析调用者角色。
- **4 个团队工具 IToolExecutor 均遵循同一模式**（plan 225/227 ✅）：
  1. cast context → `AgentToolExecuteContext`
  2. NoOp 短路（teamManager/taskStore 为 NoOp/null 时诚实报告）
  3. `agentCtx.getSessionId()` 取 callerSessionId
  4. `teamManager.getTeamBySession(callerSessionId)` 解析调用者团队
  5. 执行操作（store / messenger）
  
  **当前步骤 4 与 5 之间无任何权限检查**——任何绑定了团队的成员均可执行全部操作。各工具具体位置：
  - `TeamSendMessageExecutor`（`io.nop.ai.agent.tool.TeamSendMessageExecutor:96-102` 团队解析 → `:104+` 参数解析 / 成员查找 / 消息投递）
  - `TeamStatusExecutor`（`:89-95` 团队解析 → `:98+` taskStore 查询 / 状态构建）
  - `TeamTaskCreateExecutor`（`:106-112` 团队解析 → `:114+` 参数解析 / 任务创建）
  - `TeamTaskUpdateExecutor`（`:112-118` 团队解析 → `:120-145` 参数解析 / 任务加载 / 跨团队校验 → `:148-162` action 分发 / store 调用）
- **`AgentToolExecuteContext` 已携带 teamManager + teamTaskStore**（plan 225 ✅）：`teamManager` / `teamTaskStore` final 字段（`:50-51`）+ 全参构造（`:222-254`）+ getter（`:362-375`）。**本计划新增 `teamAclChecker` 字段**，遵循同一模式。
- **`AgentToolExecuteContext` 生产代码仅有一个构造路径**（grep `src/main` 确认）：`ReActAgentExecutor.java:1543` 唯一生产 `new AgentToolExecuteContext(...)` 调用点（`:1543-1559` 传 teamManager/teamTaskStore）。测试代码有 5 个文件直接调用当前 16 参 endpoint 构造器（`TestTeamSendMessageExecutor` / `TestTeamStatusExecutor` / `TestTeamTaskCreateExecutor` / `TestTeamTaskUpdateExecutor` / `TestTeamTaskUpdateEndToEnd`）。该 context 有 6 个构造器重载形成委托链（`:53` → `:81` → `:117` → `:146` → `:179` → `:222`）。本计划新增 17 参 endpoint 构造器，既有 16 参构造器委托到 17 参（传 `NoOpTeamAclChecker.noOp()`），生产调用点 `:1543` 改用 17 参构造器传 engine 的 teamAclChecker，5 个测试调用点零修改（经 16→17 委托获得 NoOp）。
- **`DefaultAgentEngine` teamManager/teamTaskStore 接线模式已确立**（plan 225 ✅）：`teamManager` 字段（`:288`，默认 `NoOpTeamManager.noOp()`）+ `setTeamManager`（`:1301-1302`，null-safe 回退 NoOp）+ `resolveExecutor` 经 `ReActAgentExecutor.Builder` 传递（`:2504-2505`）。`teamTaskStore` 同（`:299` / `:1326-1327`）。本计划新增 `teamAclChecker` 字段 + setter，镜像此模式。
- **`ReActAgentExecutor` teamManager/teamTaskStore 传递链已确立**（plan 225 ✅）：字段（`:304-305`）+ 构造参数（`:344-345`）+ 赋值（`:408-409`）+ Builder 字段（`:456-457`）+ Builder 方法（`:872-873` / `:885-886`）+ Builder.build() 传递（`:940-941`）+ context 构造传递（`:1558-1559`）。本计划追加 teamAclChecker，镜像此全链路。
- **既有权限基础设施（不同关注点，不直接复用）**：
  - `IToolAccessChecker`（`io.nop.ai.agent.security.IToolAccessChecker`）：`checkAccess(toolName, AgentExecutionContext) → ToolAccessResult`——通用工具访问检查（allowedTools 集合），不含团队角色语义。
  - `IPermissionProvider`（`io.nop.ai.agent.security.IPermissionProvider`）：`resolve(toolName, agentName, sessionId) → Permission`——per-tool 权限解析，不含团队角色语义。
  - `IPermissionMatrix`（`io.nop.ai.agent.security.IPermissionMatrix`）：`check(ChannelKind, Principal, SecurityLevel) → MatrixDecision`——通道 × 安全级别矩阵（Layer 2），与团队角色 ACL 是不同维度。
  
  本计划新建 `ITeamAclChecker`（团队维度 ACL），与上述 3 个接口并存，各管不同授权维度。
- **零 Team ACL 代码**：grep `TeamAcl|ITeamAclChecker|TeamAclChecker|TeamAclDecision|TeamAclAction|NoOpTeamAcl|DefaultTeamAcl` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 命中。
- **vision §5.1 Team ACL 模型**：定义 `TeamAclEntry`(teamId + actorRole + resource + actions) + `AclResource` 枚举(SESSION/PLAN/TOOL_EXECUTION/FILE_SCOPE/MESSAGE_CHANNEL) + `AclAction` 枚举(READ/WRITE/EXECUTE/ADMIN) + 默认 ACL 规则表(LEAD=ADMIN / MEMBER=READ+WRITE+EXECUTE) + 权限派生（子 Actor 继承父 ACL）。本计划裁定首版交付团队工具维度的角色权限矩阵 + 拦截层（§5.1 模型的功能性子集），完整 AclResource 枚举 / 权限派生 / TeamSpec permissions override 为独立 successor。

## Goals

- **`ITeamAclChecker` 契约**（新接口）：`TeamAclDecision checkAccess(String teamId, String callerSessionId, String toolName, String action)`。`action` 为操作动词字符串（team-send-message → `"send"`、team-status → `"view"`、team-task-create → `"create"`、team-task-update → `"claim"` / `"complete"` / `"abandon-claimed"` / `"abandon-unclaimed"`）。返回 `TeamAclDecision`（allowed + reason + resolvedRole）。
- **`TeamAclAction` 枚举**（新文件）：`READ` / `WRITE` / `EXECUTE` / `ADMIN`，对齐 vision §5.1 `AclAction` 定义。
- **`TeamAclDecision` 数据对象**（新文件）：不可变，`allowed`(boolean) + `reason`(String，allowed 时为 null) + `resolvedRole`(MemberRole，未解析到成员时为 null)。工厂方法 `allow(MemberRole)` / `deny(MemberRole, String reason)`。
- **`NoOpTeamAclChecker` shipped 默认**（新文件）：`checkAccess` 恒返回 `allow(null)`（零回归——NoOp 下所有团队工具操作不增加任何授权开销，行为与当前完全一致）。
- **`DefaultTeamAclChecker` 功能实现**（新文件）：构造期接收 `ITeamManager` 引用。`checkAccess` 流程：(1) `teamManager.getTeam(teamId)` 取团队（不存在 → deny(null, "team not found")）；(2) 遍历 `team.getMembers()` 按 sessionId 匹配定位调用者（未匹配 → deny(null, "caller is not a member of this team")）；(3) 取 `member.getRole()`；(4) 按 (toolName, action) → required `TeamAclAction` 映射（裁定 1 矩阵）；(5) 校验角色是否持有该 action（LEAD 恒 ADMIN 通过全部；MEMBER 通过 READ/WRITE/EXECUTE 但不通过 ADMIN）；(6) allow 或 deny。
- **角色权限矩阵（裁定 1）**：

  | toolName | action | Required AclAction | LEAD(=ADMIN) | MEMBER(=R+W+E) |
  |---|---|---|---|---|
  | team-send-message | send | WRITE | ✓ | ✓ |
  | team-status | view | READ | ✓ | ✓ |
  | team-task-create | create | WRITE | ✓ | ✓ |
  | team-task-update | claim | EXECUTE | ✓ | ✓ |
  | team-task-update | complete | EXECUTE | ✓ | ✓ |
  | team-task-update | abandon-claimed | EXECUTE | ✓ | ✓ |
  | team-task-update | abandon-unclaimed | ADMIN | ✓ | ✗ DENY |

  **唯一 MEMBER 被拒绝的操作**：放弃未被认领的任务（CREATED → ABANDONED，裁定 2）。其余全部协作操作对 MEMBER 开放。ADMIN 维度的区分（LEAD 独有）为后续 ADMIN-only 团队管理操作（disband / 成员管理 / 权限 override 工具）预留矩阵基础设施。

- **`AgentToolExecuteContext` 扩展**：新增 `teamAclChecker`(ITeamAclChecker) final 字段 + 新增 17 参 endpoint 构造器（既有 16 参 endpoint 构造器 `:222-254` **改为委托**到新 17 参构造器，传 `NoOpTeamAclChecker.noOp()` 作为 teamAclChecker 默认值——既有全部调用点零修改）+ `getTeamAclChecker()` getter。此策略镜像 plan 225 引入 teamManager/teamTaskStore 时的做法（当时新增 16 参构造器，既有 14 参委托到 16 参传 null/null）。
- **`DefaultAgentEngine` 扩展**：新增 `teamAclChecker` 字段（默认 `NoOpTeamAclChecker.noOp()`）+ `setTeamAclChecker`（null-safe 回退 NoOp）+ `resolveExecutor` 经 Builder 传递。
- **`ReActAgentExecutor` 扩展**：新增 `teamAclChecker` 字段 + 构造参数 + Builder 字段 + Builder 方法 + `AgentToolExecuteContext` 构造传递（全链路镜像 teamManager 模式）。
- **4 个团队工具集成 ACL 检查**：每个工具在团队解析之后、实际操作之前，调用 `agentCtx.getTeamAclChecker().checkAccess(teamId, callerSessionId, toolName, action)`。denial → 诚实错误结果（status="success" + body JSON 说明拒绝原因——不中断 ReAct 循环，与 CAS 失败诚实报告一致）。team-task-update 的 abandon 动作在任务加载后按当前 status 区分 abandon-claimed / abandon-unclaimed 再检查。
- **NoOp shipped 默认零回归**：NoOpTeamAclChecker 恒 allow → 既有全量测试零回归（行为不变）。
- **端到端验证**（Anti-Hollow #22）：(a) LEAD agent 执行全部 4 工具全部操作 → 全部成功；(b) MEMBER agent 执行全部协作操作 → 成功，但 abandon 未认领任务 → 诚实拒绝；(c) DefaultTeamAclChecker 下非团队成员 session 调用 → 诚实拒绝；(d) NoOp 默认配置下既有全量测试零回归。
- vision §5.1 Team ACL 模型标注 foundational 拦截层已落地（角色矩阵 + ITeamAclChecker + 4 工具集成）；roadmap §4 Layer 4 验收标准"租户间资源隔离"标注团队维度 ACL 基础设施已交付（完整多租户 tenantId/userId 隔离仍为 successor）。

## Non-Goals

- **TeamSpec `permissions` override**（vision §5.1 "LEAD 可以通过 permissions override 收紧成员权限，只能收紧不能放款"）：`TeamSpec` 当前无 permissions 字段（`io.nop.ai.agent.team.TeamSpec`，5 个字段：teamName/description/leadAgentName/memberSpecs/maxParallelMembers）。本计划 `DefaultTeamAclChecker` 使用硬编码 §5.1 默认矩阵，不支持 per-team/per-member override。Classification: successor plan required（依赖 TeamSpec 扩展 + override 裁定）。
- **权限派生（vision §5.1 "子 Actor 继承父 Actor 的 ACL 规则"）**：父子 Actor 层级间的 ACL 继承。本计划 ACL 仅在单一团队上下文内按角色授权（调用者 → 其所在团队 → 其角色），不涉及跨 Actor 层级继承。Classification: successor plan required（属完整 §5.1 ACL 模型的 SESSION/PLAN 资源维度）。
- **完整 `AclResource` 枚举（SESSION / PLAN / TOOL_EXECUTION / FILE_SCOPE / MESSAGE_CHANNEL）**：vision §5.1 定义 5 种资源的 × action 矩阵。本计划仅覆盖 4 个团队工具的操作维度（toolName + action → required AclAction），不建模完整资源枚举。Classification: successor plan required（当 ACL 扩展到 session 访问 / plan 修改 / 文件 scope 等资源时引入）。
- **多租户 tenantId/userId 隔离**（vision §5.1 "Tenant 隔离 / User 隔离"）：不同租户的 Actor 完全不可见、DB 查询自动加 tenantId 条件。本计划 ACL 仅在团队维度授权，不涉及租户隔离。Classification: successor plan required（独立 carry-over `L4-multi-tenant-isolation`，依赖 AgentExecutionContext tenant 标识标准化）。
- **DB-backed ACL 规则持久化**：`TeamAclEntry` 表 + 跨进程共享 ACL 规则。本计划矩阵为硬编码默认（DefaultTeamAclChecker 构造期内建）。Classification: successor plan required。
- **Fencing Token**（vision §5.1 monotonic counter 并发写入防护）：属 ResourceGuard / Phase 5 范畴。Classification: successor plan required。
- **ResourceGuard + 配额强制**（vision §5.2 maxParallelMembers 等）：Classification: successor plan required。
- **通用工具访问检查（`IToolAccessChecker`）集成团队 ACL**：本计划团队 ACL 检查内嵌于 4 个团队工具 executor（与现有 team resolution 同一位置），不改变 `IToolAccessChecker` / `IPermissionProvider` / `IPermissionMatrix` 的现有调度路径。将团队 ACL 统一到通用工具检查管线是后续架构 successor。
- **DB-backed 团队（team）持久化 / 自动团队绑定 / TeamSpec XDSL / 跨进程团队消息路由**：均为 plan 223/225 显式 successor，本计划不触及。

## Scope

### In Scope

- `io.nop.ai.agent.team` 包（新文件）：
  - `TeamAclAction.java` — 枚举（READ / WRITE / EXECUTE / ADMIN）
  - `TeamAclDecision.java` — 不可变数据对象（allowed + reason + resolvedRole）+ 工厂方法
  - `ITeamAclChecker.java` — 接口（checkAccess(teamId, callerSessionId, toolName, action)）
  - `NoOpTeamAclChecker.java` — shipped 默认（恒 allow）
  - `DefaultTeamAclChecker.java` — 功能实现（构造期接收 ITeamManager + 内建 §5.1 矩阵 + 角色解析）
- `io.nop.ai.agent.engine` 包（扩展）：
  - `AgentToolExecuteContext.java` — 新增 teamAclChecker final 字段 + 全参构造追加 + getter；既有构造器链向后兼容
  - `DefaultAgentEngine.java` — 新增 teamAclChecker 字段（默认 NoOp）+ setTeamAclChecker + resolveExecutor 传递
  - `ReActAgentExecutor.java` — 新增 teamAclChecker 字段 + 构造参数 + Builder 全链路 + context 构造传递
- `io.nop.ai.agent.tool` 包（扩展 4 个 executor）：
  - `TeamSendMessageExecutor.java` — 团队解析后插入 ACL 检查（action="send"）
  - `TeamStatusExecutor.java` — 团队解析后插入 ACL 检查（action="view"）
  - `TeamTaskCreateExecutor.java` — 团队解析后插入 ACL 检查（action="create"）
  - `TeamTaskUpdateExecutor.java` — 任务加载后插入 ACL 检查（claim/complete → 直接 action；abandon → 按当前 status 区分 abandon-claimed/abandon-unclaimed）
- 测试文件：
  - `TestTeamAclDecision.java` — 数据对象工厂方法 + 不可变
  - `TestDefaultTeamAclChecker.java` — 角色解析（LEAD/MEMBER/非成员）+ 矩阵全部 7 个 (toolName, action) 组合 + deny reason + team 不存在
  - `TestNoOpTeamAclChecker.java` — 恒 allow + 零开销
  - `TestTeamSendMessageExecutorAcl.java` — MEMBER send 允许 / 非成员拒绝 / NoOp allow
  - `TestTeamStatusExecutorAcl.java` — MEMBER view 允许 / 非成员拒绝 / NoOp allow
  - `TestTeamTaskCreateExecutorAcl.java` — MEMBER create 允许 / 非成员拒绝 / NoOp allow
  - `TestTeamTaskUpdateExecutorAcl.java` — MEMBER claim/complete/abandon-claimed 允许 / MEMBER abandon-unclaimed 拒绝 / LEAD abandon-unclaimed 允许 / 非成员拒绝 / NoOp allow
  - `TestTeamAclEndToEnd.java` — 端到端（DefaultAgentEngine + InMemoryTeamManager + DefaultTeamAclChecker + mock LLM：LEAD 全通过 / MEMBER abandon-unclaimed 拒绝 / NoOp 零回归）

### Out Of Scope

- `TeamSpec` permissions 字段（Non-Goal: permissions override）
- `AclResource` 枚举（Non-Goal: 完整资源矩阵）
- 权限派生 / 父子 Actor ACL 继承（Non-Goal: 权限派生）
- tenantId/userId 隔离（Non-Goal: 多租户隔离）
- `TeamAclEntry` DB 表（Non-Goal: DB 持久化）
- Fencing Token / ResourceGuard（Non-Goal）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **ACL 契约签名 = `(teamId, callerSessionId, toolName, action)` 简单字符串四元组**。不引入 `TeamAclRequest` 值对象或 `AclResource` 枚举——foundational slice 的 4 个团队工具操作可经 toolName + action 字符串完全表达。`action` 取值：team-send-message→`"send"`、team-status→`"view"`、team-task-create→`"create"`、team-task-update→`"claim"`/`"complete"`/`"abandon-claimed"`/`"abandon-unclaimed"`。理由：(1) 与既有团队工具的参数解析模式（`getStringArg` + 字符串分发）一致；(2) `DefaultTeamAclChecker` 内部经静态映射 `(toolName, action) → TeamAclAction` 查表，无需值对象开销；(3) 未来引入 `AclResource` 枚举时，映射层升级为 `(AclResource, AclAction)`，接口签名不变（checker 内部升级）。team-task-update 的 abandon 按 task 当前 status 区分 claimed/unclaimed 两个 action，使矩阵能表达"放弃未认领任务 = ADMIN-only"这一管理操作区分。

2. **唯一 MEMBER 被拒绝的操作 = abandon-unclaimed（放弃 CREATED 状态的未认领任务）**。`abandonTask` store 方法允许 `CLAIMED→ABANDONED` 和 `CREATED→ABANDONED` 两种转换（plan 227 裁定 2）。ACL 矩阵裁定：放弃已被认领的任务（CLAIMED→ABANDONED，action=`abandon-claimed`，required=EXECUTE）对 MEMBER 开放（成员放弃自己认领的任务是正常协作）；放弃未被认领的任务（CREATED→ABANDONED，action=`abandon-unclaimed`，required=ADMIN）仅 LEAD 可执行（从任务池移除未开始的工作是团队管理决策）。理由：(1) 给 foundational ACL 一个具体可测的角色区分语义（LEAD vs MEMBER 有实际不同的授权结果），而非纯基础设施空转；(2) 从任务池移除无人认领的任务影响全团队工作分配，属管理操作；(3) MEMBER 放弃自己认领的任务不影响他人（任务已被认领，其他成员本就无法认领）。team-task-update executor 在任务加载后（`taskStore.getTask` 返回后、action switch 前）按 `current.getStatus()` 选择 abandon-claimed / abandon-unclaimed 传给 checker。

3. **DefaultTeamAclChecker 角色解析 = 构造期接收 ITeamManager + 运行时 getTeam + 遍历 members 按 sessionId 匹配**。checker 不自带团队状态缓存——每次 checkAccess 经 `teamManager.getTeam(teamId)` 取最新团队快照 + 遍历 `team.getMembers().values()` 匹配 `member.getSessionId().equals(callerSessionId)`。理由：(1) 与 `InMemoryTeamManager` 的 ConcurrentHashMap 快照语义一致（getTeam 返回当前快照）；(2) 避免 checker 与 manager 状态同步问题；(3) sessionId 在成员绑定后不变（`TeamMember.bind` 后稳定），匹配可靠。若团队不存在 → deny(null, "team not found")。若 session 未绑定到该团队任何成员 → deny(null, "caller session is not a member of team")。

4. **NoOpTeamAclChecker 恒 allow(null) 而非恒 deny**。shipped 默认必须零回归——NoOp 下 4 个团队工具的行为必须与当前完全一致（全部放行）。allow(null) 表示"无角色信息，放行"（resolvedRole=null）。这与 `NoOpTeamManager`（写操作抛 UOE）/ `NoOpTeamTaskStore`（转换抛 UOE）的语义不同：ACL 的 NoOp 是"不启用 ACL = 不增加授权限制"，而非"不启用 ACL = 全部拒绝"。功能性 teamManager + NoOp ACL 的组合 = 团队功能启用但无角色授权限制（向后兼容当前行为）。

5. **ACL 检查插入位置 = 团队解析之后、实际操作之前；team-task-update 在任务加载之后**。对于 team-send-message / team-status / team-task-create：在 `getTeamBySession` 返回团队之后、参数解析/操作之前插入检查。对于 team-task-update：在 `getTask` 返回 + 跨团队校验之后、action switch 之前插入检查（abandon 需要当前 task status 决定 action 字符串）。理由：(1) ACL 需要 teamId（来自团队解析）；(2) team-task-update 的 abandon 需要当前 task status（来自任务加载）；(3) 在 NoOp 短路之后插入——NoOp teamManager 下根本不进入 ACL 检查（与当前行为一致）。

6. **denial 结果 = 诚实错误（status="success" + JSON body），非异常**。ACL denial 不中断 ReAct 循环——返回 `AiToolCallResult`(status="success", output.body=JSON {allowed:false, reason:"...", resolvedRole:"MEMBER"})，让 LLM 看到策略反馈并调整行为（与 team-task-update CAS 失败的 `honestCasResult` 模式一致）。理由：(1) 工具调用被授权拒绝不是系统错误（error status 会让 LLM 认为是技术故障），而是策略反馈（LLM 应理解"我没有权限做这个"并寻求替代方案）；(2) 与既有诚实报告模式一致（NoOp honestNotEnabled / CAS honestCasResult）。

7. **不修改 `ai-agent-tools.beans.xml`**。`ITeamAclChecker` 是引擎级组件（与 ITeamManager 同层），经 `DefaultAgentEngine.setTeamAclChecker` 程序化注入，不是工具 bean。4 个团队工具 executor 从 context 读取 checker，不直接持有 checker 引用。beans.xml 无变更。

### Phase 1 - ITeamAclChecker 契约 + DefaultTeamAclChecker + NoOp + 引擎接线

Status: completed
Targets: `io.nop.ai.agent.team`（5 个新文件）、`io.nop.ai.agent.engine`（AgentToolExecuteContext / DefaultAgentEngine / ReActAgentExecutor 扩展）

- Item Types: `Fix`（Team ACL gap = plan 223/225/227 carry-over）、`Proof`

- [x] 新建 `TeamAclAction` 枚举（READ / WRITE / EXECUTE / ADMIN），Javadoc 引用 vision §5.1 AclAction 定义 + 各值语义
- [x] 新建 `TeamAclDecision` 不可变数据对象：`allowed`(boolean) + `reason`(String) + `resolvedRole`(MemberRole)；工厂方法 `allow(MemberRole role)` + `deny(MemberRole role, String reason)`；getter `isAllowed()` / `getReason()` / `getResolvedRole()`
- [x] 新建 `ITeamAclChecker` 接口：`TeamAclDecision checkAccess(String teamId, String callerSessionId, String toolName, String action)`，Javadoc 明确参数语义 + allow/deny 控制流
- [x] 新建 `NoOpTeamAclChecker`：`checkAccess` 恒返回 `TeamAclDecision.allow(null)`，Javadoc 明确"shipped 默认，零回归，不增加授权限制"（裁定 4）；提供 `NoOpTeamAclChecker.noOp()` 单例工厂（与 `NoOpTeamManager.noOp()` / `NoOpTeamTaskStore.noOp()` 模式一致）
- [x] 新建 `DefaultTeamAclChecker`：构造期接收 `ITeamManager`（non-null）；`checkAccess` 实现裁定 3 角色解析（getTeam + 遍历 members 按 sessionId 匹配）+ 裁定 1 矩阵查表（静态 `Map<(toolName, action), TeamAclAction>`）+ LEAD 恒通过 / MEMBER 校验非 ADMIN；deny 携带 reason + resolvedRole
- [x] `AgentToolExecuteContext` 新增 `teamAclChecker`(ITeamAclChecker) final 字段 + 新增 17 参 endpoint 构造器（既有 16 参 endpoint 构造器 `:222-254` **改为委托**到新 17 参构造器，传 `NoOpTeamAclChecker.noOp()` 作 teamAclChecker——既有 5 个直接调用 16 参构造器的测试文件 `TestTeamSendMessageExecutor` / `TestTeamStatusExecutor` / `TestTeamTaskCreateExecutor` / `TestTeamTaskUpdateExecutor` / `TestTeamTaskUpdateEndToEnd` **零修改**，经委托获得 NoOp checker = 零回归）+ `getTeamAclChecker()` getter
- [x] `DefaultAgentEngine` 新增 `teamAclChecker` 字段（默认 `NoOpTeamAclChecker.noOp()`）+ `setTeamAclChecker(ITeamAclChecker)`（null-safe 回退 NoOp，镜像 `setTeamManager` `:1301-1302`）+ `getTeamAclChecker()`；`resolveExecutor` 经 `ReActAgentExecutor.Builder.teamAclChecker(this.teamAclChecker)` 传递（镜像 `:2504-2505`）
- [x] `ReActAgentExecutor` 新增 `teamAclChecker` final 字段（`:304-305` 旁）+ 构造参数（`:344-345` 旁）+ 赋值（`:408-409` 旁）+ Builder 字段（`:456-457` 旁）+ Builder 方法 `teamAclChecker(ITeamAclChecker)`（`:872-886` 旁）+ Builder.build() 传递（`:940-941` 旁）+ `AgentToolExecuteContext` 构造（`:1558-1559` 旁）追加 teamAclChecker
- [x] 编写 `TestTeamAclDecision`：allow/deny 工厂方法 + 字段 + 不可变（allow 时 reason 为 null / deny 时 reason 非 null）
- [x] 编写 `TestDefaultTeamAclChecker`：(a) LEAD 全部 7 个 (toolName, action) 组合 allow；(b) MEMBER 6 个非 ADMIN 组合 allow；(c) MEMBER abandon-unclaimed deny + reason 含 ADMIN；(d) caller session 未绑定到团队任何成员 deny；(e) teamId 不存在 deny；(f) resolvedRole 在 allow/deny 中正确记录
- [x] 编写 `TestNoOpTeamAclChecker`：恒 allow + resolvedRole=null + noOp() 单例

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TeamAclAction.java` 存在于 `io.nop.ai.agent.team` 包，含 READ/WRITE/EXECUTE/ADMIN 4 值
- [x] `TeamAclDecision.java` 存在，含 allow/deny 工厂方法 + isAllowed/getReason/getResolvedRole
- [x] `ITeamAclChecker.java` 存在，含 checkAccess(teamId, callerSessionId, toolName, action) 方法
- [x] `NoOpTeamAclChecker.java` 存在，checkAccess 恒 allow(null)（**无静默跳过** #24——NoOp 明确放行非空方法体），含 noOp() 单例工厂
- [x] `DefaultTeamAclChecker.java` 存在，含角色解析 + 矩阵查表 + deny reason 真实逻辑（非空壳）
- [x] `AgentToolExecuteContext` 含 teamAclChecker final 字段 + getter；既有构造器链编译通过（向后兼容）
- [x] `DefaultAgentEngine` 含 teamAclChecker 字段（默认 NoOp）+ setTeamAclChecker（null-safe）+ resolveExecutor 传递
- [x] `ReActAgentExecutor` 含 teamAclChecker 全链路（字段→构造→Builder→context 构造）
- [x] **接线验证**（Minimum Rules #23）：`TestDefaultTeamAclChecker` 断言 checker 经构造期注入的 ITeamManager.getTeam 能解析到团队成员角色（非 mock 空转）
- [x] **无静默跳过**（Minimum Rules #24）：NoOpTeamAclChecker 显式 allow（非空方法体 / 非 continue 跳过）；DefaultTeamAclChecker deny 时返回含 reason 的 TeamAclDecision（非吞掉返回 null）
- [x] `TestTeamAclDecision` + `TestDefaultTeamAclChecker` + `TestNoOpTeamAclChecker` 全绿
- [x] No owner-doc update required（owner doc 更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 4 个团队工具 executor 集成 ACL 检查

Status: completed
Targets: `io.nop.ai.agent.tool`（TeamSendMessageExecutor / TeamStatusExecutor / TeamTaskCreateExecutor / TeamTaskUpdateExecutor 扩展）

- Item Types: `Fix`（团队工具授权 gap）、`Proof`

- [x] `TeamSendMessageExecutor` 在团队解析后（`:102` 之后）、参数解析前插入：`TeamAclDecision d = agentCtx.getTeamAclChecker().checkAccess(team.getTeamId(), callerSessionId, "team-send-message", "send")`；`!d.isAllowed()` → 诚实拒绝结果（裁定 6 JSON：{allowed:false, reason, resolvedRole}）
- [x] `TeamStatusExecutor` 在团队解析后（`:95` 之后）、taskStore 查询前插入 ACL 检查（toolName="team-status", action="view"）
- [x] `TeamTaskCreateExecutor` 在团队解析后（`:112` 之后）、参数解析前插入 ACL 检查（toolName="team-task-create", action="create"）
- [x] `TeamTaskUpdateExecutor` 在任务加载 + 跨团队校验后（`:145` 之后）、action switch 前（`:148` 之前）插入 ACL 检查：action 解析时若 action="abandon"，按 `current.getStatus()` 区分——CLAIMED → action="abandon-claimed"，CREATED → action="abandon-unclaimed"；claim → "claim"、complete → "complete"；调 `checkAccess(team.getTeamId(), callerSessionId, "team-task-update", refinedAction)`
- [x] 每个工具新增 `honestDenied(int callId, TeamAclDecision decision)` 辅助方法（或复用既有 honestCasResult 模式），返回 status="success" + body JSON {allowed:false, reason, resolvedRole, toolName, action}
- [x] 编写 `TestTeamSendMessageExecutorAcl`：(a) DefaultTeamAclChecker 下 MEMBER send 允许（正常投递）；(b) 非成员 session（未绑定到团队）拒绝；(c) NoOp checker 下允许（零回归）
- [x] 编写 `TestTeamStatusExecutorAcl`：(a) MEMBER view 允许（正常返回状态 JSON）；(b) 非成员拒绝；(c) NoOp 允许
- [x] 编写 `TestTeamTaskCreateExecutorAcl`：(a) MEMBER create 允许（正常创建）；(b) 非成员拒绝；(c) NoOp 允许
- [x] 编写 `TestTeamTaskUpdateExecutorAcl`：(a) MEMBER claim 允许；(b) MEMBER complete 允许；(c) MEMBER abandon CLAIMED 任务允许（abandon-claimed, EXECUTE）；(d) MEMBER abandon CREATED 任务拒绝（abandon-unclaimed, ADMIN）+ reason 含权限说明；(e) LEAD abandon CREATED 任务允许；(f) 非成员拒绝；(g) NoOp 允许全部

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 4 个 executor 均在团队解析后、实际操作前调用 `agentCtx.getTeamAclChecker().checkAccess(...)`
- [x] team-task-update abandon 按当前 task status 区分 abandon-claimed / abandon-unclaimed 传给 checker
- [x] denial 返回诚实结果（status="success" + JSON body {allowed:false, reason, ...}），**非异常**（裁定 6）
- [x] **无静默跳过**（Minimum Rules #24）：ACL denial 不返回正常成功结果假装通过（honestDenied body 明确 allowed:false）；NoOp checker 下不跳过检查代码而是经 checker 返回 allow 后正常执行
- [x] **接线验证**（Minimum Rules #23）：focused 测试断言 executor 经 `agentCtx.getTeamAclChecker()` 访问到功能性 DefaultTeamAclChecker（非 NoOp），且 denial 实际阻止了 store/messenger 操作（非仅返回拒绝但操作仍执行）
- [x] 4 个 ACL focused 测试类全绿
- [x] No owner-doc update required（owner doc 更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证 + 设计文档同步 + roadmap 升级

Status: completed
Targets: 端到端测试、`nop-ai-agent-actor-runtime-vision.md` §5.1、`nop-ai-agent-roadmap.md` §4

- Item Types: `Proof`

- [x] 编写端到端测试 `TestTeamAclEndToEnd`：构造 `DefaultAgentEngine`（InMemoryTeamManager + InMemoryTeamTaskStore + LocalAgentMessenger + **DefaultTeamAclChecker** + mock LLM）→ 程序化创建团队（LEAD + MEMBER）+ 绑定 session → (a) LEAD agent ReAct 调用 team-task-create + team-task-update claim/complete/abandon-unclaimed → 全部成功；(b) MEMBER agent ReAct 调用 team-task-create + claim + complete + abandon-claimed → 成功；MEMBER 调用 abandon-unclaimed → 诚实拒绝（body allowed:false）；(c) MEMBER 调用 team-send-message + team-status → 成功
- [x] 编写 NoOp 零回归验证：默认配置（NoOpTeamAclChecker）下 4 个团队工具行为与当前一致（全部放行，无拒绝）；既有全量测试零回归
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §5.1：Team ACL 模型标注 foundational 拦截层已落地（`ITeamAclChecker` 契约 + `DefaultTeamAclChecker` §5.1 默认矩阵 + `NoOpTeamAclChecker` shipped 默认 + 4 团队工具集成 + abandon-unclaimed ADMIN-only 裁定）；标注 TeamSpec permissions override / 权限派生 / 完整 AclResource 枚举 / 多租户隔离仍为 successor
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 256："多用户可并发运行独立 Actor，租户间资源隔离"标注团队维度 ACL 基础设施已交付（角色矩阵 + 拦截层），完整多租户 tenantId/userId 隔离仍为 successor

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：从 LEAD/MEMBER agent `engine.execute()` 入口 → ReAct → 团队工具 → ACL 检查 → 允许/拒绝 → 实际操作执行/阻止，完整路径跑通且有测试覆盖
- [x] **角色区分验证**：端到端测试断言 MEMBER abandon-unclaimed 被拒绝（body allowed:false）且任务 status 未变（操作被阻止），LEAD abandon-unclaimed 成功（任务 status 变为 ABANDONED）
- [x] NoOp 默认配置下既有全量测试零回归
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言工具经 context 访问到功能性 DefaultTeamAclChecker（非 NoOp），且 ACL denial 实际阻止了 store 操作（非仅返回拒绝但 store 状态被改）
- [x] vision §5.1 + roadmap §4 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `ITeamAclChecker` 契约 + `TeamAclDecision` + `TeamAclAction` + `NoOpTeamAclChecker` + `DefaultTeamAclChecker` 落地为真实（非空壳）代码
- [x] `AgentToolExecuteContext` + `DefaultAgentEngine` + `ReActAgentExecutor` teamAclChecker 全链路接线落地
- [x] 4 个团队工具 executor 集成 ACL 检查（denial 阻止操作 + 诚实报告）落地
- [x] NoOp shipped 默认零回归（NoOpTeamAclChecker 恒 allow）
- [x] 必要 focused verification 已完成（TestTeamAclDecision + TestDefaultTeamAclChecker + TestNoOpTeamAclChecker + 4 工具 ACL 测试 + TestTeamAclEndToEnd）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（TeamSpec permissions override / 权限派生 / 完整 AclResource 枚举 / 多租户隔离 / DB ACL 持久化 / Fencing Token / ResourceGuard 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs 已同步到 live baseline（vision §5.1 + roadmap §4）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）4 个团队工具在运行时确实经 context 调用 checker（不只是字段存在），（b）ACL denial 确实阻止了 store/messenger 操作（端到端断言 task status 未变），（c）无空方法体/静默跳过/no-op 作为正常实现（NoOp 显式 allow、denial 显式拒绝）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；TeamSpec permissions override / 权限派生 / 完整 AclResource 枚举 / 多租户 tenantId/userId 隔离 / DB-backed ACL 持久化 / Fencing Token / ResourceGuard 配额 / 通用工具检查管线统一均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **TeamSpec `permissions` override**（vision §5.1 "LEAD 通过 permissions override 收紧成员权限"）：per-team / per-member 工具操作限制。Classification: successor plan required（依赖 TeamSpec 扩展 permissions 字段）。
- **权限派生**（vision §5.1 "子 Actor 继承父 Actor ACL 规则"）：父子 Actor 层级 ACL 继承。Classification: successor plan required（属完整 §5.1 ACL 的 SESSION/PLAN 资源维度）。
- **完整 `AclResource` 枚举**（SESSION / PLAN / TOOL_EXECUTION / FILE_SCOPE / MESSAGE_CHANNEL）：5 资源 × action 完整矩阵。Classification: successor plan required（当 ACL 扩展到 session 访问 / plan 修改等资源时引入）。
- **多租户 tenantId/userId 隔离**（vision §5.1 "Tenant 隔离 / User 隔离"）。Classification: successor plan required（独立 carry-over `L4-multi-tenant-isolation`）。
- **DB-backed ACL 规则持久化**（`TeamAclEntry` 表 + 跨进程共享）。Classification: successor plan required。
- **通用工具检查管线统一**（将团队 ACL 统一到 `IToolAccessChecker` 调度路径）。Classification: successor plan required（架构重构）。
- **Fencing Token / ResourceGuard 配额 / 自动团队绑定 / TeamSpec XDSL / 跨进程团队消息路由 / DB-backed 团队持久化**：均为 plan 223/225 显式 successor，Classification: successor plan required。

## Closure

Status Note: Plan 228 closes the Team ACL successor gap carried over from plans 223/225/227. The full foundational ACL enforcement layer is delivered: `ITeamAclChecker` contract + `TeamAclAction` / `TeamAclDecision` data model + `NoOpTeamAclChecker` shipped default (zero-regression) + `DefaultTeamAclChecker` (vision §5.1 default role matrix with the only MEMBER-denied operation being `team-task-update` abandon-unclaimed = ADMIN-only). The full engine wiring chain is connected (`AgentToolExecuteContext` → `DefaultAgentEngine` → `ReActAgentExecutor` Builder → dispatch-loop context construction), and all 4 team tool executors consult the checker after team resolution and before performing the actual store/messenger operation. ACL denials are honest strategy feedback (status="success" + JSON body, not exceptions) so the ReAct loop can react to policy. NoOp default preserves the pre-228 behaviour (all bound members can perform all operations regardless of role). Owner docs (vision §5.1 + roadmap §4) are synced. Independent closure audit by explore subagent verified all 12 closure gates PASS with live-code evidence.
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: explore subagent (session `ses_12cf0e861ffeJwA6HtrF5K66pX`) — independent fresh-session audit, NOT a continuation of the implementation session
- Audit Session: ses_12cf0e861ffeJwA6HtrF5K66pX
- Evidence:
  - **All 12 Closure Gates PASS** — verified against live code with file:line citations
  - Gate 1 (Contract surface): 5 new files exist with real logic — `TeamAclAction.java:40-45` (4 enum values), `TeamAclDecision.java:29-67` (immutable + factory invariant `deny ⇒ reason non-null`), `ITeamAclChecker.java:76-77` (returns non-null decision), `NoOpTeamAclChecker.java:43-50` (non-empty body, explicit `allow(null)`), `DefaultTeamAclChecker.java:96-161` (constructor non-null ITeamManager + `getTeam` call + members iteration + 7-tuple static matrix + LEAD-all-pass / MEMBER-deny-ADMIN-only + fail-closed on unknown team/session/tuple)
  - Gate 2 (Engine wiring): full chain verified — `AgentToolExecuteContext.java:54,225-251,304,438-440` + `DefaultAgentEngine.java:314,1368-1369,2548` + `ReActAgentExecutor.java:311,352,417,467,911-913,968,1587`
  - Gate 3 (4 executors integrated): each executor calls checker AFTER team resolution and BEFORE store/messenger — `TeamSendMessageExecutor.java:111-117`, `TeamStatusExecutor.java:102-108`, `TeamTaskCreateExecutor.java:119-125`, `TeamTaskUpdateExecutor.java:180-186`; **critical abandon branching** at `TeamTaskUpdateExecutor.java:161-171` switches on `current.getStatus()` (CLAIMED→abandon-claimed, else abandon-unclaimed)
  - Gate 4 (NoOp zero-regression): `NoOpTeamAclChecker.java:43-50` always `allow(null)` + end-to-end assertion `TestTeamAclEndToEnd.java:430-431`
  - Gate 5 (Focused verification): 8 test files, 45 tests, all green
  - Gate 6 (No silent deferral): all Non-Goals explicitly listed plan lines 77-87 + 257-265
  - Gate 7 (Owner docs synced): `nop-ai-agent-actor-runtime-vision.md:202,269` + `nop-ai-agent-roadmap.md:252,257`
  - Gate 9 (Anti-Hollow Check): all 3 sub-criteria PASS —
    (a) runtime checker invocation confirmed via `engine.execute()` e2e path (`TestTeamAclEndToEnd.java:299-354` denial surfaces in tool response body, proving the field is traversed at runtime through `DefaultAgentEngine.java:2548` → `ReActAgentExecutor:352` → context `:1587`);
    (b) denial blocks operation confirmed via 3 lines of evidence — static code path (deny return precedes store/messenger), recording-stub focused tests (`TestTeam*ExecutorAcl.java` each assert recording flag stays false), AND post-call store-state inspection (`TestTeamAclEndToEnd.java:341-343` task status stays CREATED after MEMBER abandon-unclaimed denial);
    (c) no silent no-op / empty body — `NoOpTeamAclChecker.java:43-50` has real body with explicit return; `DefaultTeamAclChecker.java` every failure path returns explicit `deny(...)` with non-null reason (`:110-111, :123-127, :136-140, :145-148`); `Objects.requireNonNull` on all inputs (`:103-106`)
  - Gate 10/11/12 (build/test/checkstyle): `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS, **2307 tests, 0 failures, 0 errors** (independently re-executed in audit session)
  - **Wiring Verification (#23) — 8 distinct assertions** proving runtime call (not just type existence): `TestDefaultTeamAclChecker.java:320` (stub verify getTeam called), `:248-254` (live snapshot rebind flips deny→allow), `TestTeamAclEndToEnd.java:346-354` (full engine path), `:430-431` (engine defaults to NoOp singleton), `TestTeamSendMessageExecutorAcl.java:218-219` (messenger not called), `TestTeamStatusExecutorAcl.java:176-177` (store not queried), `TestTeamTaskCreateExecutorAcl.java:178-179` (createTask not called), `TestTeamTaskUpdateExecutorAcl.java:351-352` (claimTask not called)
  - **`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/228-nop-ai-agent-team-acl-enforcement.md --strict`** exit code 0 — all 64 checklist items ticked
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high`** exit code 0 — 4 high-severity findings all in PRE-EXISTING files unrelated to plan 228 (`AlwaysClosed.java`, `NoOpGoalTracker.java`, `NoOpSustainer.java`, `InMemoryTeamTaskStore.java`); zero findings in new plan 228 files
  - **Deferred 项分类检查**: all 7 Non-Goals explicitly classified as successor plan required (TeamSpec permissions override / 权限派生 / 完整 AclResource 枚举 / 多租户 tenantId/userId 隔离 / DB-backed ACL 持久化 / 通用工具检查管线统一 / Fencing Token + ResourceGuard + 自动团队绑定 etc.) — no in-scope live defect downgraded to non-blocking follow-up

Follow-up:

- TeamSpec `permissions` override (per-team / per-member 工具操作限制) — successor plan required (依赖 TeamSpec 扩展 permissions 字段)
- 权限派生（父子 Actor ACL 继承）— successor plan required
- 完整 `AclResource` 枚举（SESSION / PLAN / TOOL_EXECUTION / FILE_SCOPE / MESSAGE_CHANNEL）— successor plan required
- 多租户 tenantId/userId 隔离 — successor plan required (carry-over `L4-multi-tenant-isolation`)
- DB-backed ACL 规则持久化（`TeamAclEntry` 表 + 跨进程共享）— successor plan required
- 通用工具检查管线统一（将团队 ACL 统一到 `IToolAccessChecker` 调度路径）— successor plan required
- Minor (M1, non-blocking): MEMBER attempting `abandon` on a terminal task (COMPLETED/ABANDONED) receives the ACL "abandon-unclaimed ADMIN-only" denial rather than a CAS-failure message. Operation still correctly denied either way; LEAD behavior unaffected. Documented at `TeamTaskUpdateExecutor.java:165-169`.
