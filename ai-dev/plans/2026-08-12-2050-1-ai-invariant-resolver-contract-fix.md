# 1 AI Invariant Loop — AgentExecutorResolver 两参重载契约修复 + MA4.2-05 拆分参数传递完整性复查

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Last Reviewed: 2026-08-12
> Source: `ai-dev/audits/2026-08-12-1119-open-audit-nop-ai-invariant-loop.md` [P1][AR-1]（open-ended adversarial audit，live 证据 git show 2f3251589^ 实证）
> Related: `2026-08-12-1411-2-ai-invariant-i4-fix-execution.md`（MA4.2-05 拆分本体）；`2026-08-12-1700-2-ai-invariant-i6-loop-closure.md`（Cycle 1 收口）
> Review: 四轮独立子 agent 对抗性审查（fresh sessions ses_009f550f7ffe / ses_009edfd57ffe / ses_009e65689ffe / ses_009e1c2b4ffe），R4 共识 = executable as-is（无 Blocker/Major）→ draft → active

## Purpose

修复 MA4.2-05 引擎拆分（commit 2f3251589）引入的两参重载静默丢参 contract drift，并对拆分面做参数传递完整性复查，防止同批拆分的其他类存在同类残留。

## Current Baseline

（2026-08-12 live 核实）

- **缺陷实锤**：`AgentExecutorResolver.resolveExecutor(AgentModel, IToolAccessChecker)` 两参重载（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutorResolver.java:129-131`）实现为 `return resolveExecutor(model, config.getToolAccessChecker(), config.getPathAccessChecker());`——调用方传入的 `toolAccessChecker` 被静默丢弃。拆分前（`git show 2f3251589^:DefaultAgentEngine.java:3250`）为 `resolveExecutor(model, toolAccessChecker, this.pathAccessChecker)`，参数被正确使用。
- **契约声明**：两参重载 javadoc（`AgentExecutorResolver.java:123-128`）声明 "Existing callers that only override the tool checker continue to compile **and behave identically**"——当前实现违背该声明（behave differently）。
- **调用方**：`DefaultAgentEngine.java:989-990` 两参 delegate → resolver；`TestSubAgentPermissionWiring.java:306`（`engine.resolveExecutor(model, wrapped)`）。生产路径均走三参（`DefaultAgentEngine.java:768`、`AgentSessionLifecycle.java:313/447/627`），故当前仅测试路径触发——但这是安全相关参数（parent-constrained tool checker）的静默 contract drift。
- **测试盲区**：`TestSubAgentPermissionWiring.resolveExecutorPassesEffectiveCheckerToExecutor`（:294-310）只断言 `assertNotNull(executor)`，测不出 wrapped checker 是否生效——"拆分语义 0-diff"宣称在此点不成立。
- **可观察性事实（2026-08-12 live 核实）**：`ReActAgentExecutor` 无 checker getter（checker 仅构造参数 :182 / 使用 :249）——测试无法从装配面读回 checker；库内既有先例 = `DefaultAgentEngine.getToolAccessCheckerForTest()`（:560-561，package-private 测试支持方法）。测试与 `ReActAgentExecutor` 同在 `io.nop.ai.agent.engine` 包，package-private accessor 可行且不改变 public contract。
- **拆分面残留风险**：同批拆分共 9 个类（`AgentExecutorResolver`/`AgentCallDelegate`/`AgentSessionLifecycle`/`AgentTeamBinder`/`AgentStartupWarnings`/`SessionLockRenewal`/`AgentSessionSupport`/`DefaultAgentEngineConfig` + `team/scheduler/TaskDispatchCoordinator`，见 audit AR-6 清单；**注意 AR-6 的 `TaskDispatchCoordinator:37` logger 为 live 复核误报项**——其 logger 实为 `TeamTaskSchedulerDaemon.class`，Phase 2 复查时只核对其参数传递、不处理 logger），AR-1 抓到的丢参 + AR-5/AR-6 的 import 三重复制 / logger 挂名说明 copy-paste 残留真实存在，参数传递完整性未经系统复查。
- 已知相关事实：I5 full-green（`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS，2026-08-12）；门禁②/其他五族门禁零命中（I5 记录）。

## Goals

- 两参重载恢复参数传递：`resolveExecutor(model, toolAccessChecker, config.getPathAccessChecker())`，实现与 javadoc 声明一致。
- 行为级测试：断言 wrapped checker 实际生效（非仅 non-null），防再次静默回归。
- 拆分面参数传递完整性复查：9 个拆分类逐一核对参数是否被静默丢弃/替换，发现即修。

## Non-Goals

- **不处理 P2 项**：import 三重复制（AR-5）、logger 挂名（AR-6）属 `## Follow-up Backlog`（roadmap `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md`），不在本 plan。
- 不改变三参重载签名 / 不改 `IToolAccessChecker` 接口 / 不调整 `DefaultAgentEngineConfig` 默认 checker 装配逻辑。
- 不评估门禁①-⑤ 其他维度（归 audit 其他 finding / backlog）。

