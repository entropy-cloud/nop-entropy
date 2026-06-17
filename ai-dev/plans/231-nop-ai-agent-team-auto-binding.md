# 231 nop-ai-agent 声明式团队自动绑定 — agent.xdef `<team>` / `<team-member>` + 引擎三入口点 auto-bind

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-team-auto-binding
>
> 注：`active` 为本工作流新建计划的状态值，被编排引擎接受——`ai-dev/tools/check-plan-checklist.mjs` 的 `PLAN_STATUS_RE`（含 `active`）与 `ai-dev/tools/opencode-goal-driver/src/flow-loader.js` 的 `ACTIVE_STATUSES`（`scan-active-plans` 经 PLAN_ROUTER 拾取）均显式列入 `active`；编排引擎自身模板 `prompts/plan-draft.md` 亦写 `active`。`00-plan-authoring-and-execution-guide.md` 的词汇表略陈旧（缺 `active`/`draft`，而 checker 已接受）。

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/230-nop-ai-agent-team-db-persistence.md`（Non-Goals：`自动团队绑定（从 agent config 的 TeamSpec 自动创建团队 + 绑定成员 session）：引擎三入口点自动调用 TeamManager。本计划团队创建仍由集成商程序化调用。Classification: successor plan required（plan 223/225 Non-Goal，依赖 TeamSpec XDSL 配置化）` + `TeamSpec XDSL 配置化（team-spec.xdef）：团队定义经 XDSL 配置加载、Delta 定制。Classification: successor plan required`）+ `ai-dev/plans/227-nop-ai-agent-team-task-update.md`（Non-Goals：`自动团队绑定 ... 均为 plan 225 显式 successor`）+ `ai-dev/plans/228-nop-ai-agent-team-acl-enforcement.md`（Non-Goals：`自动团队绑定 ... 为独立 successor`）+ `ai-dev/plans/223-nop-ai-agent-team-manager.md`（Non-Goals：`TeamSpec XDSL 配置化（vision §8.1 team-spec.xdef）... 是独立 successor`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §8.1（TeamSpec XDSL 字段）+ §4.2（TeamManager 平台层组件，TeamSpec 程序化数据对象，引擎当前不在三入口点自动创建团队）+ §10 Phase 3（TeamSpec DSL 为显式 successor）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 258（"多 Agent 任务可以通过 Flow / Task 组织 ... 自动团队绑定 ... 仍为显式 successor"）
> Related: `223`（交付 `ITeamManager` 契约 + `TeamSpec`/`TeamMemberSpec`/`MemberRole` 不可变数据对象 + `InMemoryTeamManager` + 引擎 `setTeamManager` 接线，本计划在其上新增声明式建团/绑定）、`225`（交付团队通信工具，本计划交付的声明式团队是工具的消费前提）、`228`（交付 `DefaultTeamAclChecker` 持有 `ITeamManager`，声明式团队激活后角色经 `TeamMember.role` 透明流入 ACL）、`230`（交付 `DbTeamManager` 跨进程持久化，声明式团队在 DB-backed 部署下跨进程可见）

## Purpose

把 nop-ai-agent 的团队组成从"仅程序化——集成商必须显式调用 `ITeamManager.createTeam(TeamSpec)` + `bindMemberSession(...)` 才能建立团队与成员绑定；agent 配置（`.agent.xml`）无法声明团队"扩展为"可选声明式——lead agent 的 `.agent.xml` 经可选 `<team>` 元素声明团队结构（teamName / leadAgentName / 成员花名册 / maxParallelMembers），成员 agent 的 `.agent.xml` 经可选 `<team-member>` 元素声明归属；引擎在三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）按声明自动调用 `teamManager.createTeam` + `bindMemberSession`（幂等），无需任何程序化调用"。本计划只负责这一件事：闭合 plan 223/225/227/228/230 反复切出的"自动团队绑定 + TeamSpec XDSL 配置化"successor gap，打通模块"DSL-First 无人值守自动化"主用例的最后一块团队基础设施——让一个完整团队（lead + members）可仅凭 `.agent.xml` 配置声明式materialize。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main` + `nop-kernel/nop-xdefs`，2026-06-17）：

