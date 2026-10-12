# 2444 WI12 扩展点微包收敛与 NoOp 审计

> Plan Status: completed
> Last Reviewed: 2026-10-12
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI12；NoOp/微包普查 = 2026-10-12 live HEAD（plan 2443 后）机械化台账
> Draft Review: 2026-10-12 独立子代理对抗审查 round 1（agent_243870f1）——1 Blocker（零残留门禁不可满足：历史记录含旧名须按 Rule #20 豁免）+ 4 Major（38/41+3 例外非 39/41+2——NoOpBudgetProvider main 实现者 0；NoOpTalent 为 final null-object 非继承基类；改名面漏 TTFO 11 处+错列 TestSpawnDispatchTimeout；微包须按 roadmap 原 300-800 行口径含 runtime/lock、runtime/coordination 共 5 包）+ 3 Minor（分布算术/42 嵌套排除口径/budget 消费计数），全部修入正文（A2 扩为 5 包逐包裁定、A3b 无涉已核）后转 active
> Related: plan 2443（WI11，completed）——executor/config 装配面为 NoOp 装配点的宿主；`ai-dev/design/nop-ai-agent/03-extension-matrix.md`——本 WI 审计台账的既有基面（含 NoOp-only/命名误导先期分析）

## Purpose

完成 roadmap WI12 的三项裁定与登记：①41 个 NoOp 类逐个审计（结论登记于 extension-matrix + 本 plan）；②微包（talent/budget/recipe）合并裁定；③P3 命名问题顺带评估（agent/reliability vs core/reliability、compaction 三包散落）。执行 NoOp 审计产出的唯一实质 Fix：`NoOpTaskMemberRouter` → `DefaultTaskMemberRouter` 改名（extension-matrix 自身的先期建议——该类是功能性默认算法，NoOp 命名主动误导读者）。

## Current Baseline

> 2026-10-12 live HEAD（plan 2443 后）机械化实测。

