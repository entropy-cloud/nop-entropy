# 2359 full-code-review 7.3 nop-metadata P1×1 live 复核 + P2 台账巡检

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.3（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: `ai-dev/audits/arm-unclosed-findings-nop-metadata.md`（Owner Doc，137 行 v3：未闭包 P1×3=1 live open+2 watch-only 已复核、P2×16 归集登记；2026-08-05 两轮 11 P1 全 fixed）；`ai-dev/audits/arm-index-nop-metadata.md`（P1 汇总镜像）。
> 机械基线（主 agent 实测，2026-10-08）：**P1×1 live**=`2026-07-19-1118#维度20-01` OrmModelImporter.java:58,68 `System.currentTimeMillis()` DDD-006 违反残余（主代码 10 处已修 8 处，MR2 机械修复归属）；**P2×16** 归集登记（部分已裁定 watch-only/optimization——逐条现状巡检为本项主体）；2 项 P1 watch-only（MISSING-AUTH/维度16-01）已有 MA3.3/MA4.4-4.7 复核归属，本项仅核对归属落账不重复复核。
> **方法学裁定**：① P1×1 live 复核=代码亲读 :58/:68（+git log 该文件 arm 收口后变更）+ MR2 归属确认（仍 live→R1.0 输入行）；② P2×16 逐条四态标定（fixed-by-later-plan/still-open/watch-only-维持——沿 2358 批量标定形态；每条判定依据一句+代码/台账锚）；③ 撞车检索面=6.7（nop-metadata 唯一归批次 c——check nop-metadata P1=MetaTableProfiler :331-346 非同条声明核对）/6.9（metadata 域升级候选）/MA3.3/MA4.x 复核注记；④ 验证协议：发现/标定双锚；Phase 2 抽验 ≥30%；冻结协议三要素（seed=2359+台账清单哈希）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚。
> Draft Review:（待对抗审查）

## Purpose

执行 roadmap 工作项 7.3：nop-metadata P1×1 live 复核 + P2×16 台账巡检，产出报告 `7.3-nop-metadata-incremental-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- 台账 v3（2026-08-05）：P1×3（1 live+2 watch-only 已复核）+P2×16+2026-08-05 两轮 11 P1 全 fixed。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.3-nop-metadata-incremental-audit.md`：摘要 + P1 live 复核段 + P2×16 标定台账 + 裁决（附录记冻结协议）。
- P1 仍 live→R1.0 输入行确认；P2 标定结果归 P2 台账通道。
- index 登记 7.3 行 + 独立 closure audit + roadmap 7.3 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 2 项 P1 watch-only 重复复核（归属落账核对即可）；修复执行；check nop-metadata 重标定（6.7 已闭）。

## Scope

### In Scope

- P1×1（维度20-01）live 复核
- P2×16 逐条四态标定
- 只读输入：git 历史、台账、6.7/6.9 metadata 域发现

### Out Of Scope

- 修复执行；watch-only P1 重复复核；非台账发现。

## Execution Plan

### Phase 1 - 复核与标定（1 agent）

Status: planned
Targets: 台账 17 条（P1×1+P2×16）

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派审计 agent）：① P1×1 live 复核（OrmModelImporter :58/:68 亲读+git log arm 后变更+MR2 归属确认）② P2×16 逐条四态标定（双锚）③ 撞车比对 ④ 冻结协议三要素 → `_tmp/fcr-7.3-audit-a.md`
- [ ] 主 agent 抽查质量：冻结表重放+标定抽验 ≥3 条（含代码亲读）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 审计产物落盘；标定双锚证据
- [ ] 17 条全量标定；样本表可重放
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：P1 亲读复核+P2 标定抽验 ≥30%+净账
- [ ] 主 agent 汇总：台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.3-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.3-nop-metadata-incremental-audit.md`；index 登记 7.3 行 + 仍 live 进 R1.0 输入行
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项）；**通过后** roadmap 7.3 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划，免除全量构建。

- [ ] 报告落盘且 17 条全量复核/标定
- [ ] 每条双锚证据可复现；撞车比对留痕
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.3 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（P1 修复归 MR2/R1.x；watch-only 维持。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
