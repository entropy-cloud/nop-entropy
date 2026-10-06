# 2345 full-code-review 5.1 docs-for-ai 与 live 代码一致性审计

> Plan Status: active
> Last Reviewed: 2026-10-06
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 5.1（MA5）；Skill=`ai-dev/skills/documentation-routing-audit-prompt.md`
> Related: owner doc `docs-for-ai/INDEX.md`；Roadmap Details：「docs-for-ai 抽样与 live 代码对照（路由表、命令、默认规则是否与实现一致）」。
> 机械基线（主 agent 实测）：docs-for-ai 含 00-required-reading×5/00-start-here/01-repo-map/02-core-guides/03-modules/03-runbooks/04-reference/05-examples/06-extensibility/90-maintenance/INDEX.md。
> 只读审计（P0 走异步注入）。**抽样制**：02-core-guides + 03-modules 抽样对照（roadmap 字面口径），非全量逐字校对。**方法学裁定**：documentation-routing-audit-prompt.md 仅部分适用（路由自足性维度；其「禁读正文」与「修复重测」条款被本项「读正文对照 + 只读归 R1.0」取代）；内容对照半边的方法锚=**deep-audit-prompts.md 维度 18（文档-代码一致性，含 source-anchors.md 锚点检查）+ 共享前缀判级（P0–P3 :329-333）**。
> Draft Review: agent_66f85dee 首轮 4 Major（F-1 3.8 撞车编号修正〔权限面族=001/002；003 系 auth 缺陷类别澄清〕、F-2 撞车清单补全〔P1-2.6-001/P1-3.3-002/P1-3.6-002/P2-3.5-008/009 等〕、F-3 方法学裁定——skill 仅路由维度适用+内容对照锚维度 18+共享前缀判级、F-4 拆双 agent+每篇验证密度 ≥5 条）+ 7 Minor（判级锚定/4.1 口径具体化/INDEX 默认规则节点名/P0 前移/Phase 3 owner-doc 行/全覆盖措辞/抽样密度复核下限）——本轮修复后 agent_6411a285 聚焦复核全 PASS（modules agent 补撞车核对归属）——进入执行。

## Purpose

执行 roadmap 工作项 5.1：docs-for-ai 与 live 代码一致性审计（02-core-guides + 03-modules 抽样），产出报告 `5.1-docs-consistency-audit.md`。

## Current Baseline

- docs-for-ai 结构（主 agent 实测）：00-required-reading×5/00-start-here/01-repo-map/02-core-guides/03-modules/03-runbooks/04-reference/05-examples/06-extensibility/90-maintenance/INDEX.md。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；本项为文档对照型（验证以复核员独立抽验为锚）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/5.1-docs-consistency-audit.md`：摘要 + 抽样台账 + P0–P3 发现（`P<级别>-5.1-<序号>`）+ 驳回台账 + 裁决。
- **02-core-guides 抽样对照**（roadmap 点名）：抽样 ≥6 篇核心指南（service-layer/api-and-graphql/model-first-development/ioc-and-config/error-handling/testing 等优先），逐篇核对：文档声称的 API 签名/命令/默认规则/文件路径 vs live 代码。
- **03-modules 抽样对照**：抽样 ≥6 篇模块文档（优先历次深审域：nop-sys/nop-report/nop-batch/nop-wf/nop-task——已知 drift 高发），核对实体表/能力入口/源码锚点 vs live。
- **INDEX.md 路由表**（owner doc）：路由条目指向的文件存在性 + 描述准确性抽查。
- 历次深审 owner-doc drift 撞车清单（机械基线步从 index「P1 发现汇总」+ 各报告「转 docs（G.2）」注记编制）：P1-3.5-001（ILockService 幻影——nop-sys.md :48/:72，R1.0 open 应仍 live）、P2-3.5-008/009（死装配族同批互引——nop-sys.md）、**P1-2.6-001（service-layer.md「默认有事务」表述——2.6 转 docs G.2，点名第一优先篇目）**、**P1-3.3-002（batch-dsl.md :15 processor order 勘正伴随——02-core-guides 域）**、**P1-3.6-002（nop-report.md 权限文档勘正伴随——点名抽样域）**、P1-1.8-001/P1-3.4-001（文档声明伴随面）。**类别澄清**：P1-3.8-001/002/003 系产品 auth 缺陷非 doc drift——对照角度=「文档是否描述了不存在的安全行为」。
- index 登记 + 独立 closure audit + roadmap 5.1 翻 done。

## Non-Goals

- docs 全量逐字校对；ai-dev/design drift（5.2 域）；nop-*-api 契约（5.3 域）；docs 内容改进执行（发现归 R1.0）。

## Scope

### In Scope

- `docs-for-ai/02-core-guides/**`（抽样 ≥6）、`docs-for-ai/03-modules/**`（抽样 ≥6）、`docs-for-ai/INDEX.md`（路由抽查）
- 只读输入：`ai-dev/skills/documentation-routing-audit-prompt.md`、历次深审报告（已知 drift 对照）

### Out Of Scope

- 00-required-reading/03-runbooks/04-reference/05-examples/06-extensibility/90-maintenance 全面校对（抽查可覆盖）；5.2/5.3 域；docs 改进执行。

## Execution Plan

### Phase 1 - 抽样对照（双 agent 并行）

Status: in progress
Targets: docs-for-ai 02/03 + INDEX

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（不干净：登记在途变更，触及扫描域则等待，未触及登记豁免继续）
- [ ] 机械基线：02-core-guides 与 03-modules 文件清单（抽样表选取）、已知 drift 撞车清单（3.5/3.8 等）
- [ ] core-guides 抽样 agent：≥6 篇逐篇对照（**每篇最低验证密度：≥5 条已核对声明、逐条 doc:line + code file:line 锚**）+ 已知 drift 撞车核对
- [ ] modules+INDEX 抽样 agent：03-modules ≥6 篇逐篇对照（同密度）+ 已知 drift 撞车核对 + INDEX 路由抽查（**含「默认规则」节 :31 8 条逐条核验——与 P1-2.6-001 同族「@BizMutation 自动进事务」表述**）+ source-anchors.md 锚点有效性核对
- [ ] 裁定复核迭代：初稿完成后主 agent 抽查质量（完成判据：≥12 篇逐篇结论 + 每篇 ≥5 条核对声明）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 产物 `_tmp/fcr-5.1-docs.md`

Exit Criteria:

- [ ] 初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [ ] 抽样台账（≥12 篇逐篇结论）落盘
- [ ] No owner-doc update required（只读——发现归 R1.0 修复）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 抽样独立验证
- [ ] 主 agent 汇总：判级统一（文档 drift 判级按「误导性文档=P1；过时=P2/P3」总裁据）、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-5.1-docs-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `5.1-docs-consistency-audit.md`；index 登记 5.1 行（如有 P2 走「P2 裁决台账」通道）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 定向验证锚点：抽样台账由复核员独立抽验 + doc-links exit 0
- [ ] **No owner-doc update required（只读——docs 修复发现归 R1.0）**
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应）；通过后 roadmap 5.1 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 抽样台账可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立抽验为验证锚点。

- [ ] 报告落盘且抽样面全覆盖（**抽样制：≥12 篇逐篇结论，非 47+30 全量**）+ 路由抽查表
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 5.1 回填 `done`；log 收口记录已写
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
