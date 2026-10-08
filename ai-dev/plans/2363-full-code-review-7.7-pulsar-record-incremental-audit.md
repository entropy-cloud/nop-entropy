# 2363 full-code-review 7.7 nop-message-pulsar + nop-record 单轮审计残留复核

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.7（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 04/09/14 巡检侧重）
> Related: **`ai-dev/audits/2026-05-20-adversarial-review-nop-message-pulsar/adversarial-review-round1.md`（Owner Doc，422 行——发现 1~16，空壳原型时代产物）**；**`ai-dev/audits/2026-08-16-1447-adversarial-review-nop-record-plan-203.md`（144 行——plan 203 对抗审查：P0 Blocker+P1×6+P2×3，readWhen 接线面）**；6.1（**P2-6.1-001 Pulsar seekToPosition 空实现**——发现 7 附注挂接 7.7/R1.0；6.9 escalation #:192 与 P2-6.1-001 同一缺陷双立标记——R1.0 合并裁定注记）；4.4（Pulsar seek 非「真缺陷 2」但在案发现 7 附注）/6.8（format-record 单元 P1×6 标定——nop-record plan-203 面撞车对照）。
> 机械基线（主 agent 实测，2026-10-08）：pulsar round1 **发现 1~16**（空壳时代：发现 14「三个子模块完全空壳」等——此后 nop-message-pulsar 大规模建设，2026-05-20..HEAD 仅 1 提交但建设提交在更早窗口，**现状=全面重建后的模块**）；nop-record plan-203 审查 **P0×1+P1×6+P2×3=10 条**（readWhen 流式路径/集合 item/header-trailer/DynLV/红测试等）；6.8 已标定 format-record 单元 P1×6（**撞车面**：plan-203 审查发现与 6.8 标定的 6 条 P1 的关系 Phase 1 对账——可能同源〔plan 203 实现缺陷〕也可能异面〔审查新增〕）。
> **范围裁定**：① pulsar 16 发现逐条现状复核（空壳时代发现 vs 全面重建后现状——预期大面积 already-fixed/obsolete，仍 live 者立条）；② **P2-6.1-001/6.9 #:192 seekToPosition 现状复核**（双立标记的同一缺陷——现状+R1.0 合并裁定输入整理）；③ nop-record plan-203 10 条现状复核（撞车对照 6.8 format-record 标定——同源归并）；④ 增量巡检=2026-05-20..HEAD pulsar 1 提交+nop-record 近窗提交（少量，以复核为主）。
> **方法学裁定**：① 双 agent（Agent 1=pulsar 面/Agent 2=nop-record 面）；② 发现编号 `P1-7.7-NNN`/`P2-7.7-NNN`（历史发现复核沿用原 ID 承载）；P0 即时通道；③ 撞车检索面=6.1/6.9/4.4/6.8/2.2（nop-record EQL 面？核对）；④ 验证协议：双锚；Phase 2 still-live 100% 亲读+复核抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 实跑；冻结协议三要素（seed=2363+清单哈希）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红按 plan 2331 :92 先例归因登记。
> Draft Review:（待对抗审查）

## Purpose

执行 roadmap 工作项 7.7：nop-message-pulsar 单轮审计（2026-05-20 round1）16 发现现状复核 + nop-record plan-203 审查 10 条现状复核 + seekToPosition 双立现状整理，产出报告 `7.7-pulsar-record-residual-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- pulsar 模块自 2026-05-20 空壳原型后全面重建（6.1 审计确认 seek 实现在位、仅 seek 侧注 live）。
- nop-record plan-203 审查（2026-08-16）10 条 open；6.8 已标定 format-record 单元 P1×6（撞车对照源）。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.7-pulsar-record-residual-audit.md`：摘要 + pulsar 16 发现现状台账 + plan-203 10 条现状台账 + seekToPosition 双立整理 + 发现列表（双锚）+ 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.7 行 + 独立 closure audit + roadmap 7.7 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 修复执行；已由 6.8 标定的 format-record P1 重复标定（撞车归并）；pulsar 重建面全量重审（6.1 已扫）。

## Scope

### In Scope

- pulsar round1 发现 1~16 现状复核（含发现 7 附注 seek 侧注+P2-6.1-001/6.9 #:192 双立整理）
- nop-record plan-203 审查 10 条现状复核（撞车对照 6.8）
- 只读输入：git 历史、两份 Owner Doc、6.1/6.8/6.9 报告

### Out Of Scope

- 修复执行；6.8 已标定条目重复标定；pulsar 重建面全量重审。

## Execution Plan

### Phase 1 - 现状复核（2 agent 并行）

Status: planned
Targets: pulsar 16 发现 + plan-203 10 条

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent）：① pulsar 16 发现清单核验（round1 :11-356 逐条）② plan-203 10 条清单核验（P0×1+P1×6+P2×3）③ 与 6.8 标定 6 条撞车预对账 ④ 冻结协议三要素 ⑤ 双 agent 分工
- [ ] Agent 1「pulsar 面」：16 发现逐条现状复核（重建后代码亲读；发现 7 附注+P2-6.1-001/6.9 #:192 专节）→ `_tmp/fcr-7.7-audit-a.md`
- [ ] Agent 2「nop-record 面」：10 条逐条现状复核（撞车对照 6.8 format-record 标定——同源归并/异面立条）→ `_tmp/fcr-7.7-audit-b.md`
- [ ] 主 agent 抽查质量：冻结表重放+复核抽验（含实跑 ≥1）
- [ ] 产物 `_tmp/fcr-7.7-audit-a/b.md` + 清单
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 审计产物落盘；still-live 条目各 ≥9 行；双锚证据
- [ ] 16+10 条全覆盖；样本表可重放
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁+复核抽验 ≥30%+双立整理核实+净账
- [ ] 主 agent 汇总：台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.7-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.7-pulsar-record-residual-audit.md`；index 登记 7.7 行 + 仍 live 进 P1 汇总或 P2 台账
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项）；**通过后** roadmap 7.7 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 复核口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划，免除全量构建。

- [ ] 报告落盘且 16+10 条全覆盖复核
- [ ] 每条双锚证据可复现；撞车比对留痕
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.7 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（仍 live 修复归 R1.x；seekToPosition 双立归 R1.0 合并裁定。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
