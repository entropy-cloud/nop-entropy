# 2329 full-code-review 3.2 nop-task 深审

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.2（MA3）；Skill=`ai-dev/skills/state-machine-business-review-prompt.md`（任务状态机）+ deep-audit 维度 14（调度可靠性）
> Related: owner doc `docs-for-ai/03-modules/nop-task.md`；**F-WF-02-1 回灌接收**（3.1 已标定：DaoTaskStateStore isSupportPersist=true+JsonTool.serialize 明文 JSON 多点落库仍 live——本项深审该面并给修复裁定）；check2 撞车对照（nop-task 相关条目）；plan 2306 批次残留（check2 [P1-1] JobTimeoutCheckerImpl 修复先例在案）
> 只读审计（P0 走异步注入）。
> Draft Review: agent_ff115a71 首轮 1 Blocker（continuation-skip P0 原文实为 check/nop-task.md:26 非 check2/security-audit；check 系列 14 条缺席撞车盘点——与 6.4 双计协调）+ 2 Major（JobTimeoutChecker 落点 nop-job 未澄清+同族扫描未定义；三份先审计资产缺席——含 09-05「一次性设计定案」在案裁定）+ 6 Minor 全修（902 行勘正、维度 14 实名、日期、跨 agent 去重句、真实锚点替换悬空引用、Out Of Scope 补 nop-job）；修后换 task_id 复核。

## Purpose

执行 roadmap 工作项 3.2：深审 nop-task（DaoTaskStateStore 850 行、continuation-skip P0 复核、可靠性装饰器），产出报告 `3.2-task-deep-audit.md`。

## Current Baseline

- `nop-task/nop-task-core`（+service/dao 若有）：以机械基线为准。核心：DaoTaskStateStore（实测 902 行——plan 349/364 两轮修复后较 roadmap 记载 850 增长；状态持久化）。
- 撞车对照：**check 系列**（`ai-dev/audits/check/nop-task.md` 14 条=P0×3/P1×5/P2×4/P3×2，含 continuation-skip P0——与 6.4 标定结果互引/回填，防双计：6.4 已标定的条目本项标「与 6.4 一致/差异」）；**check2/nop-task.md**（P1 outputs 重放 :210 plan 349 已修等）；**2026-09-25 深审**（`ai-dev/audits/2026-09-25-1410-deep-audit-nop-task-quality/`——在案发现如 TaskStepHelper.newError 不可达 .param）；**2026-09-05 模块分析**（`ai-dev/analysis/2026-09/2026-09-05-nop-task-module-analysis.md`——**在案设计裁定「恢复类缺陷不建议逐条零散修复，应一次性设计定案」——修复裁定须先对照此裁定**）。
- **F-WF-02-1 回灌输入**：3.1 已标定「DaoTaskStateStore isSupportPersist=true+JsonTool.serialize(...,false) 明文 JSON 多点落库（:175/:196/:557/:565/:735）仍 live」——本项深审该面（敏感字段明文落库的完整数据流+修复裁定）。
- **continuation-skip P0 复核（原文钉死）**：`ai-dev/audits/check/nop-task.md:26`（「[P0] 持久化状态下 Loop 迭代 2+/Fork 分支 2+ 被 continuation-skip 静默跳过并复用首轮回放结果」）；处置=`check/nop-task.md:60`（fix-ai-check 2026-08-22 已修）+`check/SUMMARY.md:97`；回归测试 TestRepeatedStepPathExecution 实测在位。复核靶心=plan 349/364 两轮改动后门控语义是否仍成立。
- 先审计资产三份（只读输入+驳回台账标定用）：①`ai-dev/audits/check/nop-task.md`（check 系列 14 条，P0×3）；②`ai-dev/audits/2026-09-25-1410-deep-audit-nop-task-quality/`（七文件深审，初审完成待深挖复核——在案发现如 TaskStepHelper.newError 不可达 .param）；③`ai-dev/analysis/2026-09/2026-09-05-nop-task-module-analysis.md`（**在案设计裁定：恢复类缺陷不建议逐条零散修复，应一次性设计定案——本项修复裁定须先对照**）。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；nop-task 组在基线内。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.2-task-deep-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-3.2-<序号>`）+ 驳回台账（正文）+ 裁决。
- 状态机面（state-machine skill）：DaoTaskStateStore 状态机全转移验证；continuation-skip P0 复核（定位原文→live 标定）；任务状态语义（pause/resume/cancel/fail 路径）。
- 可靠性面（维度 14〔异步与事务模式〕）：可靠性装饰器语义（retry/timeout/checkpoint 装饰器栈）；**同族模式扫描=nop-task 域内扫「装配缺失/nullable 依赖静默降级」模式**（先例 JobTimeoutCheckerImpl 在 nop-job——check2/nop-job.md:51，plan 340 已修 `@Inject @Nullable`；nop-job 本体不在本项）；调度接线完整性。
- F-WF-02-1 深审：DaoTaskStateStore 明文序列化面的完整数据流（哪些字段敏感/落库位置/读取方）+ 修复裁定（加密 vs 脱敏 vs 文档声明）。
- index 登记 + 独立 closure audit + roadmap 3.2 翻 done。

## Non-Goals

- nop-wf 域（3.1 已审）；nop-batch（3.3/3.4 域）；任务调度 UI 面。

## Scope

### In Scope

- `nop-task/**`（以机械基线确认实际模块结构）
- 只读输入：`docs-for-ai/03-modules/nop-task.md`、security-audit consolidation-summary 相关条目、3.1 报告 F-WF-02-1 标定段（3.1 报告的「3.1 F4」系笔误无法解析，真实锚点=check2/nop-job.md P1 与 3.1 P2-3.1-001 job yaml 失配）

### Out Of Scope

- nop-wf/nop-batch（3.1/3.3/3.4 域）；任务调度前端。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: planned
Targets: nop-task

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：模块结构清单（聚合 pom+10 子模块）、DaoTaskStateStore 方法清单、continuation-skip P0 原文核对（check/nop-task.md:26/:60）、**三份先审计资产盘点（check 14 条/09-25 深审/09-05 模块分析——在案发现与裁定清单）**、可靠性装饰器清单
- [ ] 状态机子 agent：DaoTaskStateStore 全转移验证 + continuation-skip P0 复核（实测优先）+ 任务状态语义
- [ ] 可靠性子 agent（维度 14）：装饰器栈语义 + F-WF-02-1 明文序列化深审（数据流+修复裁定）+ JobTimeoutChecker 同族扫描
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-3.2-{statemachine,reliability}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] continuation-skip P0 + F-WF-02-1 标定/深审结论落盘
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + P0/F-WF-02-1 独立验证
- [ ] 主 agent 汇总：判级统一、跨 agent 去重（DaoTaskStateStore 双 agent 深读重叠——2.4 先例）、与 3.1（F-WF-02-1 来源）/3.3/3.4 边界协调
- [ ] 状态机正结论收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-3.2-{statemachine,reliability}-review.md`
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `3.2-task-deep-audit.md`；index 登记 3.2 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-task -am`（按机械基线确认实际模块路径；如遇失败归因预存并登记）
- [ ] 独立子 agent closure audit；通过后 roadmap 3.2 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因预存并登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且状态机+可靠性面全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 3.2 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；F-WF-02-1 修复工单归 R1.0/R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
