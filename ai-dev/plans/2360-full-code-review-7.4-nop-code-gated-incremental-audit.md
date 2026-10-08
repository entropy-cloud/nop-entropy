# 2360 full-code-review 7.4 nop-code gated 项复核 + 增量巡检（lang-go/rust/csharp 新扩展）

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.4（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 03/09/12 巡检侧重）
> Related: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Owner Doc，91 行——Cycle 1 关闭稳态暂停分支 A；**Phase 9 @Auth gated 阻塞待人工确认**〔Protected Area，successor path 已登记〕；Phase 4 cascadeDelete successor 已落地 `2026-08-14-0707-1`）；5.3 报告（nop-code 组版本漂移 P2 邻接）；6.7 批次 c nop-code 5 条 P1 标定（撞车面）。
> 机械基线（主 agent 实测，2026-10-08）：nop-code/ **2026-09-29..HEAD 共 6 提交**（增量面）；**nop-code-lang-go/rust/csharp 三个 2026-09-29 新语言扩展**（0.5 基线刷新登记面——无在先审计覆盖，本项结构巡检）；Phase 9 @Auth gated 的 successor path 登记位置与现状 Phase 1 核对。
> **范围裁定**：① gated 复核=Phase 9 @Auth 现状核对（gated 原因/人工确认状态/successor path 落点——**本项只核对登记，不实施 @Auth 实现**〔Protected Area ask-first〕）+ Phase 4 cascadeDelete successor 落地验证；② 增量巡检=6 提交 diff triage（沿 2357 准入规则）+ 三新语言扩展结构巡检（模块定位/pom 合规/与 lang-java 的形态一致性/测试面——发现按判级锚立条）；③ 撞车检索面=6.7（nop-code 5 条 P1）/6.9（nop-code 3 条 still-open）/5.3（版本漂移 P2）/invariant-loop AR 台账。
> **方法学裁定**：① 巡检两跳：聚类→逐簇 diff triage+新模块结构面；② 发现编号 `P1-7.4-NNN`/`P2-7.4-NNN`；P0 即时通道；③ 验证协议：发现双锚；Phase 2 still-live 100% 亲读+其余抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 亲读；冻结协议三要素（seed=2360+提交簇哈希）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红按 2331 :93 先例归因登记。
> Draft Review:（待对抗审查）

## Purpose

执行 roadmap 工作项 7.4：nop-code Phase 9 @Auth gated 项复核 + 增量巡检（含 lang-go/rust/csharp 新扩展），产出报告 `7.4-nop-code-gated-incremental-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- invariant-loop Cycle 1 关闭（稳态暂停）；Phase 9 @Auth gated 待人工确认（successor path 已登记）；Phase 4 cascadeDelete successor 已落地。
- 5.3 已登记 nop-code 版本漂移 P2；6.7 已标定 nop-code 5 条 P1（AF 2/live 3→P1×2/P2×1——见 index）。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.4-nop-code-gated-incremental-audit.md`：摘要 + gated 复核段（Phase 9 @Auth 现状+successor path+Phase 4 验证）+ 增量巡检台账（6 提交+三新扩展）+ 发现列表（双锚）+ 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.4 行 + 独立 closure audit + roadmap 7.4 翻 done（closure audit 先于翻 done）。

## Non-Goals

- Phase 9 @Auth 实施（Protected Area ask-first——本项只核对登记）；invariant-loop 飞轮运营；修复执行；nop-code 已审区全量重审（invariant-loop Cycle 1 覆盖面+6.7 标定面）。

## Scope

### In Scope

- Phase 9 @Auth gated 现状核对+Phase 4 successor 落地验证
- nop-code/ 2026-09-29..HEAD 6 提交 diff 巡检
- lang-go/rust/csharp 三新扩展结构巡检
- 只读输入：git 历史、invariant-loop 台账、5.3/6.7 报告

### Out Of Scope

- @Auth 实施；飞轮运营；修复执行；已审区重审。

## Execution Plan

### Phase 1 - gated 复核与增量巡检（1 agent）

Status: planned
Targets: gated 项+增量面+新扩展

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派审计 agent）：① Phase 9 @Auth gated 现状核对（gated 原因/successor path 登记落点/人工确认状态——只核对不实施）+Phase 4 cascadeDelete successor 落地验证（`2026-08-14-0707-1` closure 证据）② 6 提交 diff triage（发现准入沿 2357 规则）③ 三新扩展结构巡检（pom/模块定位/lang-java 形态对照/测试面）④ 撞车比对 ⑤ 冻结协议三要素 → `_tmp/fcr-7.4-audit-a.md`
- [ ] 主 agent 抽查质量：冻结表重放+发现抽验（含亲读）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 审计产物落盘；发现双锚证据
- [ ] gated 复核+增量面+三扩展全覆盖；样本表可重放
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁+其余抽验 ≥30%+gated 复核结论核实+净账
- [ ] 主 agent 汇总：台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.4-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.4-nop-code-gated-incremental-audit.md`；index 登记 7.4 行 + 仍 live 进 P1 汇总或 P2 台账
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项）；**通过后** roadmap 7.4 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 巡检口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划，免除全量构建。

- [ ] 报告落盘且 gated 复核+增量面+三扩展全覆盖
- [ ] 每条发现双锚证据可复现；撞车比对留痕
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.4 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（Phase 9 @Auth 实施归其 successor plan〔人工确认后〕；仍 live 修复归 R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