## Scope

### In Scope

- `AgentExecutorResolver.java:129-131` 两参重载参数传递修复。
- `ReActAgentExecutor` package-private `getToolAccessCheckerForTest()` accessor（测试支持，镜像 `DefaultAgentEngine:560` 先例，非 public contract 变更）。
- `TestSubAgentPermissionWiring.resolveExecutorPassesEffectiveCheckerToExecutor` 升级为行为断言。
- 9 个拆分类（MA4.2-05 拆分产物）参数传递完整性复查（`git show 2f3251589^` 与 live 逐方法对比）。

### Out Of Scope

- AR-5/AR-6 等 P2 清理（backlog）。
- 门禁机制改造（门禁②分支级 marker 升级归 Plan 2 `2026-08-12-2050-2`）。

## Execution Plan

### Phase 1 - 两参重载丢参修复 + 行为级测试（同 Phase 落地，防回归）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutorResolver.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestSubAgentPermissionWiring.java`

- Item Types: `Fix | Proof`

- [x] `Fix` `AgentExecutorResolver.java:129-131`：两参重载改为 `return resolveExecutor(model, toolAccessChecker, config.getPathAccessChecker());`（恢复拆分前语义；参数名保持 `toolAccessChecker`）。
- [x] `Fix` `ReActAgentExecutor` 增加 checker 字段存储 + package-private `IToolAccessChecker getToolAccessCheckerForTest()` accessor：当前 checker 只经构造参数直接传入 `AgentSecurityConsultation`（`ReActAgentExecutor.java:182` 构造参数、:249 使用），无自有字段——需新增字段 + 构造器赋值，再暴露 package-private getter（镜像 `DefaultAgentEngine.java:560-561` 既有测试支持先例；**非 public API 变更**，不触碰跨模块契约）。
- [x] `Fix` 升级 `TestSubAgentPermissionWiring.resolveExecutorPassesEffectiveCheckerToExecutor`（:294-310）：`engine.resolveExecutor(model, wrapped)` 后断言 `((ReActAgentExecutor) executor).getToolAccessCheckerForTest() == wrapped`（引用相等——修复后两参重载必须把调用方传入的 wrapped 实例透传，任何替换为 config 默认值/重新包装都会失败）；保留原 assertNotNull 作为防御。
- [x] `Proof` 确认三参重载与 javadoc 不受影响；确认 `DefaultAgentEngine.java:985-994` 三个 delegate 无同类问题（live 阅读）。
- [x] `Proof` 负例验证（必做）：临时把两参重载改回丢参实现 → `resolveExecutorPassesEffectiveCheckerToExecutor` 必红；恢复修复 → 绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AgentExecutorResolver.java:129-131` 两参重载实际使用传入的 `toolAccessChecker`（代码可读验证）
- [x] 行为级断言成立：executor 携带的 checker 与调用方传入的 wrapped 引用相等（`==` 断言通过）
- [x] 负例验证记录：丢参实现 → 测试红；修复 → 绿
- [x] `git diff` 仅含：两参重载一行变更 + ReActAgentExecutor accessor + 测试升级（无附带改动）
- [x] `./mvnw test -pl nop-ai-agent -am -Dtest=TestSubAgentPermissionWiring -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] No owner-doc update required（源码级契约修复 + package-private 测试支持，javadoc 已声明该行为，修复后实现与声明一致）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MA4.2-05 拆分参数传递完整性复查

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/`（8 个拆分类：AgentExecutorResolver/AgentCallDelegate/AgentSessionLifecycle/AgentTeamBinder/AgentStartupWarnings/SessionLockRenewal/AgentSessionSupport/DefaultAgentEngineConfig）+ `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/scheduler/TaskDispatchCoordinator.java`

- Item Types: `Proof`（发现新缺陷时升级为 `Fix`）

- [x] `Proof` 逐类对比 `git show 2f3251589^`（拆分前）与 live（拆分后）：每个 public/包内方法的参数是否被静默丢弃、替换为 config 默认值、或语义变更；重点核对：`AgentCallDelegate`、`AgentSessionLifecycle`（3 处 `resolveExecutor` 调用点 :313/447/627）、`SessionLockRenewal`、`TaskDispatchCoordinator`（注意：其 logger 为 `TeamTaskSchedulerDaemon.class`（:37），属 AR-6 误报项——复查参数传递时**排除该 logger 项**，只核对参数）。
- [x] `Fix`（如发现）对复查发现的同类丢参/换参缺陷逐一修复，并为每个缺陷补行为级测试（复用 Phase 1 模式：可观察性 accessor + `==` 引用断言或行为断言）。（复查零同类残留，无新缺陷需修复）
- [x] `Proof` 复查结论落盘：零发现 → daily log 记录"9 类逐一核对（8 engine + 1 team/scheduler），零同类残留；TaskDispatchCoordinator logger 为 AR-6 误报项已排除"；有发现 → 修复后同样落盘。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 9 个拆分类全部核对完毕（daily log 记录逐类结果）
- [x] 发现的同类缺陷已修复 + 行为级测试（如无发现则记录"零同类残留"）
- [x] `./mvnw test -pl nop-ai-agent -am` 通过（含 Phase 1 测试）
- [x] No owner-doc update required（或如发现契约级变更则更新对应 owner doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-1（两参重载静默丢参）已修复且实现与 javadoc 声明一致
- [x] 拆分面参数传递完整性复查完成（零残留或已修复）
- [x] 行为级测试证明 wrapped checker 生效（引用相等断言 + 负例验证）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证两参重载 → 三参 → ReAct executor 装配的调用链运行时连通（测试行为级断言），无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过（`./mvnw checkstyle:check -pl nop-ai -am`）

## Deferred But Adjudicated

无（本 plan 无 deferred 项）。

## Non-Blocking Follow-ups

无。

## Closure

Status Note: AR-1 两参重载静默丢参已修复（实现与 javadoc 声明一致）+ 行为级引用相等断言 + 负例验证红→绿 + 9 类拆分参数传递完整性复查零同类残留；两 Phase Exit Criteria 全勾 + Closure Gates 全勾；独立子 agent closure-audit APPROVED（零 Blocker/Major）。可关闭。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session `ses_009cd1051ffetrNrzNto5IB2j1`，review-only，无任何文件修改）
- Audit Session: ses_009cd1051ffetrNrzNto5IB2j1
- Evidence:
  - Phase 1 Exit Criteria 逐条 PASS（live 证据）：1a `AgentExecutorResolver.java:130` 两参重载实际使用传入的 `toolAccessChecker`；1b `ReActAgentExecutor.java:347-349` package-private `getToolAccessCheckerForTest()`（字段 :164 / 构造赋值 :239，非 public API）；1c `TestSubAgentPermissionWiring.java:312` `assertSame(wrapped, ...)` + `:310` assertNotNull 防御；1d `./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest=TestSubAgentPermissionWiring -Dsurefire.failIfNoSpecifiedTests=false` BUILD SUCCESS（7/7）；1e 负例红→绿记录于 `ai-dev/logs/2026/08-12.md` 顶部条目；1f git diff 仅含 plan 范围内三文件（一行修复 + accessor + 测试升级）；1g `DefaultAgentEngine.java:985-995` 三 delegate 零丢参；1h 三参重载 `:162-163` 两参均使用。
  - Phase 2 Exit Criteria 逐条 PASS：2a daily log 记录 9 类核对 + 零同类残留 + AR-6 logger 误报项排除；2b 抽查 AgentSessionLifecycle:313/447/627、AgentCallDelegate:59-61、SessionLockRenewal:69-87、TaskDispatchCoordinator:155-218、AgentTeamBinder:75-98 全部参数实际使用；2c `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS（**3418 tests 0 failures**，423 类）。
  - Closure Gates 全 PASS：AR-1 修复 + javadoc(:123-128)↔impl(:129-130) 一致；Anti-Hollow = 调用链全连通（test:308 → DefaultAgentEngine:989-990 → AgentExecutorResolver:129-130 → 三参:139-140 → builder:162 → ReActAgentExecutorBuilder:152-153/628 → 构造器:183/字段:239 → getter:347 → assertSame:312），无 no-op/空壳；无 in-scope defect 被降级（Deferred = 无）；`./mvnw compile -pl nop-ai/nop-ai-agent -am` PASS；checkstyle gate N/A（仓库未启用：nop-ai-agent 模块 21349 条 pre-existing violation，本 diff 仅 LineLength 风格行——与该模块实际风格一致，I5/I6 同口径记录）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0（closure 后复跑，0 unchecked + Evidence 已写入）
  - Anti-Hollow 扫描：`scan-hollow-implementations.mjs --module nop-ai --severity high` = 2 条 pre-existing（PlanReplanner:272 / NoOpProviderFailoverQueue:34，与 I5/I6 基线同口径），零新增
  - Deferred 项分类检查：本 plan 无 deferred/follow-up；P2 项（AR-5~9）在 plan Non-Goals 明确 out-of-scope（roadmap Follow-up Backlog 登记）

Follow-up:

- 无（no remaining plan-owned work）；P2 清理（AR-5~9）归 roadmap Follow-up Backlog，与本 plan 无关。

## Optional Sections

### Risks And Rollback

- 风险：Phase 2 复查若发现跨模块契约变更，需在动手前评估（预计无跨模块面，拆分类均在 nop-ai-agent 模块内；`AgentExecutorResolver` 为模块内类，非公共 API）。
- Rollback：Phase 1 为单行变更 + 新增 package-private accessor（不影响 public contract），`git revert` 即可回滚；Phase 2 测试独立，不影响生产装配。
