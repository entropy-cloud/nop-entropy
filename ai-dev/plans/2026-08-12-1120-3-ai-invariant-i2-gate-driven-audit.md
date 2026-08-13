# 3 AI Invariant Loop I2 — 不变式驱动审计

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 1 / I2. 不变式驱动审计
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` §I2；`ai-dev/skills/invariant-loop-audit-prompt.md`（I2 = 跑门禁 + 对抗探查）
> Related: `ai-dev/plans/2026-08-12-1120-2-ai-invariant-i1-gate-codification.md`（I1 门禁）；`ai-dev/audits/nop-ai-invariants/invariant-catalog.md`（I0 目录与目标集表）；I3 裁决计划（待创建，roadmap §I3）

## Purpose

运行 I1 五族门禁，产出结构化 **red list**（门禁覆盖缺口 = 已有但未声明 secure-default / timeout / 安全边界 / 清理对称性的实例 + 表完备性未登记成员），并对审计关闭后新增面做对抗探查（新增 Default* 子类 / 编排入口 / ToolExecutor 实现 / 兄弟路径），输出供 I3 裁决的零悬挂清单。

## Current Baseline

- I1 已产出（前置条件，本 plan 执行前须确认全绿）：
  - 门禁①②③（JUnit/ArchUnit，随 `./mvnw test -pl nop-ai -am -T 1C` 执行）、门禁④⑤（mjs：`ai-dev/tools/check-ai-tool-executor-boundary.mjs`、`check-fix-commit-diff.mjs`，脚本 `pnpm check:ai-tool-boundary` / `pnpm check:fix-commit-diff`，聚合入口 `pnpm check:ai-invariants`）。
  - known-gaps 清单（`ai-dev/audits/nop-ai-invariants/gate-gaps.*`，格式由 I1 Phase 1 裁定）：登记 I1 首跑发现的既有缺口，门禁对清单外缺口 fail；变更受 I1 Phase 1 策略约束（仅 I1 首跑 + I2/I3 裁决两个登记入口）。门禁⑤输出为违规 commit 清单（无 known-gaps）。
- 审计目标集：I0 四张表（Default* / 编排入口 / ToolExecutor / entry-point）带复现命令。
- 对抗探查背景（live 可查）：nop-ai 最近审计关闭于 2026-07-31（`ai-dev/audits/2026-07-31-*arm-*nop-ai*`、MR 系列 plan）；`nop-ai-agent-audit-tracker.md` 记录已修复实例（但**不含 MA6.2/ToolExecutor 安全族行**——该族证据在 `arm-index.md` + `2026-07-31-arm-MA6.2-nop-ai-agent-security.md` + Lesson 08，兄弟路径探查须以这三者为种子源）；其后产品演进（responses migration plans 325-330、agent 功能 plan 1437-1505 系列、nop-ai-gateway 新通道面）可能引入新 Default*/编排入口/ToolExecutor。
- **门禁⑤的处理约定**：I2 记录其输出；违规 commit（零实质 diff）→ red-list 条目交 I3，裁决选项含「重新验证/文档更正」（历史 commit 无法在 I4 被代码修复，须 I3 给出处置路径）。
- **owner-doc drift 路由**：探查若发现 docs-for-ai 与实际代码不一致，记录为 red-list finding 交 I3 裁决（不得静默忽略，guide rules 13/16）。
- 本 plan 不重新执行 6 deep/ARM 全量审计——门禁 + 定向探查覆盖 Cycle 1 范围。

## Goals

- 五族门禁全量运行，输出 **red list 文档** `ai-dev/audits/nop-ai-invariants/red-list-2026-08.md`：每个 finding 含（不变式族 / 实例（`文件:行`）/ 证据（门禁输出或代码路径）/ 建议修复面 / 严重度候选），供 I3 裁决，零悬挂（每个 finding 归属唯一族）。
- 对抗探查覆盖两个方向：① 审计关闭后新增面（新 Default* / 新编排入口 / 新 ToolExecutor / 新 entry-point）；② 已修复实例的兄弟路径抽查（同族未报实例的主动搜寻）。
- 门禁缺口清单与探查发现合并去重，red list 中每条 finding 可追溯证据。

## Non-Goals

- **不裁决**（P0/P1/P2 分级、修复/观察裁定属 I3）。
- **不修复**（I4）。
- **不验证修复**（I5）、**不收口**（I6）。
- 不跑全量 deep audit 维度（MA1-MA7 类全面审计不在本 plan——roadmap 明确 Cycle 1 以门禁覆盖为主、探查为辅）。

## Scope

### In Scope

- 五族门禁运行与缺口采集（含 known-gaps 清单 diff 核对）。
- 对抗探查（新增面 + 兄弟路径）。
- red list 文档编写与交叉核对。

### Out Of Scope

- 裁决表（I3）、修复执行（I4）、全量验证（I5）、收口判定（I6）。

## Execution Plan

### Phase 1 - 门禁运行与缺口采集

Status: completed
Targets: `nop-ai/*/src/test/**`（门禁测试）、`ai-dev/tools/*.mjs`、`ai-dev/audits/nop-ai-invariants/`

- Item Types: `Proof`

- [x] `Proof` **前置校验（fail-fast）**：确认 I1 已落地（门禁脚本 `check-ai-tool-executor-boundary.mjs` / `check-fix-commit-diff.mjs` 与 pnpm scripts 存在、known-gaps 清单存在、门禁测试类存在于测试目录）；不满足则停在该项并记录，不得在门禁缺失时假装运行（防静默跳过）。
- [x] `Proof` 运行门禁①②③（`./mvnw test -pl nop-ai -am -T 1C` 中门禁测试类）与门禁④⑤（mjs scripts：`pnpm check:ai-tool-boundary` / `pnpm check:fix-commit-diff` / 聚合 `pnpm check:ai-invariants`），记录每条门禁输出（含退出码/命中列表/违规 commit 清单）。
- [x] `Proof` known-gaps 清单 diff 核对：门禁实际缺口集 vs 清单——差异（清单外新缺口）逐条记录为 finding（表完备性/棘轮违约候选）。
- [x] `Proof` 交叉核对：每条缺口 finding 回到目标集表与 live 代码确认存在性（文件:行），排除门禁误报（门禁 bug 而非真实缺口 → 记为门禁缺陷 finding，属 I3 裁决范围）；**边缘类显式裁决**：如 `DefaultGuardrailGrader`（src/main 中路径含 `/test/` 段）若被门禁①标记为未登记，须按 I0 裁定记录「排除+理由」或「补入表」，不得悬置。
- [x] `Proof` 门禁⑤输出记录：违规 `fix(nop-ai)` commit 清单（2026-07-31 后），交 I3 裁决（重新验证/文档更正/接受）。

**Phase 1 执行记录（2026-08-12）**：

- 前置校验 PASS：门禁测试类 4 个（`TestInvariantGate1SecureDefault` / `TestInvariantGate2OrchestrationTimeout` / `TestInvariantGate3EntryPointCleanup`（agent）+ `TestInvariantGate1SecureDefaultShell`（shell））、mjs 脚本 2 个、pnpm scripts（`check:ai-tool-boundary` / `check:fix-commit-diff` / `check:ai-invariants`）、known-gaps 清单（gate-gaps.yaml）全部存在。
- 门禁①（secure-default）：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS；surefire 实测 agent `TestInvariantGate1SecureDefault` 5 tests / 0 fail + shell `TestInvariantGate1SecureDefaultShell` 3 tests / 0 fail。表完备性：`find nop-ai -name "Default*.java" -not -path "*/target/*" -not -path "*/test/*" -not -path "*/_gen/*"` = 33 行 == §3.1 表 33 条，零 diff。
- 门禁②（timeout）：`TestInvariantGate2OrchestrationTimeout` 3 tests / 0 fail。清单 5 条（4 not-applicable + 1 missing-declaration `SingleTurnExecutor.execute`），实判与清单一致（9 declared + 4 N/A + 1 missing）。
- 门禁③（清理对称性）：`TestInvariantGate3EntryPointCleanup` 3 tests / 0 fail。8/8 成对存在，清单空，零缺口。
- 门禁④（tool boundary）：`pnpm check:ai-tool-boundary` exit 0，PASS — 30 实例全部声明成立（29 成立 + 1 清单内 not-applicable `AskOracleExecutor`），表完备性一致；`LocalToolFileSystem.isPathAllowed→resolveFile` 接线 OK；`UpdateTodosExecutor` 表文件面/live 内存面偏差 note 记录。
- 门禁⑤（fix-commit）：`pnpm check:fix-commit-diff` exit 0，PASS — 扫描到 7 个 `fix(nop-ai)` 匹配 commit，全部实质 diff > 0，零违规。清单：`5ebad065e`（diff=23，chore(ci)，body 含 "fix(nop-ai)" 字样被 grep 匹配）/ `c1362dc77`（diff=616，feat(ai)，body 含字样）/ `f8a3c8bb6`（diff=10）/ `92dcb583b`（diff=46）/ `cb6850b38`（diff=152）/ `44118eec`（diff=316）/ `d72da2f66`（diff=302）。
- 交叉核对（live 证据）：
  - gate-1 清单 33/33 FQCN 与 find 输出逐条一致；`DefaultGuardrailGrader`（`nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/test/`）边缘类按 I0 裁定记录「排除+理由」（guardrail 测试辅助 grader，非引擎/安全组件；被复现命令 `-not -path "*/test/*"` 排除），与 catalog §3.1 边缘类裁定一致，不悬置。
  - gate-2 `SingleTurnExecutor.execute`（`SingleTurnExecutor.java:29`）live 确认无 timeout 标记（直接 `chatService.call(request, null)`，无 orTimeout/get-with-timeout/配置引用）——missing-declaration 真实缺口；`forkSession`（`DefaultAgentEngine.java:622`）live 确认仅 `sessionStore.forkSession` 注册子会话、不启动执行路径——not-applicable 理由成立；`IAgentEngine` 公共方法 live 计数 10 个（sendMessage/execute/forkSession/getSessionStatus/cancelSession/resumeSession/restoreSession/wakeSession/restorePendingSessions/close）与 catalog §3.2 一致。
  - gate-4 `AskOracleExecutor`（`AskOracleExecutor.java:14`）live 确认 fail-fast（ORACLE_ENDPOINT 缺失或 client 未实现即 errorResult，无实际网络 I/O）——not-applicable 理由成立。
- 门禁缺陷/精度观察项（记入 red list 交 I3）：门禁⑤ `--grep=fix(nop-ai)` 匹配 commit **body** 含该字符串的非 `fix(nop-ai)` commit（本次 2 个：chore(ci)/feat(ai) 的 I1 门禁 commit）。本次两 commit 均有实质 diff 未误报违规，但 body 提及 fix(nop-ai) 而产品 diff 为零的 chore/feat commit 会被误报——门禁精度 finding（I3 裁决：收窄 subject-only 匹配或接受现状）。

Exit Criteria:

- [x] 五族门禁输出全部记录（含退出码/命中列表），存档于 red-list 文档或 daily log
- [x] 清单 vs 实际缺口差异表完整（零差异或逐条列出）
- [x] 每条 finding 带 live 代码证据（`文件:行`），无证据 finding 已显式标注「待 I3 复核」
- [x] 无 owner-doc update required（red list 为 ai-dev 审计产物）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 对抗探查

Status: completed
Targets: `nop-ai/*/src/main/**`（**全模块组：agent/core/toolkit/shell/gateway/service/coder/tools/rag 等，排除 MCP**——与 mission scope 对齐）

- Item Types: `Proof | Follow-up`

- [x] `Proof` 新增面探查：用 **git 时域 diff**（而非 I0 复现命令——后者是快照无时域维度）识别「审计关闭后新增/重命名」的 Default* 类 / 编排入口 / ToolExecutor / entry-point，例如 `git log --since="2026-07-31 23:59" --diff-filter=ACDMR --name-only -- 'nop-ai/**'`（排除 `_gen/`、`_*.xmeta`、测试与前端资源路径）交叉 I0 目标集表，并逐一对五族不变式人工核对（这些实例即使已入门禁表也需行为级确认——接口存在 ≠ 语义成立，Lesson 08 接线教训）。已知新面示例：`DefaultGuardrailGrader`、`DefaultWaitCoordinator`、`ReadRefExecutor`、`nop-ai-gateway/.../ChannelMessageServiceImpl.dispatchInbound`（新派发入口，timeout/at-least-once 族直系兄弟）。
- [x] `Proof` 兄弟路径探查：以 **tracker ✅ 行 + `arm-index.md` + `2026-07-31-*arm-*nop-ai*` + Lesson 08 实例**为种子（tracker 不含 MA6.2 工具族行，须补足），按其族 grep 全类兄弟，搜寻未报实例（类别清扫的反向验证——门禁是否漏网）。
- [x] `Proof` 接线抽查：对「校验函数存在」类实例（Lesson 08 模式）追踪运行时调用链，确认声明 ≠ 接线（如 isPathAllowed 类问题在新增执行器上是否复发），覆盖全部安全敏感 ToolExecutor（网络/文件/命令类）。
- [x] `Proof` 探查发现汇总：非门禁面观察项记录（供 I3 参考，不得取代 finding）；owner-doc drift（docs-for-ai 与 live 代码不一致）记录为 finding 交 I3，不得静默忽略、不得降级为 follow-up（guide rules 15/16）。

**Phase 2 执行记录（2026-08-12）**：

- **新增面 diff**（`git log --since="2026-07-31 23:59" --diff-filter=ACDMR --name-only -- 'nop-ai/**'`，排除 test/_gen/_dump/resources）逐类核对：
  - `DefaultGuardrailGrader`（新增 2026-08-02）→ I0 边缘类裁定排除理由 live 确认成立（guardrail 测试辅助 grader，非引擎/安全组件），不悬置。
  - `DefaultWaitCoordinator`（新增 2026-08-01）→ 门禁①表 #7 + gate-gaps 已登记；行为级确认：TIMEOUT 条件注册时经 `IScheduledExecutor` 调度 `deliverWake`（:78-82），有 timeout 机制；shipped default = `NoOpWaitCoordinator`（WAIT_FOR opt-in 设计，设计 §13.1 Decision G），非缺口。
  - `ReadRefExecutor`（新增，plan 2026-08-02-0900 引用式压缩）→ 门禁④表 #25 已覆盖；接线确认：仅用有界抽象 `ICompactionArchiveReader`（context 桥），hash 校验 + fail-loud，声明成立。
  - `ChannelMessageServiceImpl.dispatchInbound`（新增 2026-08-08）→ **finding R-2-1**（见 red list）：新派发入口无 timeout 声明（mode-1 同步 fan-out 无包装、mode-2 发布 messageService），timeout/at-least-once 族直系兄弟。
  - plan/runtime 包（`PlanExecutor`/`PlanRunner`/`TaskRunner`/`PlanScheduler`/`StagnationDetector` 等，新增 2026-08-01 系列）→ **finding R-2-2**：无 timeout 标记（无 orTimeout/timeout 配置引用）+ **生产零接线**（`TaskRunner` 无产品实现，仅测试构造 `PlanExecutor`；`grep -rln "plan.runtime" src/main` 仅 AgentPlan 引 AgentPlanValidator）——编排入口候选 + Anti-Hollow 观察项。
  - `TeamTaskFlowOrchestrator.executeAsync` / `MemberFanOutDispatcher.dispatch`（live 存在）→ **finding R-2-3**：**I0 §3.2 排除理由「不启动 agent 执行路径」被 live 证据推翻**——`MemberFanOutDispatcher.java:305` 直接 `agentEngine.execute(request)` 启动 agent 执行路径且无 timeout；`TeamTaskFlowOrchestrator.executeAsync` 亦无 timeout 标记 → gate-2 表完备性缺口 + owner-doc drift（catalog §3.2 排除表）。
  - `TaskDispatchCoordinator`/`TeamTaskSchedulerDaemon`（新增）→ 有 `awaitInFlightDispatches(timeoutMs)` deadline 语义（:121-137）、start/stop 对称（schedule cancel + `shutdownOwnSpawnExecutor`），INV-3 无缺口。
  - `SsrfGuardDnsResolver`（plan-336，2026-08-07 新增）→ **finding R-4-1**：实现 `IDnsResolver` 但**生产零接线**（无 bean 配置、无 `setDnsResolver` 调用点，仅单测 `TestSsrfGuardDnsResolver` 引用）；HttpRequestExecutor/GraphqlQueryExecutor 的 host 级校验（`SsrfAddressGuard.validateHost`）已接线 ✓（:63/:71 调用点 live 确认）。
  - `DeferredAckMailbox`（新增 message 族）→ 内存消息原语（bounded capacity/back-pressure/dead-letter），非编排入口，INV-2 不适用。
- **兄弟路径探查**（五族全覆盖）：
  - INV-1：find 命令 33 行 == §3.1 表 33 条（gate-1 已覆盖）；DefaultGuardrailGrader 边缘裁定 live 确认；零漏网 Default* 类。
  - INV-2：种子 live 确认（`LlmCallCoordinator.callChatWithTimeout:605` 调用点 :218、`AgentToolDispatcher` per-tool orTimeout :232、`CallAgentExecutor` resolveTimeoutMs :211 + dispatch 链）——已修复实例接线成立；**新缺口** = R-2-1（dispatchInbound）、R-2-2（plan/runtime）、R-2-3（team-flow fan-out）。
  - INV-3：gate-3 8/8 成对；新面 start/stop 对称核对：TeamTaskSchedulerDaemon（cancel+shutdownOwnSpawnExecutor）、DBMessageService close（poller shutdownNow）、TeamTaskFlowOrchestrator close（ownedSpawnExecutor.shutdownNow）、InMemoryActorRuntime（executor shutdown + shutdownTimeoutMs）——零新缺口。
  - INV-4：30/30 声明成立（gate-4）；接线抽查 live 确认：`LocalToolFileSystem.isPathAllowed`→`resolveFile`（:53-54）、`HttpRequestExecutor.validateUrl`→`SsrfAddressGuard.validateHost`（:63/:84）、`GraphqlQueryExecutor.validateUrl`（:71）、`BashExecutor.validateCommand`（:103）+ plan-335 sandbox fail-closed（无 backend 即拒绝，:91-93）；`ReadRefExecutor` 有界抽象 + fail-loud；**新缺口** = R-4-1（SsrfGuardDnsResolver 零接线）。
  - INV-5：门禁⑤ 7 commit 零违规；**门禁精度观察项 R-5-1**：`--grep=fix(nop-ai)` 匹配 commit body 含该字符串的非 fix commit（本次 2 个 I1 门禁 commit 被扫入，diff>0 未误报）。
- **owner-doc drift**：catalog §3.2 排除理由 vs live `MemberFanOutDispatcher.java:305` 矛盾（见 R-2-3）；其余 docs-for-ai 面本 plan 探查范围内未发现 drift。

Exit Criteria:

- [x] 新增面 diff 清单完整（新增/重命名逐一列出并标注核对结果）
- [x] 兄弟路径探查覆盖全部五族，每族至少 1 条探查结论（发现或明确排除）
- [x] 接线抽查覆盖全部安全敏感 ToolExecutor（网络/文件/命令类）
- [x] 每条探查 finding 带 live 证据；排除项注明「核对过，无问题」
- [x] 无 owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - red list 汇总与交叉核对

Status: completed
Targets: `ai-dev/audits/nop-ai-invariants/red-list-2026-08.md`

- Item Types: `Fix | Proof`

- [x] `Fix` 编写 red-list 文档：门禁缺口（含门禁⑤违规 commit 清单、owner-doc drift 条目）+ 探查发现合并去重，统一格式（族 / 实例 / 证据 / 建议修复面 / 严重度候选），标注来源（门禁输出 vs 探查）。
- [x] `Proof` 零悬挂核对：每条 finding 归属唯一族且指向唯一 successor 路径（I3 裁决）；无「未分类」游离项。
- [x] `Fix` 与 I0 目录/I1 门禁双向核对：finding 的族与不变式编号一致；建议修复面与门禁判定标准一致。
- [x] `Proof` 独立子 agent 审查 red list 至共识（零 Blocker）。

**Phase 3 执行记录（2026-08-12）**：

- 产出 `ai-dev/audits/nop-ai-invariants/red-list-2026-08.md`：§1 汇总表（五族 × 门禁/探查计数）、§2 门禁缺口（39 条：gate-1 33 + gate-2 5 + gate-4 1，全带 live 证据）、§3 探查 finding 5 条（R-2-1 dispatchInbound 无 timeout / R-2-2 plan/runtime 零 timeout + 零接线 / R-2-3 team-flow fan-out 启动 agent 执行无 timeout + catalog §3.2 排除理由被推翻 / R-4-1 SsrfGuardDnsResolver 零接线 / R-5-1 门禁⑤ --grep 匹配 body 精度）、§4 探查排除项（含核对过无问题的五族结论）、§5 门禁输出归档（退出码/命中列表）、§6 独立审查记录。
- 零悬挂核对：44 条 = 门禁 39 + 探查 5，合并去重前后计数一致（探查 5 条与门禁清单无重叠）；每条唯一族归属（R-2-2/R-2-3 双标签均为主族 INV-2 + 过程标签，不违反唯一族规则）；无未分类游离项；每条含（族/实例/证据/建议修复面/严重度候选），I3 可直接裁决。
- 双向核对：R-2-3 与 catalog §3.2 排除表对照（排除理由 vs live 代码矛盾显式记录）；R-5-1 与门禁⑤脚本 `--grep=fix(nop-ai)` 用法对照；gate-2 与 §3.2 判定标准对照（gateway 包在表外 = 表完备性扩展候选，交 I3）。
- 独立审查：fresh session（general，task `ses_00b7fab7fffeoYnqMNCn4behLA`）1 Blocker + 1 Major + 4 Minor 全部修订（见 red-list §6 逐条记录）；核心 finding 面全部 live 成立，verdict revised → approved。
- 验证：`node ai-dev/tools/check-doc-links.mjs --strict` 覆盖（见 daily log）。

Exit Criteria:

- [x] red-list-2026-08.md 存在，零悬挂（族归属 100%）
- [x] 每条 finding 可追溯证据（门禁输出文件/命令或代码路径）
- [x] 门禁缺口与探查发现去重后无遗漏（合并前后计数核对记录）
- [x] 独立子 agent 审查意见与修订记录写入文档尾部或 daily log
- [x] 无 owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] red list 零悬挂，每条 finding 归属唯一不变式族且证据可追溯
- [x] 五族门禁输出 + 新增面 diff + 兄弟路径探查结论全部归档
- [x] 门禁误报与门禁缺陷（若有）已单独标注并计入 I3 裁决范围（未静默忽略）
- [x] 不存在被静默降级的 in-scope 项（探查发现不得因「无修复面」而丢弃——裁决归属 I3）
- [x] 受影响的 owner docs 无需更新（`No owner-doc update required`，ai-dev 审计产物）
- [x] 独立子 agent 完成 closure-audit 并记录证据
- [x] **Anti-Hollow Check**：red list 中每个 finding 均经 live 代码确认（非仅门禁清单转抄）；接线抽查证明「校验函数存在 ≠ 已接线」模式检查真实执行
- [x] `./mvnw compile -pl nop-ai -am`（本 plan 不改产品代码，仅确认未破坏基线）
- [x] checkstyle / 代码规范检查通过（未改代码，N/A 或零变更确认）

## Deferred But Adjudicated

None（本 plan 的探查观察项与 owner-doc drift 均记录为 red-list finding 交 I3 裁决，不在此处延期；超出五族门禁面的观察项由 I3 按 Loop Rule 预授权路径决定是否派生 Cycle 2 新不变式）

## Non-Blocking Follow-ups

- 门禁运行效率观察（若某门禁运行显著慢，I5 前优化）。
- 探查工具化候选：若兄弟路径探查发现高频模式，建议在 I6 收口时评估沉淀为第六门禁族（Cycle 2 派生输入）。

## Closure

Status Note: I2 全量执行完毕——五族门禁运行（① 5/5+3/3 ② 3/3 ③ 3/3 surefire 全绿、④⑤ mjs exit 0）产出 known-gaps diff 零差异；对抗探查（git 时域 diff + 兄弟路径 + 接线抽查）产出 5 条新 finding（R-2-1/2/3、R-4-1、R-5-1）；red list 44 条（门禁 39 + 探查 5）零悬挂、每条带 live 证据 + 建议修复面 + 严重度候选，交 I3 裁决。独立子 agent 审查（1 Blocker + 1 Major + 4 Minor 全部修订）+ 独立 closure audit（零 Blocker 零 Major，2 Minor 已收口）均通过。本计划零产品代码改动，满足全部 Exit Criteria 与 Closure Gates，Plan 关闭。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session，task `ses_00b789c6affeaYuUpijnVaRRc1`，review-only）
- Evidence:
  - **Phase 1 PASS**：surefire 报告 live 复跑全绿（`TestInvariantGate1SecureDefault` 5/0、`Gate2OrchestrationTimeout` 3/0、`Gate3EntryPointCleanup` 3/0、shell `Gate1SecureDefaultShell` 3/0，时间 13:34）；门禁④⑤ mjs 复跑 exit 0（7 commit diff 23/616/10/46/152/316/302 与记录一致）；`gate-gaps.yaml` 39 条（33+5+1 awk 分节计数）；`find` 33 行 == §3.1 表 33 行零 diff。
  - **Phase 2 PASS**：R-2-3 live 实测 `MemberFanOutDispatcher.java:305` `agentEngine.execute(request)` + 全文件零 orTimeout、`TeamTaskFlowOrchestrator.executeAsync` :513，catalog §3.2 排除理由确被推翻；R-4-1 全仓引用面 = 自身 + 单测 + 2 javadoc、XML bean 零命中、新增 commit 2026-08-08；R-2-2 `import io.nop.ai.agent.plan.runtime` src/main 仅 1 命中 + `TaskRunner` 零产品实现；R-2-1 `dispatchInbound` :208 全类零 timeout 标记；R-5-1 `--grep=fix(nop-ai)` body 匹配实测。
  - **Phase 3 PASS**：red-list-2026-08.md 存在（120 行）；44 计数（39+5）自洽零重叠；§6 审查记录 1 Blocker + 1 Major + 4 Minor 逐条修订对应。
  - **checklist 完整性**：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-12-1120-3-ai-invariant-i2-gate-driven-audit.md --strict` 退出码 0。
  - **Anti-Hollow**：red list 每条 finding 带 live `文件:行` 证据（R-2-1/2/3、R-4-1、R-5-1 逐条实测）；接线抽查真实执行（LocalToolFileSystem:41→:53-54、HttpRequestExecutor:63/84、GraphqlQueryExecutor:71、BashExecutor:103、ReadRefExecutor 有界抽象）——「校验函数存在 ≠ 已接线」模式检查覆盖网络/文件/命令类 ToolExecutor。
  - **Deferred 分类**：§Deferred = None；全部 finding（39 门禁缺口 + 5 探查 + owner-doc drift R-2-3）交 I3 唯一 successor，无 in-scope live defect 被降级为 follow-up。
  - **文本一致性**：Plan Status / Phase 1-3 Status / Closure Gates 全 completed；2 Minor（daily log I2 条目缺失、roadmap I2 行未翻转）已在本 closure pass 收口（见 `ai-dev/logs/2026/08-12.md` I2 条目 + roadmap `todo`→`done`）。
  - **构建基线**：`git status` 确认 nop-ai/** 无 Java 源码改动（仅 ai-dev 文档 + red-list 新增）；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（13:35 运行）；`./mvnw compile -pl nop-ai -am -T 1C` PASS；checkstyle 未改代码 N/A；doc-links 20 个 pre-existing 错误非本次引入（red-list 与 I2 plan 零新增断链）。

Follow-up:

- 44 条 red-list finding 交 I3 裁决（`ai-dev/plans/2026-08-12-1120-4-*` successor 计划待创建，roadmap §I3）。
- 门禁运行效率优化 + 第六门禁族候选（兄弟路径探查发现高频模式时沉淀）= non-blocking（Non-Blocking Follow-ups 段原文）。
- no remaining plan-owned work。
