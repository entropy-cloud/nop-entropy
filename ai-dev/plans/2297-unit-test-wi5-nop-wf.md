# 2297 unit-test-coverage-roadmap WI5 — nop-wf 引擎面补强

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI5 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；docs-for-ai/03-runbooks/build-approval-flow.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-wf 引擎面从 0 到 1 建立测试：nop-wf-core（80 main/0 test，全仓最大零测试引擎模块）流程定义解析/节点流转/任务分配/回退跳转语义；nop-wf-dao（36/0）与 nop-wf-api（61/0）结构性用例。wf-service 已有 22 个测试文件为模式参照。已知 P1（canonical 审批模板 end listener 驳回即通过）修复时回归并入（独立立项，本 WI 不修产品）。

## Current Baseline

- nop-wf-core：NO-EXEC（无 src/test），main 文件数按排除 `_gen` 口径 79（roadmap 80 口径未注明）；pom 已有 junit-jupiter test 依赖。
- nop-wf-dao：NO-EXEC，排除 `_gen` 38 main；nop-wf-api：NO-EXEC，61 main；**两者 pom 均无 junit 测试依赖**（实测确认，均需新增，见 Scope 裁定）。
- **测试基建（审查确认）**：wf-core/dao/api 均**无** nop-autotest 依赖；wf-service 的测试（AbstractWorkflowTestCase extends JunitAutoTestCase）依赖全栈容器，**不可照搬**。wf-core 测试走 nop-core 式 `CoreInitialization.initialize()/destroy()` 纯 junit 档（wf.xdef 经 nop-core→nop-xlang→nop-xdefs 传递到 test classpath，已验证可加载）。
- **具名靶点（wf-core）**：WfModelParser（纯静态解析，`store/WfModelParser.java`，已验证可行）为第一优先；流程流转/回退语义主体在 WorkflowImpl/WorkflowStepImpl，依赖 IWfRuntime 协作对象——仅在其可用手写 fake 隔离时纳入（执行时判断，不可行则如实记录并转向解析/模型语义面）。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi5-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- nop-wf-core：流程定义解析（xdsl/wf 模型加载）、节点流转语义、任务分配、回退/跳转的纯逻辑用例 ≥15 个。
- nop-wf-dao / nop-wf-api：模型/实体/枚举结构性用例 ≥8 个。
- 三模块测试全绿，记录基线 0% → 复测增量。

## Non-Goals

- 不修改产品代码；canonical 模板 P1 缺陷只记录不修；不做需要流程引擎全容器的 E2E。

## Scope

### In Scope

- `nop-wf/nop-wf-core|nop-wf-dao|nop-wf-api/src/test/**` 新增测试与资源。
- **pom 裁定**：允许且仅允许为缺测试依赖的模块（nop-wf-api，必要时 nop-wf-dao）新增 `junit-jupiter` test-scope 依赖（测试基建，非产品行为变更，逐项记录在 plan）。

### Out Of Scope

- 产品代码、流程模板、生成管线；wf-service/web/app（已有覆盖或非引擎面）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: completed
Targets: `nop-wf/*/src/test/**`

- Item Types: `Fix`

- [x] wf-core 靶点 ≥15 用例（WfModelParser 解析语义优先；流转/回退语义仅在手写 fake 可隔离时纳入，断言以 wf.xdef 模型语义与 `docs-for-ai/03-runbooks/build-approval-flow.md` 为依据）。
  - 实测：47 个用例（TestWfModelParser 9、TestWfModelParserValidation 8、TestWfModelHelper 4、TestExecGroupVoteSemantics 9、TestWorkflowEngineFlow 8、TestApprovalFlowHelper 4、TestWfActorAssignSupport 5）。流转/回退/驳回/撤回语义经手写 InMemoryWfStore + SimpleWfActorResolver 隔离纳入（TestWorkflowEngineFlow）。
- [x] wf-dao + wf-api 结构性用例 ≥8 个。
  - 实测：dao 13 个（TestWorkflowDefinitionDO 5、TestWfResourceNamespaceHandler 3、TestWfDaoEntityStructure 5）+ api 16 个（TestWfActorHelper 4、TestWfActorModelSemantics 6、TestWfApiBeanContracts 6）= 29 个。
- [x] 需要的 pom test 依赖新增（wf-dao、wf-api 各一处 junit-jupiter test-scope）并记录。
  - pom 记录：`nop-wf/nop-wf-dao/pom.xml` 与 `nop-wf/nop-wf-api/pom.xml` 各新增 1 条 `org.junit.jupiter:junit-jupiter` test-scope 依赖（无版本号，由 root dependencyManagement 管理），带 `WI5 plan 2297` 注释；nop-wf-core 未改（已有 junit test 依赖）。产品代码零修改。
- [x] 三模块 `mvnq -- test -pl :<module> -am -fae` 全绿。
  - 实测：nop-wf-api / nop-wf-dao / nop-wf-core 三条命令均 BUILD SUCCESS（退出码 0，2026-10-02）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增测试 ≥23 用例，含显式语义断言。（实测 76 个：47 core + 13 dao + 16 api）
- [x] 三模块测试全绿。
- [x] pom 变更（如有）仅 test-scope 依赖且已记录。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。（plan owner 已补记）

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI5 checkbox

- Item Types: `Proof` + `Decision`

