# 2328 full-code-review 3.1 nop-wf 深审

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.1（MA3）；Skill=`ai-dev/skills/state-machine-business-review-prompt.md` + `ai-dev/skills/deep-audit-prompts.md`（维度 13/14 + 共享前缀）
> Related: owner doc `docs-for-ai/02-core-guides/workflow-configuration.md`（513 行）；security-audit WF-01/02/03 现状（security-audit-roadmap item 14：F-WF-01-*/F-WF-02-1/F-WF-03-1 中仅 F-WF-03-1 已修，其余 successor-deferred 至 MA3.11——本项点名现状标定）；plan 2325（2.7 已审 nop-wf beans/xmeta 声明面——本项审行为语义不重复）；check2 点名（nop-wf.md 15 条：P1×4 :19/:43/:69/:89 + P2×7 :112-261 + P3 余量；全部带 fix-ai-check 处置块，以 live code 为准标定）
> 只读审计（P0 走异步注入）；探针卫生：临时测试文件落 `_tmp/` 或用后即删，运行后 `git status` 复洁核对（2.6 先例：测试 `_dump` 夹具会留副作用）；check2/nop-wf.md 与 security-audit consolidation-summary 的 WF 条目为点名例外输入。
> Draft Review: agent_092fc07e 首轮 1 Blocker（AbstractWorkflowStore 路径写错模块——实为 core 非 dao；点名面横跨 core/scheduler/ai）+ 4 Major（check2 计数 P2×7→8/P3×4→3、子流程状态机零覆盖——startSubFlow/notifySubFlowEnd/F-WF-01-3 result injection、WF-02/03 跨模块落点未声明（nop-task/nop-job，与 3.2 撞车须协调）、维度 22 教训 12 引擎侧适配缺失）+ 6 Minor 全修（委托状态机显式行、dim13-02 归属 2.6 勘正、日期笔误、探针卫生、测试命令按需加 scheduler、Non-Goals 对齐）；修后换 task_id 复核。

## Purpose

执行 roadmap 工作项 3.1：深审 nop-wf 审批状态机（WorkflowEngineImpl 1941 行）+ WF-01/02/03 现状标定，产出报告 `3.1-wf-deep-audit.md`。

## Current Baseline

- nop-wf 组：core 106/service 23/dao 53/api 71 main（合计 43389 行）。核心：WorkflowEngineImpl **1941 行**（状态机核心）。
- check2/nop-wf.md 15 条：P1×4（:19/:43/:69/:89）+ P2×8（:112/:134/:153/:172/:193/:212/:239/:261）+ P3×3。全部带 fix-ai-check 处置块——以 live code 为准标定。
- security-audit：WF-01（审批委托安全）/WF-02（任务执行隔离）successor-deferred 至 MA3.11（未标定）；WF-03-1 已修。本项标定 WF-01/02 现状。
- 2.6 关联：nop-wf-service 的 BizModel 声明面已由 **2.6** 扫描（NopWfDefinition publish/unpublish 等已立案 **dim13-02=P1-2.6-006**，见 2.6 报告）——本项不重复声明面，审引擎行为。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；nop-wf 组在基线内。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.1-wf-deep-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-3.1-<序号>`）+ 驳回台账（正文）+ 裁决。
- 状态机面（state-machine skill）：WorkflowEngineImpl 审批状态机全转移验证（startStep/transition/join/split/endStep/**子流程状态机：startSubFlow :443/WAITING 语义/notifySubFlowEnd :1127/结果回注——F-WF-01-3 的 unvalidated result injection 半边归此**）；**维度 22 引擎侧适配检查项（教训 12）：*end listener 结束原因判定（驳回即通过=P0 判据）、approve/reject 单一事实源（引擎 vs BizModel 双实现漂移）、失败路径 fail-closed（静默 agree）、wf.start null-ctx 先例（P1-MA7.6-02）**；check2 P1×4 逐条 live 标定（**实测优先**：doReject/joinGroupExpr/designer auth/TO_ASSIGNED——构造 xwf 场景探针）；审批动作语义（approve/reject/withdraw 与 2.6 dim13-01 approval-support 的引擎侧衔接对照）。
- 权限与委托面（维度 13）：引擎管理类操作鉴权（check2 :172 域）；allowCallByUser 语义（:134/:239 域）；**委托状态机**（canBeDelegatedBy 委托链/转办 vs 代办状态差异）；WF-01 委托安全现状标定。
- 事务与调度面（维度 14）：WfTaskScanner 批处理（:212 域）；scheduler 身份（:239 域）；WF-02/03 现状标定（**跨模块落点显式声明：F-WF-02-1 落点 nop-task DaoTaskStateStore 序列化路径——与 3.2 撞车，本项只做现状标定不深审、结论回灌 3.2；F-WF-03-1 落点 nop-job reportTaskLog 已修——验证方式=nop-job 既有测试 TestReportTaskLogAuthGate 在位性核对；MA3.11 其余 MEDIUM 项不在本项**）。
- index 登记 + 独立 closure audit + roadmap 3.1 翻 done。

## Non-Goals

- xwf DSL 语法定义审计（xdef 归框架）；nop-wf-ai/nop-wf-scheduler 全模块（点名类入范围）；MA3.11 的 MEDIUM×14 标定（本项只做 WF-01/02/03 现状，其余归 3.11）；2.6/2.7 已立案发现。

## Scope

### In Scope

- `nop-wf/nop-wf-core/**`（引擎+模型+AbstractWorkflowStore/WfModelAnalyzer）
- `nop-wf/nop-wf-service/**`（WorkflowDesignerService 等 BizModel 行为侧）
- 点名类跨模块清单（check2 涉及面）：core（AbstractWorkflowStore/WfModelAnalyzer/WorkflowStepImpl/ApprovalFlowHelper）+ scheduler（WfTaskScanner）+ ai（WfAiHelper）+ dao（DaoWorkflowStore 侧）
- 只读输入：`ai-dev/audits/check2/nop-wf.md`、`ai-dev/audits/security-audit/2026-09-19-consolidation-summary.md` WF 条目、`docs-for-ai/02-core-guides/workflow-configuration.md`、`ai-dev/lessons/12-xwf-listener-end-reason.md`（教训 12）

### Out Of Scope

- 上述模块的**非点名类**深审（scheduler/ai 仅 WfTaskScanner/WfAiHelper 点名类入范围）；xdef 语法定义；MA3.11 标定；2.6/2.7 已立案项。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: completed
Targets: nop-wf core/service

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：WorkflowEngineImpl 方法清单与状态转移表、check2 15 条处置块状态盘点、WF-01/02/03 security-audit 台账原文
- [x] 状态机子 agent（state-machine skill）：全转移验证 + check2 P1×4 实测标定（探针优先）+ 审批动作语义
- [x] 权限调度子 agent（维度 13+14）：引擎操作鉴权/**check2 P2×8 逐条标定（:112-:261 全覆盖，验收锚=8 条标定结论落盘）**/WF-01/02/03 现状标定
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [x] 产物 `_tmp/fcr-3.1-{statemachine,auth}.md`

