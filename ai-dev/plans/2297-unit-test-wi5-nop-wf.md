# 2297 unit-test-coverage-roadmap WI5 — nop-wf 引擎面补强

> Plan Status: active
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

Status: planned
Targets: `nop-wf/*/src/test/**`

- Item Types: `Fix`

- [ ] wf-core 靶点 ≥15 用例（WfModelParser 解析语义优先；流转/回退语义仅在手写 fake 可隔离时纳入，断言以 wf.xdef 模型语义与 `docs-for-ai/03-runbooks/build-approval-flow.md` 为依据）。
- [ ] wf-dao + wf-api 结构性用例 ≥8 个。
- [ ] 需要的 pom test 依赖新增（wf-dao、wf-api 各一处 junit-jupiter test-scope）并记录。
- [ ] 三模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增测试 ≥23 用例，含显式语义断言。
- [ ] 三模块测试全绿。
- [ ] pom 变更（如有）仅 test-scope 依赖且已记录。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI5 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 删三模块 exec → baseline 脚本复测，记录 0 → X 增量。
- [ ] 残余缺口显式裁定（engine 层目标 45%）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI5 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI5 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 三模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码零修改；pom 仅 test-scope 新增且记录
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：流转/回退测试断言状态机语义（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2297-unit-test-wi5-nop-wf.md --strict` 退出码 0
- [ ] roadmap WI5 checkbox 与 plan/log 一致

## Deferred But Adjudicated

（执行结束时按实测填写）

## Non-Blocking Follow-ups

- canonical 审批模板 end listener 驳回即通过（P1）——修复立项时回归测试并入本 WI 记账。

## Closure

Status Note: （完成时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent closure audit 后填写）

Follow-up:

- （待填写）

## Optional Sections

- Risks And Rollback: 纯测试增量；pom test-scope 新增可独立回滚。