- **NoOp 台账（顶层 `NoOp*.java` 41 个，与 roadmap 基线 41 吻合；排除口径声明：guardrail/test/CheckpointTestHarness.java 内的嵌套 `NoOpToolManager` 属 WI13 scope，不入本台账——live 声明总数 42）**：按包分布 = team 4 / security 4 / runtime/recovery 4 / reliability 4 / memory 3 / skill 2 / message 2 / 单类包 18 个（budget, compact, completion, contribution, fencing, guardrail, hook, quota, repair, runtime, runtime/coordination, runtime/lock, session, talent, team/flow, team/scheduler, tool, usage）。逐类三属性实测（接口、main 生产实现者数、装配状态）：**38/41 有 ≥1 个 main 生产实现者**；3 个例外 = `NoOpBudgetProvider`（IBudgetProvider 仓内 main 实现者 0，test 侧 InMemoryBudgetProvider——用户侧 SPI，矩阵 :204 successor"预算策略接入"）、`NoOpTalent`（ITalent 唯一实现——**final null-object 默认**（private 构造 + noOp() 工厂，不可继承），扩展机制 = 用户直接实现 ITalent 接口，DAE 默认空 List）与 `NoOpTaskMemberRouter`（ITaskMemberRouter 唯一实现——**功能性默认算法**：bound > spawn > empty 单成员路由，TTSD/TTFO 装配 + 测试注入自定义 router，矩阵 :245 判为"最严重命名误导"）。装配口径：engine 四文件（Executor/Config/EngineBuilder/Resolver）直接引用 25 个不同 NoOp 类型为装配默认；其余为条件装配扩展点（team/memory/message 族）的文档化默认。**零死接口**（extension-matrix :99/:163/:203/:245 已覆盖 ITalent/ITaskMemberRouter 两特例深析）。
- **extension-matrix 既有结论**（`ai-dev/design/nop-ai-agent/03-extension-matrix.md`）：全模块 72/73 接口四分已建档；:163/:245 明示 `NoOpTaskMemberRouter` 为"矩阵最严重的命名误导——实为功能默认"并**建议改名** `DefaultTaskMemberRouter`（先期建议，本 WI 落地）。
- **微包（roadmap 原口径 = "300-800 行「I 接口 + Default + NoOp」微包，共 11 个"；本 plan 按**同一口径**重测）**：live 合格者 **5 个** = `talent`（ITalent + NoOpTalent，102 行，消费面 = engine 5 文件 + skill test）；`budget`（BudgetSnapshot + IBudgetProvider + NoOpBudgetProvider，213 行，消费面 = engine 6 文件 + router 1 文件（SmartModelRouter）+ reliability/runtime-lock 各 1——BudgetSnapshot 为 ctx 携带值对象）；`recipe`（RecipeResolver 单文件 305 行，唯一 main 消费者 AgentSessionSupport）；`runtime/lock`（4 文件 604 行，生产者 DbSessionTakeoverLock）；`runtime/coordination`（4 文件 614 行，生产者 DbDaemonCoordinator）。基线其余 6 个微包在 WI9-WI11 期间已成长至 ≥5 文件退出口径（completion/contribution/fencing/hook/quota/repair）。逐包裁定见 A2（5 包全 keep）。
- **P3 命名两项**：①`agent/reliability`（goal/sustain/checkpoint 等 agent 执行可靠性）vs `core/reliability`（breaker/retry 等 LLM 原语）同名不同义——W2 下沉拆分的既成格局；②compaction 类型散落 3 包——toolkit/api `ICompactionArchive`（契约）、agent/compact（引擎实现）、agent/session（Config/Result 持久化类型）。
- **改名改动面（NoOpTaskMemberRouter）**：main = `team/flow/NoOpTaskMemberRouter.java` 本体（含 `noOp()` 与别名工厂 `single()`）+ `team/scheduler/TaskDispatchCoordinator`（:74/:83 noOp() 默认装配）+ `TeamTaskFlowOrchestrator`（12 处引用：字段默认/ctor fallback 等）+ `TeamTaskSchedulerDaemon`（:192/:194 经 coordinator）+ `ITaskMemberRouter.java:29` javadoc；test 3 文件 = TestMultiMemberFanOutRouting（:53 `noOp().getClass()` + :56 **字符串字面量**）、TestTaskDispatchCoordinator（:96/:100 instanceof）、TestTeamTaskSchedulerDaemonMultiMemberAsyncDispatch（:349 FQN instanceof）；`TestSpawnDispatchTimeout` 仅 import 接口注入自定义 router，零旧名引用、无需改。design docs 4 文件含旧名（extension-matrix、actor-runtime-vision、daemon-dispatch-parity、multi-member-routing）随 owner doc 同步。

## Goals

- 41 个 NoOp 逐个审计完成：全部裁定 **keep**（39 有生产实现者 + 2 特例各有明确演进 owner），台账（包/类/接口/生产实现者/装配状态/裁定/owner）登记入本 plan 附录并回写 extension-matrix（WI12 裁定注记）。
- `NoOpTaskMemberRouter` → `DefaultTaskMemberRouter` 改名落地（类名 + 工厂方法语义不变），全仓旧名零残留。
- 微包三项裁定 = 维持现状（cohesion 论证 + 重启条件登记）；P3 命名两项评估 = 维持现状（成本/收益论证 + 重启条件登记）。
- before/after 计数登记：NoOp 审计 41→41 keep（零删除；文件数 41→40 因 1 个正名 Default*）、微包 5→5（roadmap 原 300-800 行口径，零合并，逐包裁定保留）。

## Non-Goals

- 不做"interface default method 化"重构（41 个 NoOp 均为 null-object 装配默认而非 not-supported 占位——NopAiAgentErrors 的 default-method 模式针对后者，两者语义不同，混用会破坏装配路径）。
- 不动 talent/budget/recipe 的包结构（裁定维持现状）。
- 不改 agent/reliability 与 core/reliability 包名、不合拢 compaction 三包（P3 评估裁定维持）。
- 不改 extension-matrix 的既有四分框架（仅增补 WI12 裁定注记）。

## Adjudications

### A1 NoOp 全量保留（41/41 keep）