Exit Criteria:

- [x] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] check2 P1×4 + P2×8 + WF-01/02/03 标定结论落盘
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + check2 标定独立验证
- [x] 主 agent 汇总：判级统一、与 2.6 dim13-01（approval-support）/3.10 边界协调
- [x] 状态机正结论收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-3.1-{statemachine,auth}-review.md`
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `3.1-wf-deep-audit.md`；index 登记 3.1 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [x] 定向验证锚点：`./mvnw test -pl nop-wf/nop-wf-core,nop-wf/nop-wf-service -am`（如涉及 scheduler 域发现则加 `nop-wf/nop-wf-scheduler`；H2 mem 无外部 DB；如遇失败归因预存并登记）
- [x] 独立子 agent closure audit；通过后 roadmap 3.1 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因预存并登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且状态机+权限调度面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 3.1 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x；MA3.11 标定独立推进。）

## Closure

Status Note: 只读审计计划收口。报告 `3.1-wf-deep-audit.md` 落盘：通过无 P0 附 P1×2（approval-support 绕引擎双事实源——与 P1-2.6-005 分立互补同批互引；超时 dueAction 通道被 allowCallByUser 阻断——与 job bean 失配叠加同批）+P2×11/P3×10（23 条唯一发现，降 0 驳 0）。**check2 12 条点名零虚假申报**（P1×4 零推翻深验 2 条、P2×8 六修二 live）；WF-01/02/03 六条全标定（F-WF-02-1 回灌 3.2、F-WF-01-3 建议 ledger 补记、MA3.11 剩余清单减单）；维度 22 引擎侧适配交付（checkEnd 无一等结束原因=双故障纵深缺口 P2）。closure audit 一次 PASS；advisory 1（scheduler 测试未入实跑）已补跑闭合——nop-wf-scheduler 测试通过。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_3cb464b9-5a2d-4b99-9f41-5b17a03efb00（fresh session，只读 closure auditor）
- Evidence:
  - 计数四方勾稽：P0=0/P1=2/P2=11/P3=10=23 ↔ 报告内实点 ↔ index 3.1 行 ↔ 复核保留数（SM 14+AU 10 独立+1 附注+1 dup=25 候选−2 去重=23）零缺陷
  - P1 台账：2 行与 index 一致；分立互补裁定经 2.6 报告 P1-2.6-005 原文核实相容；修复耦合组三族在位
  - check2 台账：P1×4 零推翻（SM-review 深验 :19+:89）+P2×8 全覆盖与 auth-review 一致；「P0 发现追踪」无 3.1 条目正确
  - WF-01/02/03：六条 F 条目溯源 ledger 原文一致；F-WF-02-1 回灌 3.2 双处在位
  - 判级调和：安全类总裁据应用一致（P2-3.1-02 双故障缺口 P2 不升）；dim13-01 撞车裁定+教训入档 G.1 双侧一致
  - 锚点抽验 6/6 live 命中
  - 定向测试：wf-test.log BUILD SUCCESS 138/0F/skip1 逐字吻合；**advisory 1 已闭合——nop-wf-scheduler 测试补跑通过**（TestWfTaskScanner 4 用例）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2328-full-code-review-3.1-wf-audit.md --strict` 退出码 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以定向 test 全绿 + 静态溯源为锚
  - Deferred 项分类检查：无 deferred；P1×2 经 index「P1 发现汇总」进 R1.0，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P1×2+P2×11+P3×10 修复归属 R1.0 三耦合族；F-WF-02-1 回灌 3.2；WF-01/02 标定减单 MA3.11；G.1 撞车教训、G.2 移交两项在报告移交注记）
