# 2346 full-code-review 5.2 ai-dev/design 设计文档 drift 审计

> Plan Status: active
> Last Reviewed: 2026-10-06
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 5.2（MA5）；Skill=`ai-dev/skills/design-doc-audit-prompt.md`
> Related: owner doc `ai-dev/design/README.md`；Roadmap Details：「design/ 各子系统设计文档是否残留 "Proposed vs Current" 草稿态、与 live 基线 drift」。
> 机械基线（主 agent 实测）：ai-dev/design/ 36 子目录 + 13 根级散篇 md，合计 274 md 文件；「Proposed vs Current/Proposed Design」草稿态标记 3 文件命中（00-design-writing-guide.md 自身/nop-datav 两篇）。
> 只读审计（P0 走异步注入）。**抽样制 + 机械全量扫描结合**：草稿态标记机械全量 grep（274 文件）；drift 深审抽样 ≥10 个子系统（优先历次深审域 + 4.6 已核对的 nop-lint）。**方法学裁定**（同 5.1 先例）：design-doc-audit-prompt.md 仅部分适用——维度 3「稳定与时间敏感责任」、维度 4「设计漂移分类」可作判定辅助；维度 1 外部基准覆盖度探测与 verdict 输出契约不采用；「274 全读」条款被抽样制取代。判级=deep-audit 共享前缀 P0–P3（:329-333）；drift 面方法锚=维度 18（scoped 至 ai-dev/design）；草稿态面判级锚=guide Rule #14 + ai-dev/design/00-design-writing-guide.md:35（禁对比条款）+:183（「草案」是状态词表合法值——标记本身不违规，须叠加「实现已落地」判定）。
> Draft Review: agent_286b1f6c 首轮 4 Major（F3 方法学裁定——skill 部分适用+P0-P3 判级锚 :329-333+维度 18 scoped+草稿态判级锚 Rule #14/writing-guide :35/:183；F4 nop-orm 域无设计文档替换 maker-checker/+≤14 篇上限；F1 基线按模式分列——英文 3 命中=0 真草稿态、草案/Draft 41 文件含 nop-lint 12 篇族；F2 裁定分类 6 类+实现落地判定方法）+ 3 Minor（P0 前移/上限/锚点判据）——本轮修复后 agent_f1d4f3ad 聚焦复核全 PASS（12→13 篇计数勘正）——进入执行。

## Purpose

执行 roadmap 工作项 5.2：ai-dev/design 各子系统设计文档 drift 审计（草稿态残留 + live 基线 drift），产出报告 `5.2-design-drift-audit.md`。

## Current Baseline

- 机械基线（主 agent 实测）：36 子目录 + 13 根级散篇 = 274 md；「Proposed vs Current|Proposed Design」grep 命中 3 文件（00-design-writing-guide.md 为写作指南自身引用、nop-datav/schedule-report-design.md、nop-datav/screen-design.md）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；本项为文档对照型（验证以复核员独立抽验为锚）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/5.2-design-drift-audit.md`：摘要 + 草稿态全量扫描台账 + drift 抽样台账 + P0–P3 发现（`P<级别>-5.2-<序号>`）+ 驳回台账 + 裁决。
- **草稿态全量扫描**（roadmap 点名「Proposed vs Current 草稿态残留」）：274 文件机械 grep（Proposed vs Current/Proposed Design/草案/Draft 状态标记）+ 命中逐个裁定。**裁定分类 6 类**：真草稿态违规（实现已落地仍标草案——guide Rule #14 触发）/合法前置草案（实现未启动，如 xdef-declarative-validation-design 自述执行器未实现）/指南自身引用/显式否定句/历史合理保留/领域术语误命中（如 getScreenDraftLayout 的产品数据草稿）。判定方法：对照模块实现状态（代码在库/已收口）。
- **drift 深审抽样 ≥10 子系统**（优先历次深审域；**子系统无设计文档时记发现并从备选池补位——nop-orm 域无 design 目录，替换为 maker-checker/**）：nop-lint（4.6 刚审——fail-closed 语义与设计 03 文档对照）/nop-sys + maker-checker/（3.5：锁 P0 复核、Maker-Checker 契约、事件队列）/nop-stream/nop-wf/nop-task/nop-report/nop-auth/nop-metadata/nop-datav/nop-ai 域——每子系统抽 1-2 篇核心设计文档对照 live（**文档数上限 ≤14 篇**；选取判据：优先 00-vision/01-architecture-baseline + 历次深审点名篇目）。
- index 登记 + 独立 closure audit + roadmap 5.2 翻 done。

## Non-Goals

- 274 文件全量逐字 drift 校对（抽样制）；设计文档内容改进执行（发现归 R1.0）；5.1 已覆盖的 docs-for-ai 域。

## Scope

### In Scope

- `ai-dev/design/**`（草稿态标记全量 grep + drift 抽样 ≥10 子系统）
- 只读输入：`ai-dev/skills/design-doc-audit-prompt.md`、历次深审报告（drift 对照）

### Out Of Scope

- 全量逐字校对；设计改进执行；5.1/5.3 域。

## Execution Plan

### Phase 1 - 扫描与抽样（双 agent 并行）

Status: planned
Targets: ai-dev/design

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（不干净：登记在途变更，触及扫描域则等待，未触及登记豁免继续）
- [ ] 机械基线：274 文件清单、草稿态 grep 模式写死、抽样表选取（≥10 子系统）、历次深审 drift 对照清单
- [ ] 草稿态扫描 agent：机械全量 grep + 命中逐个裁定
- [ ] drift 抽样 agent：≥10 子系统抽 1-2 篇核心设计文档对照 live（每篇 ≥3 条核对声明）
- [ ] 裁定复核迭代：初稿完成后主 agent 抽查质量（完成判据：草稿态全量扫描 + ≥10 子系统 drift 对照）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 产物 `_tmp/fcr-5.2-design.md`

Exit Criteria:

- [ ] 初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [ ] 草稿态全量台账 + drift 抽样台账落盘
- [ ] No owner-doc update required（只读——发现归 R1.0 修复）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 扫描口径重放
- [ ] 主 agent 汇总：判级统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-5.2-design-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `5.2-design-drift-audit.md`；index 登记 5.2 行（如有 P2 走「P2 裁决台账」通道）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 定向验证锚点：扫描口径重放由复核员独立执行；doc-links exit 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应）；通过后 roadmap 5.2 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 扫描口径可复现（复核员重放一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重放为验证锚点。

- [ ] 报告落盘且草稿态全量扫描/drift 抽样全覆盖——覆盖度量：274 文件 grep 台账 + ≥10 子系统 drift 对照
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 5.2 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；docs 修复工单归 R1.0/R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