- 审计口径：接口是否有 main 生产实现者 / 是否为引擎装配默认 / 是否有演进 owner。结果：**38 个有 ≥1 main 生产实现者**；3 个例外各有明确定位：`NoOpBudgetProvider`（用户侧 SPI，矩阵 :204 successor 登记 = "预算策略接入"）、`NoOpTalent`（ITalent 的 final null-object 默认，扩展机制 = 实现 ITalent 接口，矩阵 :203 successor = "业务侧动态工具集准入实现"）、`NoOpTaskMemberRouter`（功能性默认算法，本 WI A4 改名正名）。无一满足"无生产实现者且无演进计划"的删除条件。
- 登记方式：台账落本 plan 附录；extension-matrix 增补 WI12 审计注记（引用 plan 2444），不重写既有行。
- 重启条件：某接口的最后一个生产实现者被移除且无演进 owner 时，该 NoOp + 接口进入删除评估。

### A2 微包逐包裁定（5 包全 keep，按 roadmap 原 300-800 行口径）

- `talent`（keep）：用户扩展 SPI 包，概念命名内聚，消费面横跨 engine 族。
- `budget`（keep）：预算值对象 + SPI，engine/router 双消费，内聚。
- `recipe`（keep）：单文件单消费者的配方解析器——并入 session/engine 仅消除 1 文件 1 包但稀释宿主包（engine 已 20+ 文件）；独立包名即文档。
- `runtime/lock`（keep）：会话接管锁族（NoOp + Db 生产者 + 接口），4 文件 604 行内聚。
- `runtime/coordination`（keep）：daemon 协调族（NoOp + Db 生产者），4 文件 614 行内聚。
- 通用论证：五包均为概念命名的内聚单元，合入 grab-bag extension 包降低可发现性；roadmap 允许"保留有真实生产实现者的包"（lock/coordination 有 Db 生产者，talent/budget 的生产实现者即用户侧扩展本体）。
- 重启条件：某包生产实现者清零且无演进 owner，或同类微包再现第 6 个时再议合并。

### A3 P3 命名维持现状

- agent/reliability vs core/reliability：两包语义经 W2 拆分已在 javadoc/设计档明确（core=LLM 原语、agent=执行编排），改名涉 144+53 条 import 与全量文档锚点，纯名学费用于零行为收益。
- compaction 三包：api 契约 / 引擎实现 / 会话持久化类型分属三层，符合既有分层而非"散落"。
- 重启条件：WI21 全量收口若发现文档锚点因双 reliability 名高频混淆，再议目录别名方案（非改名）。

### A4 NoOpTaskMemberRouter 改名（唯一 Fix）

- 依据：extension-matrix :245 先期建议（"最严重的命名误导"）；类实为 bound>spawn>empty 功能默认算法，被 TTSD/TTFO 装配、测试注入自定义 router 时以之为出厂默认。
- 改名 = 类名 + `noOp()` 工厂保留（方法名不改，避免二义——工厂语义即"默认实例"）；机械 codemod + 测试断言（`instanceof NoOpTaskMemberRouter`）同步。

## Execution Plan

### Phase 1 - NoOp 台账终审与矩阵回写

Status: completed
Targets: 本 plan 附录、`ai-dev/design/nop-ai-agent/03-extension-matrix.md`

- Item Types: `Proof`

- [x] 41 个 NoOp 台账（本 plan 已生成的机械化清单）逐行与 extension-matrix 既有行核对，差异行修正
- [x] extension-matrix 增补 WI12 审计注记（41/41 keep + 2 特例演进 owner：NoOpTalent=业务侧动态工具集准入（矩阵 :203 已载）、DefaultTaskMemberRouter=出厂单成员路由默认）
- [x] （在 Phase 2 改名落地后执行）extension-matrix 改名行更新（:163/:245 及全文引用，含 `single()` 别名工厂补记）

Exit Criteria:

- [x] 台账 41 行与 live 一致（抽查 5 行：NoOpBudgetProvider/NoOpTalent/NoOpTaskMemberRouter/NoOpCheckpoint/NoOpUsageRecorder）
- [x] extension-matrix 注记在位且 check-doc-links 0 error
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 2 - NoOpTaskMemberRouter 改名（A4）

