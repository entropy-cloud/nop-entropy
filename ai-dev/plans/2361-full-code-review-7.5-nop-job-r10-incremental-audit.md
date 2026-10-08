# 2361 full-code-review 7.5 nop-job R10 发现标定 + 增量巡检

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.5（MA7，Owner Doc=—）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 04/09/14 巡检侧重）
> Related: **`ai-dev/audits/2026-06-19-0931-adversarial-review-nop-job/01-open-findings.md`（R10 发现清单：5 P1+8 P2=13 条 open，summary 表 :440）**——R10=nop-job 第 10 轮 adversarial review（2026-06-19）；既有审计区（2026-05/06 deep+adversarial 多轮）不重审。
> 机械基线（主 agent 实测，2026-10-08）：R10 清单 451 行（5 P1：资源双重计算/null-cost NPE/所有权丢失后仍执行/批处理 fail-vs-defer/死错误码静默兜底 + 8 P2）；nop-job/ 2026-08-01..HEAD **50 提交**（增量巡检面）。**13 条的修复状态台账在何处登记（是否有承接 plan）Phase 1 对账落盘**。
> **范围裁定**：① R10 13 条逐条四态标定（already-fixed/still-live/duplicated/false-positive；发现准入与双锚沿 2357 形态；R10 报告自带「审查者亲读复核」声明仍须独立亲验——以当前代码为准）；② 增量巡检=50 提交 diff triage（维度 04/09/14 为主）；③ 撞车检索面=既有 nop-job 审计区（2026-05/06 各轮）/6.x（6.5 批次 a nop-job 面？无——6.5 批次 a 无 job；6.9 nop-job 域升级候选若有）/arm 台账（nop-job arm 若存在）。
> **方法学裁定**：① 标定+巡检双 agent（R10 13 条标定 / 50 提交增量巡检）；② 发现编号 `P1-7.5-NNN`/`P2-7.5-NNN`（R10 条目标定结果沿用 R10 ID 承载，仍 live 的 P1 进 R1.0 输入）；③ P0 即时通道；④ 验证协议：双锚；Phase 2 still-live 100% 亲读+标定抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 实跑；冻结协议三要素（seed=2361+清单哈希）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红按 2331 :93 先例归因登记。
> Draft Review:（待对抗审查）

## Purpose

执行 roadmap 工作项 7.5：nop-job R10 发现（5 P1/8 P2）修复状态标定 + 增量巡检（50 提交），产出报告 `7.5-nop-job-r10-incremental-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- R10 清单 13 条 open（2026-06-19 审查者亲读复核声明）；其后 nop-job/ 50 提交（可能已修复部分）。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.5-nop-job-r10-incremental-audit.md`：摘要 + R13 条标定台账 + 增量巡检台账 + 发现列表（双锚）+ 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.5 行 + 独立 closure audit + roadmap 7.5 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 既有审计区重审；修复执行；非 R10 清单与增量面的发现。

## Scope

### In Scope

- R10 13 条四态标定（以当前代码为准独立亲验）
- nop-job/ 2026-08-01..HEAD 50 提交 diff 巡检
- 只读输入：git 历史、R10 报告、既有审计区对照

### Out Of Scope

- 既有审计区重审；修复执行；非本范围发现。

## Execution Plan

### Phase 1 - 标定与巡检（2 agent 并行）

Status: planned
Targets: R10 清单 + 增量面

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent）：① R10 13 条清单核验（与报告 :440 summary 表 diff）② 修复状态台账对账（承接 plan 检索）③ 50 提交聚类 ④ 冻结协议三要素 ⑤ 双 agent 分工
- [ ] Agent 1「R10 标定」：13 条逐条四态标定（独立亲验+实跑〔可运行类〕+撞车比对）→ `_tmp/fcr-7.5-audit-a.md`
- [ ] Agent 2「增量巡检」：50 提交逐簇 diff triage（发现准入沿 2357 规则）→ `_tmp/fcr-7.5-audit-b.md`
- [ ] 主 agent 抽查质量：冻结表重放+标定/发现抽验（含实跑）
- [ ] 产物 `_tmp/fcr-7.5-audit-a/b.md` + 清单
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 审计产物落盘；still-live ≥9 行条目；双锚证据
- [ ] 13 条全量标定+50 提交全覆盖；样本表可重放
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁+标定/发现抽验 ≥30%+净账
- [ ] 主 agent 汇总：台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.5-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.5-nop-job-r10-incremental-audit.md`；index 登记 7.5 行 + 仍 live 进 P1 汇总或 P2 台账
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项）；**通过后** roadmap 7.5 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划（实跑为验证副作用），免除全量构建。

- [ ] 报告落盘且 13 条全量标定+50 提交全覆盖
- [ ] 每条双锚证据可复现；撞车比对留痕
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.5 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（仍 live 修复归 R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