- [x] 删三模块 exec → baseline 脚本复测，记录 0 → X 增量。
  - 实测（`coverage-baseline.sh --skip-test --label wi5-2026-10-02`，快照 `ai-dev/analysis/2026-10/coverage-baseline-wi5-2026-10-02.json`）：
    - nop-wf-core：NO-EXEC/0% → linePct 41.55%（1227/2953），branch 31.49%
    - nop-wf-dao：NO-EXEC/0% → linePct 12.35%（51/413），branch 8.11%
    - nop-wf-api：NO-EXEC/0% → linePct 7.94%（125/1574），branch 85.29%
- [x] 残余缺口显式裁定（engine 层目标 45%）。
  - 裁定：nop-wf-core 41.55% 距 engine 层 45% 目标差 3.45pct，残余集中在运行时协作面——WorkflowEngineImpl 35.45%（1072 行，信号等待/子流程/异常 listener 路径）、WorkflowStepImpl 19.49%（118 行）、WorkflowServiceImpl 0%（158 行，全运行时 facade）、AbstractWorkflowStore 18.89%。这些路径需要更深的 IWfRuntime 全交互（signal/subflow/exec-group 运行时），超出本 WI"从 0 建立"的门槛，裁定为 watch-only residual，建议由后续引擎深化切片（聚焦 WorkflowEngineImpl 信号/子流程路径）承接；本 WI 不静默降级，目标差额已记录。
  - nop-wf-dao 残余：DaoWorkflowStore 0%（162 行，需 ORM 全容器，属 wf-service 容器测试覆盖面）、NopWfInstance/NopWfStepInstance 实体细粒度逻辑 0%（生成代码为主）。
  - nop-wf-api 残余：18 个 `NopWf*Input/OutputBean` 生成 DTO（0%，约 1100 行），纯字段搬运结构，判定为低价值填充面，watch-only。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI5 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI5 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。（plan owner 已补记）

## Closure Gates

- [x] 三模块全部新增测试绿（含既有测试零回归）
- [x] 产品代码零修改；pom 仅 test-scope 新增且记录（git status 实证：仅 nop-wf-api/pom.xml、nop-wf-dao/pom.xml 修改 + 三个 src/test 新目录）
- [x] 覆盖增量实测记录（`ai-dev/analysis/2026-10/coverage-baseline-wi5-2026-10-02.{json,md}`）
- [x] 残余缺口显式裁定（无静默降级）（见 Phase 2 裁定 + Deferred But Adjudicated）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：流转/回退测试断言状态机语义（audit 抽查：TestWorkflowEngineFlow 经真实 WorkflowManagerImpl/EngineImpl 断言激活/迁移/终止/回退/撤回全状态迁移，无裸 future.get）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2297-unit-test-wi5-nop-wf.md --strict` 退出码 0
- [x] roadmap WI5 checkbox 与 plan/log 一致

## Deferred But Adjudicated

### `ai-dev/logs/` 条目与 roadmap checkbox（执行会话受协调约束不修改共享文件）

- Classification: `resolved`（已由协调方收口：log 条目已补记、roadmap checkbox 待 audit APPROVE 后勾选）
- Why Not Blocking Closure: 测试增量与测量数字已全部落在本 plan 与 `ai-dev/analysis/2026-10/coverage-baseline-wi5-2026-10-02.*`；协调方已补写 log 条目，roadmap 勾选按流程待独立 audit 通过。
- Successor Required: `no`

### 产品缺陷嫌疑（不修，本 WI 只记录）— 待独立立项

- Classification: `watch-only residual`（对 plan 2297 而言）
- Why Not Blocking Closure: 本 plan Non-Goals 明确"不修改产品代码"；缺陷有 focused trip-wire 测试固化当前行为，修复立项时回归测试可并入。
- Successor Required: `yes`
- Successor Path: 缺陷修复立项（见 Closure 报告缺陷清单）：
  1. `GraphBreadthFirstIterator`（nop-core）未把 root 加入 visited 集合：环包含根节点的工作流定义在模型校验期抛 `ArrayIndexOutOfBoundsException`（`DagAnalyzer.checkStartReachable`），`ERR_WF_GRAPH_CONTAINS_LOOP` 友好错误不可达。trip-wire：`TestWfModelParserValidation.testRootCycleCrashesBeforeFriendlyLoopError`（资源 `errRootLoop/v1.xwf`）。
  2. `WfModelAnalyzer.checkEnd`（nop-wf-core）的 `eventuallyToAssigned` 传播缺少 `isNextToAssigned` 前置条件，任何步骤都被无条件标记 `eventuallyToAssigned=true`，`ERR_WF_STEP_NOT_ENDABLE` 成为死代码。trip-wire：`TestWfModelParserValidation.testStepNotEndableValidationIsDeadCode`（资源 `errNotEndable/v1.xwf`）。

## Non-Blocking Follow-ups

- canonical 审批模板 end listener 驳回即通过（P1）——修复立项时回归测试并入本 WI 记账。

## Closure

Status Note: 13 测试类/76 用例全绿（47/13/16），wf-core 0→41.55% 与 XML counter 逐位一致，手写 fake 驱动真实引擎的 Anti-Hollow 抽查通过，缺陷嫌疑经源码逐字核实（WfModelAnalyzer.checkEnd L175-183 无条件传播实锤），Deferred 裁定量化（WorkflowEngineImpl 35.45%/1072L 等）。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_4333be50-9528-468e-8a68-406180a5e46e，fresh session）

Follow-up:

- （待填写）

## Optional Sections

- Risks And Rollback: 纯测试增量；pom test-scope 新增可独立回滚。