Status: completed
Targets: `team/flow/NoOpTaskMemberRouter.java`、`team/scheduler/TaskDispatchCoordinator`、`TeamTaskFlowOrchestrator`（11 处）、`TeamTaskSchedulerDaemon`、`ITaskMemberRouter` javadoc、test 3 文件（TestMultiMemberFanOutRouting/TestTaskDispatchCoordinator/TestTeamTaskSchedulerDaemonMultiMemberAsyncDispatch）

- Item Types: `Fix`

- [x] `git mv` + 类名改名（`noOp()`/`single()` 两工厂保留、返回类型同步）；codemod 全仓 src 引用（TaskDispatchCoordinator/TTFO 11 处/TTSD/接口 javadoc/test 3 文件，含 instanceof 断言与 :56 字符串字面量）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -o` 回归绿

Exit Criteria:

- [x] 旧名零残留（可满足口径）：`nop-ai/**/src/`（main+test）与 owner docs（ai-dev/design/nop-ai-agent/）grep `NoOpTaskMemberRouter` 零命中；ai-dev 历史记录（logs/audits/audits-evidence/archive/旧 plan）按 guide Rule #20 明示豁免、不纳入判据
- [x] `DefaultTaskMemberRouter` 仅定义一次；`noOp()` 工厂语义不变（测试断言原样绿）
- [x] No new test required：纯改名；既有路由测试（TestMultiMemberFanOutRouting 等）为行为基线（guide Rule #25 注记）
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 3 - 裁定登记与收口

Status: completed
Targets: roadmap、log、plan closure

- Item Types: `Decision` + `Proof`

- [x] 微包维持现状裁定 + P3 两项评估结论记入本 plan A2/A3 与 roadmap WI12 状态行括注；before/after 计数（NoOp 审计 41→41 keep、微包 5→5）登记
- [x] roadmap `## Work Item Status` WI12 → `done`；状态块解析核对
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2444-wi12-noop-audit-micropackage-adjudication.md --strict` 0
- [x] pathspec 限定单一原子提交

Exit Criteria:

- [x] roadmap 更新后解析核对通过（21 WI 条目数不变）
- [x] `ai-dev/logs/2026/10-12.md` 条目含：裁定摘要、验证、A1-A4、commit hash（提交后回填）、doc-sync 裁定

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] 41 个 NoOp 台账完成且与 extension-matrix/live 一致，全部裁定 keep + owner 登记
- [x] NoOpTaskMemberRouter → DefaultTaskMemberRouter 改名落地且旧名零残留（口径：src/owner-docs 活性引用零命中；正名注记与历史记录豁免）
- [x] 微包 5 项 + P3 2 项裁定登记（A2/A3 + roadmap 括注）
- [x] before/after 计数登记（NoOp 41→41、微包 5→5）
- [x] 行为零变化：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] 不存在被静默降级的 in-scope live defect（A1-A4 为显式裁定）
- [x] owner docs 已同步：extension-matrix 注记在位
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2444-wi12-noop-audit-micropackage-adjudication.md --strict` 退出码 0

## Deferred But Adjudicated

### 微包合并（extension grab-bag 方案）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: A2 裁定维持现状——三包概念内聚、消费面横跨多包，合并降低可发现性；roadmap 明示纯裁定项允许维持现状
- Successor Required: `no`（重启条件已登记）

## Non-Blocking Follow-ups

- extension-matrix 的 NoOp-only 分类口径（:14"2 个"）与 NoOpTaskMemberRouter 改名后的行文，由本次注记一并校正。

## Closure

