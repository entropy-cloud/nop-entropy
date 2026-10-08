# 2362 full-code-review 7.6 nop-datav followups P2 抽样巡检

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.6（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 04/09/12 巡检侧重）
> Related: **`ai-dev/backlog/nop-datav-audit-followups.md`（Owner Doc，152 行 P2-only backlog）**——#1-17 已 resolved（plan `2026-08-14-0950-1` completed 2026-08-14）；**open P2×~55**（multi #18-65=P2×48/open #66-69=P2×4/#70-72=P2×2+裁定项×1）；2.6（biz/graphql 深审——datav 域零覆盖声明？Phase 1 对账核对）/6.9（datav 域升级候选：6.7 未抽样 datav#P1-1〔D3〕已在 6.9 未抽样 12 条全「其他」类登记）。
> 机械基线（主 agent 实测，2026-10-08）：followups 台账 152 行；nop-datav/ **2026-08-01..HEAD = 45 提交**（增量巡检面；确定性口径沿 2357）。**台账 open P2 精确枚举 Phase 1 落盘（预期 ~55）**。
> **范围裁定**：① **抽样巡检**（roadmap 7.6 明文「抽样巡检」非全量标定）：open P2 按确定性抽样抽 ≥45%（≥25 条）逐条标定三分类（fixed-by-later-plan/still-open/watch-only——2358 形态），未抽样余量为抽样制设计内残留；② 增量巡检=45 提交 diff triage（域内域外标记沿 2357）；③ 撞车检索面=6.9 datav 域（未抽样登记）/2.6（biz/graphql 域交界核对）/既有 nop-datav 审计目录（5 份 source audits）。
> **方法学裁定**：① 发现编号 `P1-7.6-NNN`/`P2-7.6-NNN`；P0 即时通道；② 验证协议：双锚；Phase 2 still-live 100% 亲读+标定抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 实跑；冻结协议三要素（seed=2362+清单哈希+样本表）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红按 plan 2331 :92 先例归因登记。
> Draft Review:（待对抗审查）

## Purpose

执行 roadmap 工作项 7.6：nop-datav followups open P2 抽样巡检（≥45%）+ 增量巡检（45 提交），产出报告 `7.6-nop-datav-followups-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- followups 台账 152 行：#1-17 resolved（plan 0950-1 completed）；open P2×~55（Phase 1 精确枚举）。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.6-nop-datav-followups-audit.md`：摘要 + 台账对账（open 枚举）+ 抽样标定台账 + 增量巡检台账 + 发现列表（双锚）+ 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.6 行 + 独立 closure audit + roadmap 7.6 翻 done（closure audit 先于翻 done）。

## Non-Goals

- open P2 未抽样余量的全量标定（抽样制设计内残留）；修复执行；既有审计目录重审。

## Scope

### In Scope

- followups open P2 抽样 ≥45%（≥25 条）三分类标定
- nop-datav/ 2026-08-01 后 45 提交 diff 巡检
- 只读输入：git 历史、followups 台账、6.9 datav 域登记

### Out Of Scope

- 未抽样余量全量标定；修复执行；非台账与增量面发现。

## Execution Plan

### Phase 1 - 抽样标定与增量巡检（2 agent 并行）

Status: planned
Targets: followups open P2 + 增量面

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent）：① open P2 精确枚举（~55 预期；逐条 ID/标题/文件锚）② 45 提交聚类（确定性口径；域内域外标记）③ 冻结协议三要素（seed=2362+抽样表）④ 双 agent 分工
- [ ] Agent 1「抽样标定」：≥25 条三分类标定（代码亲读+锚点核对；fixed-by-later-plan 须修复证据）→ `_tmp/fcr-7.6-audit-a.md`
- [ ] Agent 2「增量巡检」：45 提交逐簇 diff triage（域内为主）→ `_tmp/fcr-7.6-audit-b.md`
- [ ] 主 agent 抽查质量：冻结表重放+标定/发现抽验（含实跑 ≥1）
- [ ] 产物 `_tmp/fcr-7.6-audit-a/b.md` + 清单
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 审计产物落盘；still-live 条目各 ≥9 行；双锚证据
- [ ] 抽样 ≥45% 达成+45 提交全覆盖；样本表可重放
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁+标定抽验 ≥30%+净账
- [ ] 主 agent 汇总：台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.6-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.6-nop-datav-followups-audit.md`；index 登记 7.6 行 + 仍 live 进 P1 汇总或 P2 台账
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（五项：证据完整性/严重性标定/引用路径真实性/计数对应/抽样覆盖率）；**通过后** roadmap 7.6 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 巡检口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划，免除全量构建。

- [ ] 报告落盘且抽样 ≥45% 达成+45 提交全覆盖
- [ ] 每条双锚证据可复现；撞车比对留痕
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.6 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（未抽样余量 = 抽样制设计内残留——roadmap 7.6「抽样巡检」约定内，非静默降级。）

## Non-Blocking Follow-ups

（still-live 修复归 R1.x；未抽样余量维持 backlog。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