- **agent 配置 = `.agent.xml` 经唯一 chokepoint 加载**：`DefaultAgentEngine.loadAgentModel(agentName)`（`DefaultAgentEngine.java:2672`）经 `ResourceComponentManager.instance().loadComponentModel("/" + agentName + ".agent.xml")` 加载 `AgentModel`。三个执行入口点（`execute`→`doExecute` `:1704`/`:1710`、`resumeSession` `:2007`、`restoreSession` `:2173`）均调用 `loadAgentModel`。**这是 auto-binding 的唯一挂载点**——团队声明必须进入 `AgentModel`，引擎在 loadAgentModel 后、executor 执行前消费它。
- **`agent.xdef` schema 存在且无 team 元素**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`，`xdef:name="AgentModel"` `xdef:bean-package="io.nop.ai.agent.model"`。当前可选子元素：`description` / `meta` / `chatOptions` / `tools` / `availableSkills` / `requiredSkills` / `permissions` / `constraints` / `path-rules` / `prompt` / `hooks`。**无 `<team>` / `<team-member>` 元素**。加载器注册：`_vfs/nop/core/registry/agent.register-model.xml`（`xdsl-loader fileType="agent.xml" schemaPath="/nop/schema/ai/agent.xdef"`）。
- **`AgentModel` 是 xdef 生成类**：`io.nop.ai.agent.model.AgentModel extends _AgentModel`（`model/AgentModel.java`，9 行 hand-written 壳 + `_gen/_AgentModel` 生成基类）。**新增 xdef 元素经 `mvn install` 触发 codegen 生成对应字段/getter**——这是模块既定模式（`permissions`/`constraints`/`path-rules` 均如此），不手改 `_gen`。
- **`TeamSpec` / `TeamMemberSpec` 是手写不可变 POJO**（plan 223 ✅）：`io.nop.ai.agent.team.TeamSpec`（5 字段：teamName/description/leadAgentName/memberSpecs(List<TeamMemberSpec>)/maxParallelMembers，全参构造 + 防御性拷贝，`TeamSpec.java:12` Javadoc 明确"XDSL configuration (team-spec.xdef) is an explicit successor"）+ `TeamMemberSpec`（3 字段：memberName/agentModel/role）。XDSL 生成模型是 mutable bean（extends 生成基类），与 `TeamSpec` 不可变 POJO 模式不同——声明式配置需生成 mutable 模型 + 转换器映射到既有不可变 `TeamSpec`（与 `permissions` → `AgentPermissionModel` 模式一致）。
- **`ITeamManager` 契约稳定**（plan 223 ✅）：`createTeam(TeamSpec) → Team`（内部生成 UUID teamId）/ `bindMemberSession(teamId, memberName, sessionId, actorId)` / `getTeamBySession(sessionId)` / `getMember(teamId, memberName)` / `disbandTeam` / `getActiveTeams` / `addMember` / `removeMember`。`InMemoryTeamManager`（plan 223）+ `DbTeamManager`（plan 230）为功能性实现，`NoOpTeamManager` 为 shipped 默认（写操作抛 `UnsupportedOperationException`，读返回 empty）。
- **引擎显式不在执行路径调用 teamManager**：`DefaultAgentEngine.java:281-289` 注释明确"The engine does NOT call teamManager on its execution path ... Auto team binding (creating a team from agent config's TeamSpec + binding member sessions at the three entry points) is an explicit successor that depends on TeamSpec XDSL configuration"。`teamManager` 字段 `:290`（默认 `NoOpTeamManager.noOp()`）+ `setTeamManager` `:1316`（null-safe 回退 NoOp）。`resolveExecutor` 经 Builder 传递 teamManager 到 ReAct/context 供工具消费（`resolveExecutor` `:2481`+，`.teamManager(this.teamManager)` `:2546`）。
- **首次绑定激活语义**（plan 223/230 ✅）：`bindMemberSession` 在 lead 首次绑定时触发团队 `CREATED → ACTIVE`（InMemoryTeamManager `teams.compute` exactly-once；DbTeamManager 条件 UPDATE `WHERE STATUS='CREATED'`）。`createTeam` 初始 `CREATED`，无成员绑定。
- **团队子系统其余依赖全部 completed**：TeamManager（223）+ 团队通信工具（225）+ team-task-update 状态机（227）+ Team ACL（228）+ DB 持久化（230）全部 ✅。声明式团队是唯一未交付的团队 successor，且其所有前置均已就绪。
- **零声明式团队 / team xdef 代码**：grep `<team` 在 `agent.xdef` 返回 0 命中；grep `team-member|TeamMemberModel|TeamConfigModel|autoBind|AutoBind` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 实现命中。

## Goals

- **`<team>` 可选元素进 `agent.xdef`**（lead agent 声明团队结构）：新增可选子元素 `<team>`，字段对齐 vision §8.1 TeamSpec（teamName / description / leadAgentName / 成员花名册 `<member name agentModel role/>` / maxParallelMembers）。经 `mvn install` codegen 生成 mutable `TeamModel` + `TeamMemberModel`（`io.nop.ai.agent.model` 包，与 `AgentPermissionModel` 同模式）。**既有 `.agent.xml` 无 `<team>` → 零行为回归**（可选元素）。
- **`<team-member>` 可选元素进 `agent.xdef`**（成员 agent 声明归属）：新增可选子元素 `<team-member teamName memberName/>`，供成员 agent 声明"我加入团队 X 作为成员 Y"。可选；无该元素 → 该 agent 不参与任何团队。
- **mutable 团队模型 → 既有不可变 `TeamSpec`/`TeamMemberSpec` 转换器**：lead 声明的 `TeamModel` 转换为 `TeamSpec`（供 `createTeam`），`TeamMemberModel` 转换为 `TeamMemberSpec`。转换在引擎消费时执行（loadAgentModel 后），不在加载期。MemberRole 由 `<member role>` 映射（lead 声明成员的角色）。**转换器确保 leadAgentName 以 `role=LEAD` 注册进 memberSpecs**（匹配 `InMemoryTeamManager.createTeam` 从 memberSpecs 初始化成员 + `bindMemberSession` 要求绑定目标在花名册的既定约定；lead 不在花名册则其自绑定返回 false → 由 DD#8 fail-fast 兜底）。
- **引擎三入口点 auto-bind（lead 侧）**：当 `AgentModel` 含 `<team>` 时，引擎执行：(1) 幂等建团——先 `getTeamBySession(leadSessionId)` 探测，命中则复用既有 teamId，未命中则 `createTeam(spec)`；(2) 绑定 lead session——`bindMemberSession(teamId, leadAgentName, leadSessionId, actorId)`（lead 首次绑定触发 `CREATED → ACTIVE`）。**建团/绑定在异步执行块内 `actorRuntime.createActor()` 之后执行**（`bindMemberSession` 需非 null `actorId`，而 `actorId` 来自 `createActor`；见 DD#7），`actorId` 取 Actor（actorRuntime 已启用）或回退 `sessionId`（NoOp actorRuntime 无 Actor 时）。三个入口点共用同一 auto-bind 逻辑（提取为私有方法，三处复用）。
- **引擎三入口点 auto-bind（member 侧）**：当 `AgentModel` 含 `<team-member>` 时，引擎执行 `bindMemberSession(teamId, memberName, memberSessionId, actorId)`（同样在异步块 `createActor` 后，actorId 取 Actor 或回退 sessionId），幂等（先 `getMember` 探测已绑定则跳过）。teamId 经 `teamName` 解析——`getActiveTeams()`（返回 CREATED+ACTIVE）遍历找 teamName 匹配**且 `status == ACTIVE`** 的团队（lead 必须先建团并激活；见 DD#5）。**未找到对应 ACTIVE 团队（含团队仅 CREATED 情形）→ fail-fast**；**`bindMemberSession` 返回 `false`（memberName 不在 lead 花名册或团队非可绑定状态）→ fail-fast**（见 DD#8，不静默忽略）。
- **NoOp manager 与声明冲突 = fail-fast**：当 `<team>`/`<team-member>` 声明存在但 `teamManager` 为 `NoOpTeamManager`（部署未 wire 功能性 manager），抛 `NopAiAgentException`（清晰消息："agent declares <team> but no functional ITeamManager is wired; call setTeamManager(InMemoryTeamManager/DbTeamManager)"）。**不静默跳过**（Minimum Rules #24）。无声明 → 不触碰 teamManager（零回归）。
- **opt-in / 零回归**：shipped 默认（无 `<team>`/`<team-member>` 声明 + NoOpTeamManager）引擎行为完全不变。既有全量测试零回归。
- **Delta 定制可用**：`<team>`/`<team-member>` 经既有 `xdsl-loader` + Delta 机制天然支持 `/delta/.../*.agent.xml` 覆盖（无需额外工作，验证即可观测）。
- **端到端验证**（Anti-Hollow #22）：lead `.agent.xml`（含 `<team>` + 2 成员花名册）+ 2 个 member `.agent.xml`（各含 `<team-member>`）→ 执行 lead（功能性 `InMemoryTeamManager`）→ 团队 CREATED→ACTIVE + lead 绑定可观测 → 执行 member A/B → 两成员绑定可观测 → `team-status` 工具经 DB/InMemory manager 返回完整 team + 2 members（声明式materialize 的团队被既有团队工具透明消费，drop-in 语义）。
- vision §8.1 TeamSpec 从"程序化数据对象"更新为"声明式 `<team>`/`<team-member>` 已落地"；§4.2 TeamManager + §10 Phase 3 标注 TeamSpec XDSL 配置化 / 自动团队绑定已落地；roadmap §4 Layer 4 验收标准 line 258 标注自动团队绑定已交付。

## Non-Goals

- **`maxParallelMembers` 配额强制**（vision §5.2 ResourceGuard）：`<team maxParallelMembers>` 经转换器流入 `TeamSpec.maxParallelMembers`（hint 记录），但引擎/manager 不强制（与 plan 223/225/228/230 一致的 foundational 裁定）。Classification: successor plan required（属 ResourceGuard / Phase 5 范畴）。
- **vision §8.1 的额外 TeamSpec 字段**（`kind=category|direct` / `category` 路由 / `prompt` 成员 prompt 覆盖 / `permissions` 成员权限覆盖 / `maxWallClockMinutes` / `maxMessagesPerRun`）：vision §8.1 列出更丰富的字段。本计划首版只交付建团 + 绑定所需的最小子集（teamName/description/leadAgentName/member{name,agentModel,role}/maxParallelMembers）。`prompt` 覆盖、`category` 路由、wall-clock/messages 限额、permissions override 均为独立 successor（permissions override 依赖 plan 228 successor 的 TeamSpec permissions 字段）。Classification: successor plan required / optimization candidate。
- **自动 spawn 成员 agent**：本计划只绑定成员 session，不自动启动/调度成员 agent 执行（成员由 lead 经既有 `call-agent` 工具显式调用，或由集成商外部驱动）。声明式团队只负责"成员 session 一旦执行就自动绑定"，不负责"主动拉起成员"。Classification: successor plan required（属任务调度 / nop-task DAG 范畴）。
- **隐式 member 绑定（经 call-agent 自动探测团队花名册）**：另一种 member 绑定设计——引擎在 call-agent 子 session 创建时探测父 lead 的团队花名册自动绑定。本计划采用显式 `<team-member>` 声明（明确、可测、无魔法耦合）。隐式探测为独立 successor。Classification: optimization candidate。
- **多租户 tenantId/userId 隔离**（vision §5.1）：声明式团队不做租户隔离（团队经 teamName 全局解析）。Classification: successor plan required（独立 carry-over `L4-multi-tenant-isolation`）。
- **TeamSpec `permissions` override**（vision §5.1）：`<team>` 无 permissions 元素。Classification: successor plan required（plan 228 Non-Goal）。
- **teamName 跨进程唯一性 / 冲突仲裁**：`getActiveTeams()` 按 teamName 解析时，若存在多个同名 ACTIVE 团队（理论上 DbTeamManager 多实例并发建团可能），foundational slice 取第一个匹配并 LOG.warn（不仲裁）。稳健的 teamName 唯一约束 + 冲突仲裁为 successor。Classification: optimization candidate。
- **团队成员存活心跳 / 自动 disband 空团队**：声明式建团后团队的解散仍由 `disbandTeam` 显式调用或集成商管理。空团队自动回收、成员心跳为 successor。Classification: successor plan required。
- **DB-backed ACL 规则持久化 / nop-task DAG / blockedBy 依赖解析 / ResourceGuard / Fencing Token**：均为 plan 227/228/230 显式 successor，本计划不触及。

## Scope

### In Scope

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`（编辑源 schema）：
  - 新增可选 `<team>` 元素（xdef:name 生成 `TeamModel`，bean-package `io.nop.ai.agent.model`）：属性 teamName / description? / leadAgentName / maxParallelMembers?；子元素 `<member>`（name / agentModel / role=enum:LEAD,MEMBER|MEMBER）列表
  - 新增可选 `<team-member>` 元素（xdef:name 生成 `TeamMemberRefModel`）：属性 teamName / memberName
- `io.nop.ai.agent.model` 包（codegen 生成，经 `mvn install`）：`_gen/_TeamModel.java` + `_gen/_TeamMemberModel.java` + `_gen/_TeamMemberRefModel.java` + hand-written 壳类（如 `TeamModel.java` 等，镜像 `AgentModel` 9 行壳模式）
- `io.nop.ai.agent.team` 包（新文件）：`TeamModelConverter`（或等价）—— mutable `TeamModel`/`TeamMemberModel` → 既有不可变 `TeamSpec`/`TeamMemberSpec` 转换（含 MemberRole 映射、memberSpecs 列表构造、null/默认值处理）
- `io.nop.ai.agent.engine.DefaultAgentEngine`（编辑）：提取私有 auto-bind 方法，在 `loadAgentModel` 后于三个入口点（`doExecute` `:1710` 后、`resumeSession` `:2007` 后、`restoreSession` `:2173` 后）调用：(a) `<team>` → 幂等 createTeam + bindMemberSession(lead)；(b) `<team-member>` → 解析 teamName + 幂等 bindMemberSession(member)；(c) NoOp manager 与声明冲突 → fail-fast
- 测试 fixture + 测试文件（`nop-ai/nop-ai-agent/src/test`）：
  - `.agent.xml` fixtures：`team-lead.agent.xml`（含 `<team>` + 2 成员）+ `team-member-a.agent.xml` / `team-member-b.agent.xml`（各含 `<team-member>`）
  - `TestTeamAutoBinding.java` — 单元：转换器 round-trip（TeamModel → TeamSpec 字段保真 + role 映射）、NoOp+声明 fail-fast、无声明零回归
  - `TestTeamAutoBindingE2E.java` — 端到端：功能性 `InMemoryTeamManager` + 执行 lead → 团队 ACTIVE + lead 绑定 + 执行 member A/B → 两成员绑定 + `team-status` 工具返回完整 team（Anti-Hollow #22 端到端 + #23 接线验证 engine→loadAgentModel→auto-bind→teamManager→工具调用链连通）

### Out Of Scope

- maxParallelMembers 强制（Non-Goal: ResourceGuard）
- vision §8.1 额外字段 prompt/category/permissions/wallClock/maxMessages（Non-Goal: 丰富字段 successor）
- 自动 spawn 成员（Non-Goal: 主动调度）
- 隐式 call-agent member 探测（Non-Goal: 显式声明替代）
- 多租户隔离（Non-Goal: tenantId）
- permissions override（Non-Goal: TeamSpec permissions）
- teamName 跨进程唯一性仲裁（Non-Goal: optimization）
- 空团队自动回收 / 心跳（Non-Goal: 团队 GC）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

1. **团队声明嵌入 `agent.xdef`（非独立 `team-spec.xdef` 文件）**。vision §8.1 措辞为"`team-spec.xdef`"，但实施层 `loadAgentModel(agentName)` 是唯一 chokepoint，lead agent 的 `.agent.xml` 是建团的天然挂载点（与 `<permissions>`/`<constraints>`/`<path-rules>` 嵌入模式一致）。独立文件需第二条加载路径 + agent↔team 交叉引用解析，无收益。`<team>` 元素的字段结构对齐 §8.1（即"`team-spec.xdef`"的字段集），以嵌入形式落地。理由：(1) 单一 chokepoint，三入口点无需新加载逻辑；(2) 与既有嵌入元素模式一致；(3) Delta 定制经既有 `xdsl-loader` 天然支持。

2. **member 绑定经显式 `<team-member>` 声明（非隐式 call-agent 探测）**。成员 agent 在自己的 `.agent.xml` 声明 `<team-member teamName memberName/>`。理由：(1) 显式 opt-in，无魔法耦合 call-agent 与团队；(2) 可独立测试（每个 member agent.xml 自描述其归属）；(3) lead 的 `<team>` 定义花名册（团队结构），member 的 `<team-member>` 表达绑定意愿——两者解耦但协同。隐式探测为 Non-Goal successor。

3. **mutable XDSL 模型 + 转换器 → 既有不可变 `TeamSpec`**。xdef 生成 mutable bean（`TeamModel`/`TeamMemberModel`，extends 生成基类，与 `AgentPermissionModel` 同模式），经 `TeamModelConverter` 映射到既有不可变 `TeamSpec`/`TeamMemberSpec`（plan 223 契约不变）。转换器**保证 leadAgentName 以 `role=LEAD` 出现在 memberSpecs 中**（若 `<team>` 花名册未显式列出 lead，转换器自动补登记 lead 为 LEAD 成员），匹配 `InMemoryTeamManager.createTeam` 从 memberSpecs 初始化成员、且 `bindMemberSession` 要求绑定目标在花名册的既定约定。理由：(1) 不改 `ITeamManager.createTeam(TeamSpec)` 契约；(2) 不改既有不可变 `TeamSpec` POJO；(3) XDSL mutable bean 是模块既定 codegen 模式；(4) lead 在花名册是 lead 自绑定成功的必要条件（否则 `bindMemberSession` 返回 false → DD#8 fail-fast）。

4. **建团幂等 = `getTeamBySession` 探测先于 `createTeam`**。lead 每次执行（含 resume/restore）都进 auto-bind 路径；先 `getTeamBySession(leadSessionId)` 探测，命中复用 teamId，未命中才 `createTeam`。避免重复建团（`createTeam` 内部生成新 UUID，无幂等）。member 绑定幂等 = 先 `getMember` 探测已绑定则跳过。理由：(1) resume/restore 重入安全；(2) 不要求 `ITeamManager` 契约新增幂等方法。

5. **member teamId 解析 = `getActiveTeams()` 按 teamName + ACTIVE 状态匹配**。member 的 `<team-member teamName="X">` 需解析为 teamId。注意 `getActiveTeams()` 契约返回**所有未解散团队（CREATED 或 ACTIVE）**（`ITeamManager` Javadoc + `InMemoryTeamManager` `status != DISBANDED` + `DbTeamManager` `WHERE STATUS <> 'DISBANDED'`），**非仅 ACTIVE**。因此解析须显式过滤：遍历 `getActiveTeams()`，匹配 teamName 相等 **且 `team.getStatus() == TeamStatus.ACTIVE`** 的团队（确保 lead 已绑定并激活团队；CREATED 状态团队 = lead 尚未绑定，member 不应绑定到未激活团队）。foundational slice 取首个 ACTIVE 匹配；多同名团队 LOG.warn（Non-Goal 仲裁）。未找到 ACTIVE 匹配（含"团队存在但仅 CREATED"情形）→ fail-fast（"member declares team-member but no ACTIVE team named X found; ensure the lead agent has executed and bound/activated the team"）。理由：(1) 无需新索引；(2) 要求 lead 先执行激活团队，语义明确；(3) 显式 ACTIVE 过滤使排序保证可强制（否则 member 会绑定到未激活团队）。

6. **NoOp manager + 声明 = fail-fast**。`<team>`/`<team-member>` 存在但 `teamManager` 为 NoOp → 抛 `NopAiAgentException`。理由：(1) Minimum Rules #24（No Silent No-Op）——声明了团队却无功能性 manager 是部署 misconfiguration，必须显式失败而非静默忽略；(2) 无声明路径完全不触碰 teamManager（零回归）。

7. **auto-bind 分两阶段：同步 fail-fast 预检 + 异步建团/绑定（createActor 后）**。`ITeamManager.bindMemberSession(teamId, memberName, sessionId, actorId)` 需非 null `actorId`，而 `actorId` 来自 `actorRuntime.createActor()`——该方法在三个入口点的**异步执行块内**调用（`doExecute` `:1794`、`resumeSession` `:2050`、`restoreSession` `:2212`），且受 `actorRuntime.isEnabled()` 门控（NoOp shipped 默认 `isEnabled()=false`，不创建 Actor，此时无 actorId 来源）。因此 auto-bind 的真实工作（createTeam + bindMemberSession）放在**异步执行块内 `createActor()` 之后**，此时 actorId 可用：(a) actorRuntime 已启用 → 用 Actor 的 actorId；(b) actorRuntime 为 NoOp（未配置 Actor 运行时）→ 用 `sessionId` 作为 actorId 关联值（actorId 是 `TeamMember` 上的 opaque 关联标识，团队绑定/消息路由语义只依赖 sessionId，不依赖活跃 Actor；sessionId 是稳定唯一标识，是无 Actor 运行时部署的合法 stand-in）。建团（createTeam）不需 actorId，也在同一异步块内执行。另：loadAgentModel 后、异步派发前做**同步 fail-fast 预检**——声明存在（`<team>`/`<team-member>`）但 `teamManager` 为 NoOp → 立即抛 `NopAiAgentException`（早暴露部署 misconfiguration，不进异步块）。三入口点共用私有 auto-bind 方法，建团/绑定失败（fail-fast 异常）经 CompletableFuture 传播为执行失败，不吞异常、不降级。理由：(1) actorId 在 createActor 后才可用，绑定必须在彼处；(2) 同步预检早暴露 NoOp misconfiguration；(3) 单一异步挂载点便于测试与审计。

8. **bindMemberSession 返回 false = fail-fast（No Silent No-Op）**。`InMemoryTeamManager.bindMemberSession`（与 `DbTeamManager` 同契约）在 memberName 不在 lead 花名册、或团队非可绑定状态时返回 `false`（非异常）。引擎检查该 boolean 返回：`false` → 抛 `NopAiAgentException`（清晰消息："member declares <team-member memberName='X'> but X is not in the lead's team roster, or the team is not in a bindable state"），**不静默忽略**（Minimum Rules #24）。这强制 lead `<team>` 花名册必须含所有声明 `<team-member>` 的成员名，且 lead 自身必须以 `role=LEAD` 在花名册内（否则 lead 自绑定 false → fail-fast，由转换器 DD#3 保证 lead 注册）。

### Phase 1 - agent.xdef `<team>` / `<team-member>` schema + 生成模型 + 转换器

Status: completed
Targets: `nop-kernel/nop-xdefs/.../schema/ai/agent.xdef`, `io.nop.ai.agent.model._gen.*`, `io.nop.ai.agent.model.TeamModel`/`TeamMemberModel`/`TeamMemberRefModel`, `io.nop.ai.agent.team.TeamModelConverter`

- Item Types: `Decision`, `Fix`

- [x] 编辑 `agent.xdef` 新增可选 `<team>` 元素（xdef:name `TeamModel`：teamName/description?/leadAgentName/maxParallelMembers? + `<member>` 列表 name/agentModel/role=enum:LEAD,MEMBER|MEMBER）
- [x] 编辑 `agent.xdef` 新增可选 `<team-member>` 元素（xdef:name `TeamMemberRefModel`：teamName/memberName）
- [x] `mvn install -pl nop-kernel/nop-xdefs,nop-ai/nop-ai-agent -am` 触发 codegen 生成 `_TeamModel`/`_TeamMemberModel`/`_TeamMemberRefModel` + hand-written 壳类（镜像 `AgentModel`/`AgentPermissionModel` 模式）
- [x] 实现 `TeamModelConverter`：`TeamModel` → `TeamSpec`（含 memberSpecs 列表 + MemberRole 映射 + 默认值/null 处理），字段保真
- [x] 编写 `TestTeamModelConverter`：round-trip 字段保真（teamName/description(null)/leadAgentName/member{name,agentModel,role=LEAD/MEMBER}/maxParallelMembers(0=unlimited)）、空成员列表、role 枚举映射

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `agent.xdef` 含 `<team>` 与 `<team-member>` 可选元素（grep 可观测）
- [x] `mvn install` 成功且 `_gen/_TeamModel.java`/`_gen/_TeamMemberModel.java`/`_gen/_TeamMemberRefModel.java` 生成存在（glob 可观测）
- [x] 既有 `.agent.xml`（无 `<team>`）经 `loadAgentModel` 仍加载成功且 `AgentModel.getTeam()` 返回 null（零回归，单测验证）
- [x] `TestTeamModelConverter` 全绿，覆盖字段保真 + role 映射 + 边界
- [x] **无静默跳过**：转换器对缺失必填字段（如 teamName/leadAgentName）抛异常而非返回 null/默认（Minimum Rules #24）
- [x] 若该 Phase 改变 live baseline：相关 `ai-dev/design/` 已更新（Phase 3 统一更新 vision §8.1/§4.2/roadmap）；Phase 1 本身 `No owner-doc update required`（schema 扩展，文档更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引擎三入口点 auto-bind 集成

Status: completed
Targets: `io.nop.ai.agent.engine.DefaultAgentEngine`

- Item Types: `Fix`, `Decision`

- [x] 提取私有 auto-bind 方法（lead 侧 + member 侧 + NoOp 冲突检测 + false-return 检测），三入口点共用
- [x] loadAgentModel 后**同步 fail-fast 预检**：声明存在 + teamManager 为 NoOp → 抛 `NopAiAgentException`（三入口点共用）
- [x] 在 `doExecute` 异步执行块内 `actorRuntime.createActor()`（`:1794`）后接入：含 `<team>` → 幂等 createTeam + bindMemberSession(lead)（`getTeamBySession` 探测先于 createTeam；actorId 取 Actor 或回退 sessionId）
- [x] 在 `resumeSession` 异步块 `createActor()`（`:2050`）后接入同一 auto-bind 逻辑
- [x] 在 `restoreSession` 异步块 `createActor()`（`:2212`）后接入同一 auto-bind 逻辑
- [x] member 侧：含 `<team-member>` → `getActiveTeams()`（返回 CREATED+ACTIVE）遍历找 teamName 匹配**且 `status == ACTIVE`** 的团队解析 teamId + 幂等 bindMemberSession(member)；未找到 ACTIVE 匹配（含团队仅 CREATED）fail-fast；`bindMemberSession` 返回 false（member 不在花名册/非可绑定状态）fail-fast；多同名 LOG.warn
- [x] 无 `<team>`/`<team-member>` 声明路径完全不触碰 teamManager（零回归）
- [x] 编写 `TestTeamAutoBinding`：lead 幂等建团（二次执行不重建）+ lead 绑定 + member 绑定 + member 未找到 ACTIVE 团队 fail-fast（含团队仅 CREATED 情形）+ bindMemberSession false-return fail-fast（member 不在花名册）+ NoOp+声明 fail-fast + 无声明零回归 + actorId 取 Actor 与回退 sessionId 两条路径

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultAgentEngine` 三入口点均在异步执行块 `createActor()` 后调用 auto-bind，且 loadAgentModel 后有同步 NoOp fail-fast 预检（grep 调用点可观测）
- [x] lead 执行后 `teamManager.getTeamBySession(leadSessionId)` 非 null 且团队 `ACTIVE`、lead 成员已绑定（单测 + E2E 断言）
- [x] lead 二次执行（resume/restore）不重复建团（`getActiveTeams` 计数不变，单测断言）
- [x] member 执行后 `teamManager.getMember(teamId, memberName)` 绑定可观测（E2E 断言）
- [x] member 声明的 teamName 无对应 **ACTIVE** 团队 → 抛异常（fail-fast，单测）；含"团队存在但仅 CREATED（lead 未激活）"情形 → 同样 fail-fast（单测，验证 `getActiveTeams()` 返回 CREATED+ACTIVE 时解析器正确按 ACTIVE 过滤）
- [x] `bindMemberSession` 返回 false（member 不在 lead 花名册）→ 抛 `NopAiAgentException`（fail-fast，单测，非静默忽略）
- [x] `<team>` 声明 + NoOpTeamManager → 抛 `NopAiAgentException`（同步预检 fail-fast，单测）
- [x] actorId 取 Actor（actorRuntime 已启用）与回退 sessionId（NoOp actorRuntime）两条路径均有测试覆盖，绑定均成功
- [x] 无声明的既有 agent 执行 → 不调用任何 teamManager 写方法（零回归，既有全量测试绿）
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言 engine→loadAgentModel→(同步预检)→异步块→createActor→auto-bind→teamManager.createTeam/bindMemberSession 调用链连通（mock verify 或 InMemory 断言状态）
- [x] **无静默跳过**（Minimum Rules #24）：auto-bind 各失败分支（未找到团队 / bindMemberSession false / NoOp 冲突）抛异常，无空方法体/吞异常/忽略 boolean 返回
- [x] `No owner-doc update required`（本 Phase 文档更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证 + 文档同步

Status: completed
Targets: 测试 fixtures + `ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` + `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`, `Follow-up`

- [x] 新增 `.agent.xml` fixtures：`team-lead.agent.xml`（含 `<team>` teamName/leadAgentName/2 成员花名册）+ `team-member-a.agent.xml` + `team-member-b.agent.xml`（各含 `<team-member>`）
- [x] `TestTeamAutoBindingE2E`：功能性 `InMemoryTeamManager` wired → 执行 lead → 团队 ACTIVE + lead 绑定 → 执行 member A/B → 两成员绑定 → `team-status` 工具返回完整 team + 2 members（声明式团队被既有工具透明消费）
- [x] Delta 定制观测：`/delta/.../team-lead.agent.xml` 覆盖 `<team>` 字段后加载生效（经既有 xdsl-loader，单测验证）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §8.1：TeamSpec 从"程序化数据对象，XDSL successor"→"声明式 `<team>`/`<team-member>` 已落地（字段对齐 §8.1 子集，prompt/category/permissions/wallClock/maxMessages 为 successor）"
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §4.2 + §10 Phase 3：TeamSpec XDSL 配置化 / 自动团队绑定标注已落地
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 258：自动团队绑定标注已交付（保留 nop-task DAG / blockedBy 依赖解析 / maxParallelMembers 强制等 successor 未完成状态）
- [x] 更新 `TeamSpec.java` Javadoc：将"XDSL configuration (team-spec.xdef) is an explicit successor"更新为指向已落地的 `<team>` 元素

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 3 个 `.agent.xml` fixtures 存在且可经 `loadAgentModel` 加载（含 `<team>`/`<team-member>`）
- [x] **端到端验证**（Minimum Rules #22）：`TestTeamAutoBindingE2E` 从"执行 lead 入口点"到"`team-status` 工具输出完整 team+members"全路径绿，断言团队 ACTIVE + lead 绑定 + 2 成员绑定 + 工具透明消费
- [x] Delta 覆盖测试绿（声明式团队经 Delta 定制生效）
- [x] vision §8.1/§4.2/§10 + roadmap §4 line 258 + `TeamSpec.java` Javadoc 均已更新（grep 可观测，无残留"successor"指向已落地项）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（既有 + 新增测试零回归）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `<team>` / `<team-member>` 可选元素进 `agent.xdef` 且 codegen 生成模型存在
- [x] 引擎三入口点（doExecute/resumeSession/restoreSession）均接入 auto-bind
- [x] lead 幂等建团 + 绑定 + member 绑定 + fail-fast（NoOp 冲突 / 未找到团队）全部行为成立且有 focused 测试
- [x] 端到端：声明式 lead+members `.agent.xml` → 团队 materialize → 既有 `team-status` 工具透明消费（Anti-Hollow）
- [x] 无声明路径零回归（既有全量测试绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（maxParallelMembers 强制 / §8.1 额外字段 / 自动 spawn 成员 / 隐式探测 / 多租户 / permissions override / teamName 仲裁 / 空团队回收均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（vision §8.1/§4.2/§10 + roadmap §4 + TeamSpec.java Javadoc）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）engine→loadAgentModel→auto-bind→teamManager→工具调用链运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；maxParallelMembers 强制 / vision §8.1 额外字段（prompt/category/permissions/wallClock/maxMessages）/ 自动 spawn 成员 / 隐式 call-agent member 探测 / 多租户 tenantId 隔离 / TeamSpec permissions override / teamName 跨进程唯一性仲裁 / 空团队自动回收 + 成员心跳均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **`maxParallelMembers` 配额强制**（vision §5.2 ResourceGuard）：`<team maxParallelMembers>` 声明流入 hint 但不强制。Classification: successor plan required（ResourceGuard / Phase 5）。
- **vision §8.1 丰富字段**（member `prompt` 覆盖 / `kind=category` 路由 / `category` / `permissions` override / `maxWallClockMinutes` / `maxMessagesPerRun`）：声明式团队首版只交付建团+绑定最小子集。Classification: successor plan required / optimization candidate。
- **自动 spawn 成员 agent**：声明式团队只绑定成员 session，不主动拉起成员执行。Classification: successor plan required（任务调度 / nop-task DAG）。
- **隐式 member 绑定（call-agent 自动探测花名册）**：替代显式 `<team-member>` 的隐式设计。Classification: optimization candidate。
- **teamName 跨进程唯一性 + 冲突仲裁**：foundational slice 多同名团队取首个 + LOG.warn。Classification: optimization candidate。
- **空团队自动回收 / 成员存活心跳**：声明式建团后团队 GC。Classification: successor plan required。

## Closure

Status Note: 声明式团队自动绑定全链路已落地并经独立 closure audit 验证。lead `.agent.xml` `<team>` + member `<team-member>` 嵌入 `agent.xdef`，codegen 生成 mutable 模型，`TeamModelConverter` 映射到既有不可变 `TeamSpec`，引擎三入口点按声明幂等建团 + 绑定，功能性 manager 未 wire 时 fail-fast。端到端验证声明式团队被既有 `team-status` 工具透明消费（drop-in）。所有 Non-Goals（maxParallelMembers 强制 / §8.1 丰富字段 / 自动 spawn / 隐式探测 / 多租户 / permissions override / teamName 仲裁 / 空团队回收）均显式切出为独立 successor，无 in-scope live defect 被静默降级。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: independent general-purpose subagent (closure-audit, fresh session, task_id `ses_12c19a9e0ffeYLvmu4CnT5Gn6L`)
- Evidence:
  - Item 1 (agent.xdef schema): PASS — `agent.xdef:52` `<team xdef:name="TeamModel">`, `:64` `<team-member xdef:name="TeamMemberRefModel">`.
  - Item 2 (codegen models): PASS — 3 `_gen/_*.java` + 3 hand-written shells exist; `_AgentModel.java:458` `getTeam()` / `:480` `getTeamMember()`.
  - Item 3 (TeamModelConverter): PASS — converts to TeamSpec; lead-in-roster guarantee `:83-96`; throws on missing teamName `:67-69` / leadAgentName `:72-74`.
  - Item 4 (engine wiring): PASS — 3 precheck sites (`:1721`/`:2023`/`:2195`) after loadAgentModel; 3 autoBind sites (`:1810`/`:2072`/`:2240`) inside supplyAsync after createActor.
  - Item 5 (No Silent No-Op): PASS — NoOp+declaration throws `:2749-2754`; no-ACTIVE-team throws `:2838-2844`; bindMemberSession false throws `:2803-2809`/`:2858-2864`; no empty bodies.
  - Item 6 (tests + fixtures): PASS — 5 test classes + 3 fixtures exist; auditor independently re-ran → 28/28 green.
  - Item 7 (Anti-Hollow E2E): PASS — `TestTeamAutoBindingE2E` asserts lead→ACTIVE+bound `:92`/`:115-119`, member A/B bound `:120-122`, TeamStatusExecutor returns 3 members `:146`.
  - Item 8 (doc sync): PASS — vision §8.1 `:373` + §10 `:453`, roadmap `:255` ✅ row, `TeamSpec.java:11-19` Javadoc — all "landed", no residual "team-spec.xdef successor".
  - Item 9 (zero regression): PASS — `autoBindTeam` `:2765-2774` only proceeds when getTeam()/getTeamMember() non-null.
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0 (no unchecked items, Closure Evidence written).
  - Anti-Hollow: `scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit 0 (0 findings); E2E test proves engine→loadAgentModel→precheck→async→createActor→autoBind→teamManager→tool chain connected at runtime.
  - Build: `./mvnw clean test -pl nop-ai/nop-ai-agent` → 2378 tests, 0 failures.
  - Deferred classification: all Non-Goals explicit successors; no in-scope live defect downgraded.

Follow-up:

- no remaining plan-owned work. Non-Goals carry over to successor plans (maxParallelMembers 强制=ResourceGuard; §8.1 丰富字段; 自动 spawn=nop-task DAG; 隐式探测; 多租户 `L4-multi-tenant-isolation`; permissions override=plan 228 successor; teamName 仲裁; 空团队回收).