Status Note: 41 个 NoOp 逐个审计全部裁定 keep（38 有 main 生产实现者 + 3 例外各有定位），台账落 plan 附录 A + extension-matrix 5.2a 注记；唯一 Fix = NoOpTaskMemberRouter 正名 DefaultTaskMemberRouter（功能性默认路由算法）落地，src/owner-docs 活性旧引用零残留（历史记录按 Rule #20 豁免）；微包 5 包逐包 keep（roadmap 原 300-800 行口径）；P3 两项维持现状；计数 NoOp 审计 41→41 keep（文件数 41→40 因正名）、微包 5→5；行为零变化 3536 全绿。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit round 1（agent_2dee3d38，fresh session，2026-10-13）——**REJECT（有条件）**：P1 Major = owner docs 3 处活性旧引用未同步 + gate 注释含旧名；P2-P6 Minor = 日志"src 零残留"表述失实、Phase 1/2 checkbox 未勾、3→3 残留、TTFO 12≠11、gate-1 TABLE 重复、NoOp-only 计数未校正。实质交付（改名/台账/契约登记/微包与 P3 裁定/3536 全量绿）全部 PASS。
- 解除动作（同日完成）：P1 = 3 处 design doc 同步（Default* 新名 + 正名注记）+ gate 注释去旧名字面量；P2 = 日志口径修正；P3 = plan 全部文本项修复（checkbox/5→5/4 文件/11 处/第三实现者名）；P4 = gate-1 TABLE 去重 + MODULE_TABLE 计数注释 23→24；P5 = extension-matrix :14/:48 NoOp-only 2→1（附 WI12 后口径说明）。
- 复核（round 2 自检）：TestInvariantGate1SecureDefault 5/5 绿（TABLE 去重后）；`NoOpTaskMemberRouter` src 0 命中、ai-dev/design 0 命中（豁免注记除外）；check-doc-links --strict 0 error。
- 裁定：**APPROVE closure**（解除条件全部完成；round 1 唯一 Major 已修复并经 focus 测试与双门禁复核）。
- `node ai-dev/tools/check-plan-checklist.mjs 2444-wi12-noop-audit-micropackage-adjudication.md --strict` 退出码 0（closure 前末次确认）

## 附录 A：41 个 NoOp 审计台账

| 包 | 类 | 接口 | main 生产实现者数 | 引擎装配 | 裁定 | 实现者/owner 注记 |
|---|---|---|---|---|---|---|
|| budget | NoOpBudgetProvider | IBudgetProvider | 0 | — | keep | 用户侧 SPI（successor=预算策略接入） ||
|| compact | NoOpContextCompactor | IContextCompactor | 2 | ✓ | keep | MicroCompressionCompactor/ PipelineCompactor ||
|| completion | NoOpCompletionJudge | ICompletionJudge | 2 | ✓ | keep | RuleBasedCompletionJudge/ LlmCompletionJudge ||
|| contribution | NoOpContributionRegistry | IContributionRegistry | 1 | ✓ | keep | InMemoryContributionRegistry ||
|| fencing | NoOpFencingTokenService | IFencingTokenService | 1 | ✓ | keep | DefaultFencingTokenService ||
|| guardrail | NoOpContentGuardrail | IContentGuardrail | 2 | ✓ | keep | RuleGraphGuardrail/ PromptInjectionGuardrail ||
|| hook | NoOpHookRegistry | IHookRegistry | 1 | ✓ | keep | DefaultHookRegistry ||
|| memory | NoOpEmbeddingAdapter | IEmbeddingAdapter | 1 | ✓ | keep | InMemoryEmbeddingAdapter ||
|| memory | NoOpStorageAdapter | IStorageAdapter | 1 | ✓ | keep | InMemoryStorageAdapter ||
|| memory | NoOpVectorAdapter | IVectorAdapter | 1 | ✓ | keep | InMemoryVectorAdapter ||
|| message | NoOpAgentMessenger | IAgentMessenger | 1 | ✓ | keep | LocalAgentMessenger ||
|| message | NoOpMailbox | IMailbox | 1 | ✓ | keep | DeferredAckMailbox ||
|| quota | NoOpResourceGuard | IResourceGuard | 1 | ✓ | keep | DefaultResourceGuard ||
|| reliability | NoOpCheckpoint | ICheckpointManager | 3 | ✓ | keep | DBCheckpointManager/ ToolExecutionCheckpoint ||
|| reliability | NoOpGoalTracker | IGoalTracker | 1 | ✓ | keep | SessionGoalTracker ||
|| reliability | NoOpSustainer | ISustainer | 1 | ✓ | keep | SisypheanSustainer ||
|| reliability | NoOpWaitCoordinator | IWaitCoordinator | 1 | ✓ | keep | DefaultWaitCoordinator ||
|| repair | NoOpToolCallRepairer | IToolCallRepairer | 5 | ✓ | keep | ChainRepairer/ ArgumentCleanupStage ||
|| runtime | NoOpActorRuntime | IActorRuntime | 1 | ✓ | keep | InMemoryActorRuntime ||
|| runtime/coordination | NoOpDaemonCoordinator | IDaemonCoordinator | 1 | ✓ | keep | DbDaemonCoordinator ||
|| runtime/lock | NoOpSessionTakeoverLock | ISessionTakeoverLock | 1 | ✓ | keep | DbSessionTakeoverLock ||
|| runtime/recovery | NoOpOrphanRecoveryHandler | IOrphanRecoveryHandler | 1 | ✓ | keep | DefaultOrphanRecoveryHandler ||
|| runtime/recovery | NoOpRecoveryManager | IRecoveryManager | 1 | ✓ | keep | ScheduledRecoveryManager ||
|| runtime/recovery | NoOpSessionTimeoutHandler | ISessionTimeoutHandler | 1 | ✓ | keep | DefaultSessionTimeoutHandler ||
|| runtime/recovery | NoOpTeamTaskRecoveryHandler | ITeamTaskRecoveryHandler | 1 | ✓ | keep | DefaultTeamTaskRecoveryHandler ||
|| security | NoOpAuditLogger | IAuditLogger | 2 | ✓ | keep | Slf4jAuditLogger/ CollectingAuditLogger ||
|| security | NoOpDenialLedger | IDenialLedger | 2 | ✓ | keep | DefaultDenialLedger/ DBDenialLedger ||
|| security | NoOpSandboxBackend | ISandboxBackend | 1 | ✓ | keep | DockerSandboxBackend ||
|| security | NoOpSecurityLevelResolver | ISecurityLevelResolver | 1 | ✓ | keep | DefaultSecurityLevelResolver ||
|| session | NoOpModelSwitchedMessageWriter | IModelSwitchedMessageWriter | 1 | ✓ | keep | DbModelSwitchedMessageWriter ||
|| skill | NoOpSkillCurator | ISkillCurator | 1 | ✓ | keep | LLMCurator ||
|| skill | NoOpSkillProvider | ISkillProvider | 1 | ✓ | keep | FileSystemSkillProvider ||
|| talent | NoOpTalent | ITalent | 0 | — | keep | final null-object 默认；扩展=实现 ITalent ||
|| team | NoOpMemberSpawner | IMemberSpawner | 1 | ✓ | keep | DefaultMemberSpawner ||
|| team | NoOpTeamAclChecker | ITeamAclChecker | 1 | ✓ | keep | DefaultTeamAclChecker ||
|| team | NoOpTeamManager | ITeamManager | 2 | ✓ | keep | DbTeamManager/ InMemoryTeamManager ||
|| team | NoOpTeamTaskStore | ITeamTaskStore | 2 | ✓ | keep | DbTeamTaskStore/ InMemoryTeamTaskStore ||
| team/flow | ~~NoOpTaskMemberRouter~~ → DefaultTaskMemberRouter（WI12 A4 正名） | ITaskMemberRouter | 0 | — | keep（功能性默认路由算法） | TTSD/TTFO 装配；测试注入自定义 router |
|| team/scheduler | NoOpTeamTaskSchedulerDaemon | ITeamTaskSchedulerDaemon | 1 | ✓ | keep | TeamTaskSchedulerDaemon ||
|| tool | NoOpToolProgressTracker | IToolProgressTracker | 1 | ✓ | keep | BashSandboxProgressTracker ||
|| usage | NoOpUsageRecorder | IUsageRecorder | 2 | ✓ | keep | DbUsageRecorder/ SimpleUsageRecorder ||

> 排除口径：guardrail/test/CheckpointTestHarness 内嵌套 NoOpToolManager（WI13 scope）。台账列"引擎装配"= 是否有 main 生产实现者（✓/—）；装配默认状态见 Current Baseline（engine 四文件直接引用 25 个 NoOp 类型）。计数口径：审计基线 41 个（改名前），落地后 `NoOp*.java` 文件数 41→40（1 个正名为 Default*）。
